package com.collectionselection;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

@SuppressWarnings("unchecked")
public class CollectionSelectionPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CollectionSelectionPlugin.class);
		RuneLite.main(args);
	}
}
