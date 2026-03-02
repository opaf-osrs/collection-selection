package com.collectionselection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Collection Selection",
	description = "Pick one target item per collection log page. Get it and the page locks forever.",
	tags = {"collection", "log", "selection", "challenge", "clog"}
)
public class CollectionSelectionPlugin extends Plugin
{
	private static final String  CONFIG_GROUP   = "collectionselection";
	private static final String  MENU_OPTION    = "Set target";
	private static final String  MENU_UNLOCK    = "Unlock page";
	private static final String  MENU_REMOVE    = "Remove target";
	private static final int     CLOG_SCRIPT_ID = 2731;

	/** Fires when "Collection log - New addition notification" is set to Chat (value 1). */
	private static final Pattern CLOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)");

	/** Fires when a pet is received, regardless of inventory state. */
	private static final Pattern PET_PATTERN = Pattern.compile(
		"You (?:have a funny feeling like you|feel something weird sneaking|feel something strange in your pocket).*");

	@Inject private Client                    client;
	@Inject private ClientThread              clientThread;
	@Inject private ClientToolbar             clientToolbar;
	@Inject private ChatMessageManager        chatMessageManager;
	@Inject private ConfigManager             configManager;
	@Inject private CollectionSelectionConfig config;
	@Inject private OverlayManager            overlayManager;
	@Inject private CollectionSelectionOverlay overlay;
	@Inject private TargetInfoOverlay         targetInfoOverlay;
	@Inject private Gson                      gson;

	/** All set targets, keyed by entry name (e.g. "Zulrah"). */
	final Map<String, TargetEntry> targets = new LinkedHashMap<>();

	/** The collection log entry currently open in the right panel. */
	String currentEntryName = null;

	CollectionSelectionPanel panel;
	private NavigationButton navButton;

	/** True once a pet has been received and not yet spent on an unlock. */
	boolean hasPetCredit = false;

	@Provides
	CollectionSelectionConfig provideConfig(ConfigManager cm)
	{
		return cm.getConfig(CollectionSelectionConfig.class);
	}

	// ── Lifecycle ─────────────────────────────────────────────────────────────

	@Override
	protected void startUp()
	{
		loadData();

		panel = new CollectionSelectionPanel();
		panel.setOnSimulateCollect(this::simulateCollect);
		panel.setOnSimulatePet(this::simulatePet);
		panel.setOnSimulateClogPopup(itemName -> clientThread.invokeLater(() -> showClogPopup(itemName)));
		panel.refresh(targets, hasPetCredit);

		BufferedImage icon;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/locked_icon.png");
			icon = ImageUtil.resizeImage(icon, 18, 18);
		}
		catch (Exception e)
		{
			icon = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = icon.createGraphics();
			g.setColor(new Color(0xCC4444));
			g.fillRect(0, 0, 18, 18);
			g.dispose();
		}

		navButton = NavigationButton.builder()
			.tooltip("Collection Selection")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		overlayManager.add(targetInfoOverlay);
	}

	@Override
	protected void shutDown()
	{
		saveData();
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		overlayManager.remove(targetInfoOverlay);
		currentEntryName = null;
	}

	// ── Events ────────────────────────────────────────────────────────────────

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Profile is now bound to the logged-in account — reload for this character
			loadData();
			panel.refresh(targets, hasPetCredit);

			// Warn if "Collection log - New addition notification" isn't set to Chat only.
			clientThread.invokeLater(() ->
			{
				int val = client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM);
				// 0 = off, 1 = chat only, 2 = popup only, 3 = chat + popup
				if (val == 0 || val == 2)
				{
					sendChatMessage("WARNING: Enable 'Collection log - New addition notification'" +
						" \u2192 Chat in game settings, or item detection won't work!");
				}
				else if (val == 3)
				{
					sendChatMessage("TIP: Set 'Collection log - New addition notification' to" +
						" Chat only (not Chat + Popup) \u2014 we show our own popups!");
				}
				// val == 1 (Chat only) = perfect, no warning needed
			});
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Clear in-memory state so a different account logging in starts clean
			targets.clear();
			hasPetCredit = false;
			currentEntryName = null;
			panel.refresh(targets, hasPetCredit);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Widget itemsPanel = client.getWidget(CollectionSelectionOverlay.CLOG_GROUP, CollectionSelectionOverlay.CLOG_ITEMS_PANEL);
		boolean open = itemsPanel != null && !itemsPanel.isHidden();

		if (open)
		{
			detectCurrentEntry();
			updateListAppearance();
		}
		else
		{
			currentEntryName = null;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == CLOG_SCRIPT_ID)
		{
			detectCurrentEntry();
		}
	}

	/** Reads the current entry name directly from the header label widget (621.20[0]). */
	private void detectCurrentEntry()
	{
		Widget header = client.getWidget(CollectionSelectionOverlay.CLOG_GROUP, CollectionSelectionOverlay.CLOG_HEADER);
		if (header == null) return;

		Widget[] dynChildren = header.getDynamicChildren();
		if (dynChildren == null || dynChildren.length == 0) return;

		String text = Text.removeTags(dynChildren[0].getText()).trim();
		if (text.isEmpty()) return;

		if (!text.equals(currentEntryName))
		{
			log.debug("CollectionSelection: entry → {}", text);
		}
		currentEntryName = text;
	}

	/** Colours locked entry names dark grey — the padlock icon is drawn by the overlay. */
	private void updateListAppearance()
	{
		for (int childId : CollectionSelectionOverlay.CLOG_LIST_CHILDREN)
		{
			Widget list = client.getWidget(CollectionSelectionOverlay.CLOG_GROUP, childId);
			if (list == null) continue;

			Widget[] children = list.getDynamicChildren();
			if (children == null) continue;

			for (Widget child : children)
			{
				String clean = Text.removeTags(child.getText()).trim();
				if (clean.isEmpty()) continue;

				TargetEntry entry = targets.get(clean);
				if (entry != null && entry.locked)
				{
					child.setTextColor(0x444444);
				}
			}
		}
	}

	/**
	 * Primary detection for both target locks and pet credits.
	 *
	 * Collection log message fires for both floor drops (when item lands) and
	 * direct-to-inventory drops — more reliable than onItemContainerChanged.
	 * Requires "Collection log - New addition notification" set to Chat in game settings.
	 *
	 * Pet message fires regardless of inventory state (full inv, floor drop, etc.).
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE
			&& event.getType() != ChatMessageType.SPAM) return;

		String msg = Text.removeTags(event.getMessage()).trim();

		// ── Collection log: target obtained ───────────────────────────────────
		Matcher clog = CLOG_PATTERN.matcher(msg);
		if (clog.matches())
		{
			String itemName = clog.group("item").trim();
			for (TargetEntry entry : targets.values())
			{
				if (!entry.locked && entry.itemName.equalsIgnoreCase(itemName))
				{
					triggerLock(entry);
					return;
				}
			}
			// Non-target item — show native-looking collection log popup
			if (config.showClogPopup()) clientThread.invokeLater(() -> showClogPopup(itemName));
			return;
		}

		// ── Pet received: grant unlock credit ─────────────────────────────────
		if (PET_PATTERN.matcher(msg).matches())
		{
			hasPetCredit = true;
			saveData();
			if (config.showChatMessage())
			{
				sendChatMessage("Pet received — you can now unlock one locked page.");
			}
		}
	}

	/** Injects "Set target" / "Remove target" on item panel slots and "Unlock page" on locked sidebar entries. */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		int groupId = WidgetUtil.componentToInterface(event.getActionParam1());
		if (groupId != CollectionSelectionOverlay.CLOG_GROUP) return;

		int componentId = WidgetUtil.componentToId(event.getActionParam1());

		// Sidebar list entries use setAction/CC_OP — skip them here
		for (int listChildId : CollectionSelectionOverlay.CLOG_LIST_CHILDREN)
		{
			if (componentId == listChildId) return;
		}

		// ── Items panel slots ──────────────────────────────────────────────────
		Widget itemsPanel = client.getWidget(CollectionSelectionOverlay.CLOG_GROUP, CollectionSelectionOverlay.CLOG_ITEMS_PANEL);
		if (itemsPanel == null || itemsPanel.isHidden()) return;
		if (currentEntryName == null) return;

		Widget parent = client.getWidget(groupId, componentId);
		if (parent == null) return;

		Widget[] dynamicChildren = parent.getDynamicChildren();
		int childIdx = event.getActionParam0();
		if (dynamicChildren == null || childIdx < 0 || childIdx >= dynamicChildren.length) return;

		Widget slot = dynamicChildren[childIdx];
		if (slot == null || slot.getItemId() < 0) return;

		TargetEntry entry = targets.get(currentEntryName);
		if (entry == null || !entry.locked)
		{
			client.getMenu().createMenuEntry(-1)
				.setOption(MENU_OPTION)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1())
				.setIdentifier(slot.getItemId());
		}

		// "Remove target" only on the item that is currently selected as target
		if (entry != null && !entry.locked && entry.itemId == slot.getItemId())
		{
			client.getMenu().createMenuEntry(-1)
				.setOption(MENU_REMOVE)
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.setParam0(event.getActionParam0())
				.setParam1(event.getActionParam1());
		}
	}

	/**
	 * Injects "Unlock page" on locked sidebar entries when the context menu opens.
	 * Uses mouse canvas position to identify which list entry was right-clicked,
	 * bypassing the widget action system entirely.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int mx = mouse.getX();
		int my = mouse.getY();

		for (int listChildId : CollectionSelectionOverlay.CLOG_LIST_CHILDREN)
		{
			Widget list = client.getWidget(CollectionSelectionOverlay.CLOG_GROUP, listChildId);
			if (list == null || list.isHidden()) continue;

			Widget[] children = list.getDynamicChildren();
			if (children == null) continue;

			for (int i = 0; i < children.length; i++)
			{
				Widget child = children[i];
				net.runelite.api.Point loc = child.getCanvasLocation();
				if (loc == null) continue;
				if (mx < loc.getX() || mx >= loc.getX() + child.getWidth()) continue;
				if (my < loc.getY() || my >= loc.getY() + child.getHeight()) continue;

				String clean = Text.removeTags(child.getText()).trim();

				TargetEntry entry = targets.get(clean);
				if (entry != null && entry.locked)
				{
					client.getMenu().createMenuEntry(1)
						.setOption(MENU_UNLOCK)
						.setTarget("<col=ff9040>" + clean + "</col>")
						.setType(MenuAction.RUNELITE)
						.setParam0(i)
						.setParam1(list.getId());
				}
				return;
			}
		}
	}

	/** Handles RUNELITE menu clicks from both the sidebar list and the items panel. */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		if (option == null) return;

		int componentId = WidgetUtil.componentToId(event.getParam1());

		if (event.getMenuAction() != MenuAction.RUNELITE) return;

		if (MENU_UNLOCK.equals(option))
		{
			// RUNELITE from sidebar list (injected via onMenuOpened)
			for (int listChildId : CollectionSelectionOverlay.CLOG_LIST_CHILDREN)
			{
				if (componentId == listChildId)
				{
					Widget list = client.getWidget(CollectionSelectionOverlay.CLOG_GROUP, listChildId);
					if (list != null)
					{
						Widget[] children = list.getDynamicChildren();
						int idx = event.getParam0();
						if (children != null && idx >= 0 && idx < children.length)
						{
							String raw = Text.removeTags(children[idx].getText()).trim();
							unlockEntry(raw);
						}
					}
					return;
				}
			}
			// RUNELITE from items panel
			if (currentEntryName != null) unlockEntry(currentEntryName);
		}
		else if (MENU_OPTION.equals(option))
		{
			if (currentEntryName == null || currentEntryName.isEmpty()) return;
			int itemId = event.getId();
			String itemName = client.getItemDefinition(itemId).getName();
			setTarget(currentEntryName, itemId, itemName);
		}
		else if (MENU_REMOVE.equals(option))
		{
			if (currentEntryName == null || currentEntryName.isEmpty()) return;
			targets.remove(currentEntryName);
			saveData();
			panel.refresh(targets, hasPetCredit);
		}
	}

	// ── Business logic ────────────────────────────────────────────────────────

	void setTarget(String entryName, int itemId, String itemName)
	{
		TargetEntry entry = new TargetEntry(entryName, itemId, itemName);
		targets.put(entryName, entry);
		saveData();
		panel.refresh(targets, hasPetCredit);
		log.debug("CollectionSelection: target set — {} → {} ({})", entryName, itemName, itemId);
	}

	private void triggerLock(TargetEntry entry)
	{
		entry.locked = true;
		saveData();

		if (config.showChatMessage())
		{
			sendChatMessage(entry.entryName + " has been locked!");
		}

		showNativePopup(entry);
		panel.refresh(targets, hasPetCredit);
	}

	/** Must be called on the client thread. */
	private void showClogPopup(String itemName)
	{
		int componentId = (client.getTopLevelInterfaceId() << 16)
			| (client.isResized() ? 13 : 43);

		client.openInterface(componentId, 660, WidgetModalMode.MODAL_CLICKTHROUGH);

		client.runScript(3343,
			"<col=ff981f>Collection log</col>",
			"<col=ff981f>New item:</col><br><col=ffffff>" + itemName + "</col>",
			-1);
		// Popup auto-dismisses on click or spacebar.
	}

	private void showNativePopup(TargetEntry entry)
	{
		clientThread.invokeLater(() ->
		{
			int componentId = (client.getTopLevelInterfaceId() << 16)
				| (client.isResized() ? 13 : 43);

			client.openInterface(componentId, 660, WidgetModalMode.MODAL_CLICKTHROUGH);

			String title       = "<col=C8A830>Page Locked</col>";
			String description = "<col=ff9040>" + entry.entryName + "</col>"
				+ "<br><br><col=ffffff>" + entry.itemName + "</col>";

			client.runScript(3343, title, description, -1);
			// Popup auto-dismisses on click or spacebar.
		});
	}

	void unlockEntry(String entryName)
	{
		TargetEntry entry = targets.get(entryName);
		if (entry == null || !entry.locked) return;
		if (!hasPetCredit)
		{
			if (config.showChatMessage())
			{
				sendChatMessage("You need a pet drop to unlock a page.");
			}
			return;
		}

		hasPetCredit = false;
		targets.remove(entryName);
		saveData();

		if (config.showChatMessage())
		{
			sendChatMessage("Page unlocked: " + entryName);
		}
		panel.refresh(targets, hasPetCredit);
	}

	// ── Test mode ─────────────────────────────────────────────────────────────

	private void simulateCollect(String itemName)
	{
		for (TargetEntry entry : targets.values())
		{
			if (!entry.locked && entry.itemName.equalsIgnoreCase(itemName))
			{
				triggerLock(entry);
				return;
			}
		}
		if (config.showChatMessage())
		{
			sendChatMessage("No unlocked target named '" + itemName + "' found.");
		}
	}

	private void simulatePet()
	{
		hasPetCredit = true;
		saveData();
		if (config.showChatMessage())
		{
			sendChatMessage("Pet received — you can now unlock one locked page.");
		}
	}

	// ── Persistence ───────────────────────────────────────────────────────────

	private void loadData()
	{
		targets.clear();
		hasPetCredit = false;

		String targetsJson = configManager.getRSProfileConfiguration(CONFIG_GROUP, "targets");
		if (targetsJson != null && !targetsJson.isEmpty())
		{
			Type type = new TypeToken<LinkedHashMap<String, TargetEntry>>() {}.getType();
			Map<String, TargetEntry> loaded = gson.fromJson(targetsJson, type);
			if (loaded != null) targets.putAll(loaded);
		}
		String creditStr = configManager.getRSProfileConfiguration(CONFIG_GROUP, "hasPetCredit");
		hasPetCredit = "true".equals(creditStr);
	}

	private void saveData()
	{
		configManager.setRSProfileConfiguration(CONFIG_GROUP, "targets", gson.toJson(targets));
		configManager.setRSProfileConfiguration(CONFIG_GROUP, "hasPetCredit", String.valueOf(hasPetCredit));
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void sendChatMessage(String text)
	{
		String msg = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("Collection Selection: ")
			.append(ChatColorType.NORMAL)
			.append(text)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(msg)
			.build());
	}

	// ── Data model ────────────────────────────────────────────────────────────

	public static class TargetEntry
	{
		public String  entryName;
		public int     itemId;
		public String  itemName;
		public boolean locked;

		public TargetEntry() {}

		public TargetEntry(String entryName, int itemId, String itemName)
		{
			this.entryName = entryName;
			this.itemId    = itemId;
			this.itemName  = itemName;
			this.locked    = false;
		}
	}
}
