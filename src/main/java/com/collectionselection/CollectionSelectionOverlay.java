package com.collectionselection;

import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Draws overlays directly on collection log item widgets using WidgetItemOverlay.
 *
 * Verified widget IDs (Group 621):
 *   Child 12 — Bosses list     (dynamic children = entry name labels)
 *   Child 16 — Raids list
 *   Child 32 — Clues list
 *   Child 34 — Other list
 *   Child 35 — Minigames list
 *   Child 20 — header label    (dynamic child [0] = current entry title)
 *   Child 37 — Items panel     (dynamic children = item slots)
 */
public class CollectionSelectionOverlay extends WidgetItemOverlay
{
	static final int CLOG_GROUP       = 621;
	static final int CLOG_ITEMS_PANEL = 37;
	static final int CLOG_HEADER      = 20;

	static final int[] CLOG_LIST_CHILDREN = {12, 16, 32, 34, 35};

	private final CollectionSelectionPlugin plugin;
	private final CollectionSelectionConfig config;
	private final Client                    client;
	private final ItemManager               itemManager;
	private final BufferedImage             lockIcon;
	private final BufferedImage             listLockIcon;

	@Inject
	public CollectionSelectionOverlay(CollectionSelectionPlugin plugin, CollectionSelectionConfig config,
	                                  Client client, ItemManager itemManager)
	{
		this.plugin      = plugin;
		this.config      = config;
		this.client      = client;
		this.itemManager = itemManager;
		drawAfterInterface(CLOG_GROUP);

		BufferedImage icon = null;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/locked_icon.png");
		}
		catch (Exception ignored) {}
		lockIcon = icon;

		BufferedImage listIcon = null;
		try
		{
			listIcon = ImageUtil.loadImageResource(getClass(), "/locked_list.png");
		}
		catch (Exception ignored) {}
		listLockIcon = listIcon;
	}

	/**
	 * Draws the padlock icon flush to the right edge of every locked list entry
	 * (clipped to the list widget bounds to prevent icons floating when scrolled),
	 * then delegates item-slot overlays.
	 */
	@Override
	public Dimension render(Graphics2D g)
	{
		if (listLockIcon != null)
		{
			for (int childId : CLOG_LIST_CHILDREN)
			{
				Widget list = client.getWidget(CLOG_GROUP, childId);
				if (list == null || list.isHidden()) continue;

				Widget[] children = list.getDynamicChildren();
				if (children == null) continue;

				// The list widget is the scrollable content widget — its parent is the
				// stable scroll-container representing the actual visible area on screen.
				Widget viewport = list.getParent() != null ? list.getParent() : list;
				net.runelite.api.Point vpLoc = viewport.getCanvasLocation();
				if (vpLoc == null) continue;

				int vpX0 = vpLoc.getX();
				int vpY0 = vpLoc.getY();
				int vpY1 = vpY0 + viewport.getHeight();

				Shape prevClip = g.getClip();
				g.clip(new Rectangle(vpX0, vpY0, viewport.getWidth(), viewport.getHeight()));

				for (Widget child : children)
				{
					if (child.isHidden()) continue;

					String text = net.runelite.client.util.Text.removeTags(child.getText()).trim();
					if (text.isEmpty()) continue;

					CollectionSelectionPlugin.TargetEntry entry = plugin.targets.get(text);
					if (entry == null || !entry.locked) continue;

					net.runelite.api.Point loc = child.getCanvasLocation();
					if (loc == null) continue;

					int childY0 = loc.getY();
					int childY1 = childY0 + child.getHeight();
					if (childY1 <= vpY0 || childY0 >= vpY1) continue;

					int iconSize = Math.min(child.getHeight() - 2, 12);
					int x = loc.getX() + child.getWidth() - iconSize - 1;
					int y = loc.getY() + (child.getHeight() - iconSize) / 2;

					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
					g.drawImage(listLockIcon, x, y, iconSize, iconSize, null);
				}

				g.setClip(prevClip);
			}
		}

		return super.render(g);
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		Widget w = widgetItem.getWidget();
		if (w == null) return;
		Widget parent = w.getParent();
		if (parent == null || WidgetUtil.componentToId(parent.getId()) != CLOG_ITEMS_PANEL) return;

		String entryName = plugin.currentEntryName;
		if (entryName == null) return;

		CollectionSelectionPlugin.TargetEntry entry = plugin.targets.get(entryName);
		Rectangle bounds = widgetItem.getCanvasBounds();

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (entry != null && entry.locked)
		{
			if (w.getOpacity() > 0)
			{
				drawLockedTint(g, bounds, itemId);
				drawLockIcon(g, bounds);
			}
		}
		else if (entry != null && entry.itemId == itemId)
		{
			drawShapeFire(g, bounds, itemId);
		}
	}

	// ── Target outline: shape fire ────────────────────────────────────────────

	/** Flickering fire outline that follows the item's exact pixel silhouette. */
	private void drawShapeFire(Graphics2D g, Rectangle bounds, int itemId)
	{
		BufferedImage itemImg = itemManager.getImage(itemId);
		if (itemImg == null)
		{
			drawFire(g, bounds);
			return;
		}

		double flicker = pulse((long)(120 * config.fireSpeed()));
		Color  base    = config.fireGlowColor();
		int    expand  = 2;
		int    w       = bounds.width  + expand * 2;
		int    h       = bounds.height + expand * 2;

		BufferedImage halo = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D hg = halo.createGraphics();
		hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		for (int dx = 0; dx <= expand * 2; dx++)
		{
			for (int dy = 0; dy <= expand * 2; dy++)
			{
				if (dx == expand && dy == expand) continue;
				hg.drawImage(itemImg, dx, dy, bounds.width, bounds.height, null);
			}
		}

		hg.setComposite(AlphaComposite.SrcIn);
		hg.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(160 + 95 * flicker)));
		hg.fillRect(0, 0, w, h);

		hg.setComposite(AlphaComposite.DstOut);
		hg.drawImage(itemImg, expand, expand, bounds.width, bounds.height, null);
		hg.dispose();

		g.drawImage(halo, bounds.x - expand, bounds.y - expand, null);
	}

	/** Fallback fire border used when item image is unavailable. */
	private void drawFire(Graphics2D g, Rectangle bounds)
	{
		double flicker = pulse((long)(120 * config.fireSpeed()));
		Color  base    = config.fireGlowColor();

		float sw = 1.5f + (float)(1.5 * flicker);
		g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(160 + 95 * flicker)));
		g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);

		Color bright = base.brighter();
		g.setStroke(new BasicStroke(1f));
		g.setColor(new Color(bright.getRed(), bright.getGreen(), bright.getBlue(), (int)(80 + 120 * flicker)));
		g.drawRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4);
	}

	// ── Lock style: padlock ───────────────────────────────────────────────────

	/** Dark breathing tint masked to the item's own pixel shape. */
	private void drawLockedTint(Graphics2D g, Rectangle bounds, int itemId)
	{
		double pulse = pulse(1400);
		int alpha = (int)(80 + 45 * pulse);

		BufferedImage itemImg = itemManager.getImage(itemId);
		if (itemImg != null)
		{
			BufferedImage temp = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D tg = temp.createGraphics();
			tg.setColor(new Color(0, 0, 0, alpha));
			tg.fillRect(0, 0, bounds.width, bounds.height);
			tg.setComposite(AlphaComposite.DstIn);
			tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			tg.drawImage(itemImg, 0, 0, bounds.width, bounds.height, null);
			tg.dispose();
			g.drawImage(temp, bounds.x, bounds.y, null);
		}
		else
		{
			g.setColor(new Color(0, 0, 0, alpha));
			g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		}

		if (itemImg != null)
		{
			BufferedImage vig = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D vg = vig.createGraphics();
			float cx = bounds.width  * 0.5f;
			float cy = bounds.height * 0.5f;
			float r  = Math.max(bounds.width, bounds.height) * 0.7f;
			vg.setPaint(new RadialGradientPaint(cx, cy, r,
				new float[]{0f, 1f},
				new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 90)}));
			vg.fillRect(0, 0, bounds.width, bounds.height);
			vg.setComposite(AlphaComposite.DstIn);
			vg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			vg.drawImage(itemImg, 0, 0, bounds.width, bounds.height, null);
			vg.dispose();
			g.drawImage(vig, bounds.x, bounds.y, null);
		}
	}

	/** Pulsing padlock icon centred on the item. */
	private void drawLockIcon(Graphics2D g, Rectangle bounds)
	{
		if (lockIcon == null) return;
		double pulse = pulse(1400);
		float alpha = 0.65f + 0.35f * (float) pulse;

		int size = Math.min(bounds.width, bounds.height) / 2;
		int x = bounds.x + (bounds.width  - size) / 2;
		int y = bounds.y + (bounds.height - size) / 2;

		Composite prev = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(lockIcon, x, y, size, size, null);
		g.setComposite(prev);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/** Smooth oscillation 0→1 over the given period in ms. */
	private double pulse(long periodMs)
	{
		return (Math.sin(System.currentTimeMillis() / (periodMs / Math.PI)) + 1.0) / 2.0;
	}
}
