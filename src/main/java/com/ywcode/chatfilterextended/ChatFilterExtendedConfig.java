package com.ywcode.chatfilterextended;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.util.*;

@ConfigGroup("chat-filter-extended")
public interface ChatFilterExtendedConfig extends Config
{
	@ConfigItem(
			keyName = "showFriendsMessages",
			name = "Show Friends when filtering",
			description = "Show public messages from your friends when the plugin's public chat filter is enabled.",
			position = 1
	)
	default boolean showFriendsMessages() {
		return true;
	}

	@ConfigItem(
			keyName = "showCCMessages",
			name = "Show CC when filtering",
			description = "Show public messages from your clanmates when the plugin's public chat filter is enabled.",
			position = 2
	)
	default boolean showCCMessages() {
		return true;
	}

	@ConfigItem(
			keyName = "showFCMessages",
			name = "Show FC when filtering",
			description = "Show public messages from your FC members when the plugin's public chat filter is enabled.",
			position = 3
	)
	default boolean showFCMessages() {
		return true;
	}

	@ConfigItem(
			keyName = "showGuestCCMessages",
			name = "Show Guest CC when filtering",
			description = "Show public messages from your Guest CC members when the plugin's public chat filter is enabled.",
			position = 4
	)
	default boolean showGuestCCMessages() {
		return true;
	}

	@ConfigItem(
			keyName = "showRaidPartyMessages",
			name = "Show Raid Party when filtering",
			description = "Show public messages from your Raid Party members when the plugin's public chat filter is enabled.",
			position = 5
	)
	default boolean showRaidPartyMessages() {
		return true;
	}

	@ConfigItem(
			keyName = "chatsToFilter",
			name = "Chats to Filter",
			description = "Chat tabs to show the custom filter.<br>"+
			"Hold control or shift to select multiple entries.",
			position = 6
	)
	default Set<ChatsToFilter> chatsToFilter()
	{
		Set<ChatsToFilter> Default = new HashSet<ChatsToFilter>();
		Default.add(ChatsToFilter.PUBLIC);
		Default.add(ChatsToFilter.TRADE);
		return Default;
	}

	@ConfigItem(
			keyName = "forcePrivateOn",
			name = "Force private to on when filtering",
			description = "When you tick this option, you force your private to 'on' when you enable the custom filter for your private chat.<br>" +
					"Remember that this means everyone can see you online in their Friends List and get your world when the custom filter is enabled.<br>" +
					"This might have consequences if you do dangerous activities in the wilderness (e.g. getting scouted/sniped)!<br>"+
					"If this option is disabled, private will only be properly filtered if you set it to on and then enable the custom filter.",
			position = 7
	)
	default boolean forcePrivateOn() {
		return false;
	}
}
