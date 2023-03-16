package com.ywcode.chatfilterextended;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ChatFilterExtendedTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ChatFilterExtendedPlugin.class);
		RuneLite.main(args);
	}
}