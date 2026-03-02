package com.collectionselection;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;

import javax.inject.Inject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Shows a small card anchored to the gameframe displaying the entry
 * currently selected in the plugin panel.
 *
 * Tries the resizable viewport widget (161, 34) first, then falls back
 * to the fixed-classic viewport widget (548, 2).
 */
public class TargetInfoOverlay extends Overlay
{
	private static final Color COL_GOLD    = new Color(200, 165, 55);
	private static final Color COL_BODY_BG = new Color(15, 12, 4, 140);

	private final Client                    client;
	private final CollectionSelectionPlugin plugin;
	private final CollectionSelectionConfig config;
	private final ItemManager               itemManager;

	@Inject
	public TargetInfoOverlay(Client client, CollectionSelectionPlugin plugin,
	                         CollectionSelectionConfig config, ItemManager itemManager)
	{
		this.client      = client;
		this.plugin      = plugin;
		this.config      = config;
		this.itemManager = itemManager;

		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showTargetOverlay()) return null;
		if (client.getGameState() != GameState.LOGGED_IN) return null;

		String entryName = plugin.panel.getSelectedEntryName();
		if (entryName == null) return null;

		CollectionSelectionPlugin.TargetEntry entry = plugin.targets.get(entryName);
		if (entry == null || entry.locked || entry.itemId < 0) return null;

		// Try resizable (161.34), then fixed classic (548.2)
		Widget anchor = client.getWidget(161, 34);
		if (anchor == null || anchor.isHidden()) anchor = client.getWidget(548, 2);
		if (anchor == null || anchor.isHidden()) return null;

		net.runelite.api.Point loc = anchor.getCanvasLocation();
		if (loc == null) return null;

		return drawCard(g, entry, loc.getX(), loc.getY());
	}

	private Dimension drawCard(Graphics2D g, CollectionSelectionPlugin.TargetEntry entry, int x, int y)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Font smallFont = FontManager.getRunescapeSmallFont();
		Font boldFont  = smallFont.deriveFont(Font.BOLD);

		g.setFont(boldFont);
		FontMetrics fmBold = g.getFontMetrics();
		g.setFont(smallFont);
		FontMetrics fmSmall = g.getFontMetrics();

		int iconSize = 28;
		int pad      = 5;

		String header   = "TARGET";
		String itemText = entry.itemName;
		int textW = Math.max(fmBold.stringWidth(header), fmSmall.stringWidth(itemText));
		int cardW = pad + iconSize + pad + textW + pad;
		int cardH = pad + iconSize + pad;

		g.setColor(COL_BODY_BG);
		g.fillRoundRect(x, y, cardW, cardH, 4, 4);

		double p = pulse(1200);
		int borderAlpha = (int)(120 + 100 * p);
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(new Color(COL_GOLD.getRed(), COL_GOLD.getGreen(), COL_GOLD.getBlue(), borderAlpha));
		g.drawRoundRect(x, y, cardW, cardH, 4, 4);
		g.setStroke(new BasicStroke(1f));

		BufferedImage icon = itemManager.getImage(entry.itemId);
		if (icon != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(icon, x + pad, y + (cardH - iconSize) / 2, iconSize, iconSize, null);
		}

		int textX      = x + pad + iconSize + pad;
		int lineH      = fmBold.getHeight();
		int totalTextH = lineH + fmSmall.getHeight();
		int textY      = y + (cardH - totalTextH) / 2 + fmBold.getAscent();

		g.setFont(boldFont);
		g.setColor(COL_GOLD);
		g.drawString(header, textX, textY);

		g.setFont(smallFont);
		g.setColor(Color.WHITE);
		g.drawString(itemText, textX, textY + lineH);

		return new Dimension(cardW, cardH);
	}

	private double pulse(long periodMs)
	{
		return (Math.sin(System.currentTimeMillis() / (periodMs / Math.PI)) + 1.0) / 2.0;
	}
}
