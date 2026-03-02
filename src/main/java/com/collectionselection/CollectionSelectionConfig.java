package com.collectionselection;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

import java.awt.Color;

@ConfigGroup("collectionselection")
public interface CollectionSelectionConfig extends Config
{
	@ConfigItem(
		keyName = "showChatMessage",
		name = "Chat notifications",
		description = "Show a chat message when a page locks, unlocks, or a pet is received",
		position = 1
	)
	default boolean showChatMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTargetOverlay",
		name = "Target overlay",
		description = "Show a small card on the gameframe displaying your selected target",
		position = 2
	)
	default boolean showTargetOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showClogPopup",
		name = "Collection log popup",
		description = "Show a native-style popup for non-target collection log additions",
		position = 3
	)
	default boolean showClogPopup()
	{
		return true;
	}

	// ── Fire Settings ─────────────────────────────────────────────────────────

	@ConfigSection(
		name = "Fire Settings",
		description = "Tune the fire / glow animation",
		position = 10
	)
	String fireSection = "fire";

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "fireSpeed",
		name = "Animation Speed",
		description = "Higher values = faster fire / glow animation",
		section = "fire",
		position = 11
	)
	default int fireSpeed()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "fireGlowColor",
		name = "Glow Colour",
		description = "Base colour used for the fire outline",
		section = "fire",
		position = 12
	)
	default Color fireGlowColor()
	{
		return new Color(220, 60, 0);
	}
}
