package com.ywcode.chatfilterextended;

import net.runelite.client.config.*;

import java.util.*;
import java.util.stream.*;

@ConfigGroup("ChatFilterExtended")
public interface ChatFilterExtendedConfig extends Config
{
	@ConfigSection(
			name = "Public",
			description = "Public chat tab settings",
			position = 0,
			closedByDefault = true
	)
	String publicSettings = "publicSettings";

	@ConfigSection(
			name = "Private",
			description = "Private chat tab settings",
			position = 1,
			closedByDefault = true
	)
	String privateSettings = "privateSettings";

	@ConfigSection(
			name = "Channel",
			description = "Channel chat tab settings",
			position = 2,
			closedByDefault = true
	)
	String channelSettings = "channelSettings";

	@ConfigSection(
			name = "Clan",
			description = "Clan chat tab settings",
			position = 3,
			closedByDefault = true
	)
	String clanSettings = "clanSettings";

	@ConfigSection(
			name = "Trade",
			description = "Trade tab settings",
			position = 4,
			closedByDefault = true
	)
	String tradeSettings = "tradeSettings";

	@ConfigSection(
			name = "Advanced",
			description = "Advanced settings. Don't change if you do not understand what you are doing.",
			position = 5,
			closedByDefault = true
	)
	String advancedSettings = "advancedSettings";

	@ConfigItem(
			keyName = "publicChatFilterOptions",
			name = "Public",
			description = "Allowed chat senders when the custom filter is active.<br>"+
					"Hold control or shift to select multiple entries.",
			position = 0,
			section = publicSettings
	)
	default Set<ChatTabFilterOptions> publicChatFilterOptions()
	{
		Set<ChatTabFilterOptions> Default = new HashSet<>(EnumSet.allOf(ChatTabFilterOptions.class));
		Default.remove(ChatTabFilterOptions.PUBLIC);
		return Default;
	}

	@ConfigItem(
			keyName = "publicChatFilterOptions2D",
			name = "Public 2D",
			description = "Allowed chat senders when the custom filter is active (2D / overhead text). Needs to also be active in the set above.<br>"+
					"Thus enabling 'Friends' in the set above and 'Friends (2D)' in this set does not show your friends' messages in the chatbox, but it does show it above their head.<br>"+
					"Hold control or shift to select multiple entries.",
			position = 1,
			section = publicSettings
	)
	default Set<ChatTabFilterOptions2D> publicChatFilterOptions2D()
	{
		return Collections.emptySet(); //Empty set since otherwise the default is only showing 2D (overhead text) instead of also chatbox text.
	}

	@ConfigItem(
			keyName = "publicWhitelist",
			name = "Custom whitelist",
			description = "Enable 'Custom whitelist' in the set(s) above to also allow messages from these senders. Comma,separated,input",
			position = 2,
			section = publicSettings
	)
	default String publicWhitelist()
	{
		return "";
	}


	@ConfigItem(
			keyName = "privateChatFilterOptions",
			name = "Private",
			description = "Allowed chat senders when the custom filter is active.<br>"+
					"Hold control or shift to select multiple entries.",
			position = 0,
			section = privateSettings
	)
	default Set<ChatTabFilterOptions> privateChatFilterOptions()
	{
		return Collections.emptySet(); //Empty set since forcePrivateOn is disabled by default anyway
	}

	@ConfigItem(
			keyName = "privateWhitelist",
			name = "Custom whitelist",
			description = "Enable 'Custom whitelist' in the set above to also allow messages from these senders. Comma,separated,input",
			position = 1,
			section = privateSettings
	)
	default String privateWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "forcePrivateOn",
			name = "Force private to on when filtering",
			description = "When you tick this option, you force your private to 'show all' when you enable the custom filter for your private chat ingame.<br>" +
					"Remember that this means everyone can see you online in their Friends List and get your world when the custom filter is enabled.<br>" +
					"This might have consequences if you do dangerous activities in the wilderness (e.g. getting scouted/sniped)!<br>"+
					"If this option is disabled, private will only be properly filtered if you set it to 'show all' and then set it to 'custom'.",
			position = 2,
			section = privateSettings
	)
	default boolean forcePrivateOn() {
		return false;
	}

	@ConfigItem(
			keyName = "channelChatFilterOptions",
			name = "Channel",
			description = "Allowed chat senders when the custom filter is active.<br>"+
					"Hold control or shift to select multiple entries.",
			position = 0,
			section = channelSettings
	)
	default Set<ChatTabFilterOptions> channelChatFilterOptions()
	{
		Set<ChatTabFilterOptions> Default = new HashSet<>(EnumSet.allOf(ChatTabFilterOptions.class));
		Default.remove(ChatTabFilterOptions.PUBLIC);
		return Default;
	}

	@ConfigItem(
			keyName = "channelWhitelist",
			name = "Custom whitelist",
			description = "Enable 'Custom whitelist' in the set above to also allow messages from these senders. Comma,separated,input",
			position = 1,
			section = channelSettings
	)
	default String channelWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "clanChatFilterOptions",
			name = "Clan",
			description = "Allowed chat senders when the custom filter is active.<br>"+
					"Hold control or shift to select multiple entries.",
			position = 0,
			section = clanSettings
	)
	default Set<ChatTabFilterOptions> clanChatFilterOptions()
	{
		Set<ChatTabFilterOptions> Default = new HashSet<>(EnumSet.allOf(ChatTabFilterOptions.class));
		Default.remove(ChatTabFilterOptions.PUBLIC);
		return Default;
	}

	@ConfigItem(
			keyName = "clanWhitelist",
			name = "Custom whitelist",
			description = "Enable 'Custom whitelist' in the set above to also allow messages from these senders. Comma,separated,input",
			position = 1,
			section = clanSettings
	)
	default String clanWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "tradeChatFilterOptions",
			name = "Trade",
			description = "Allowed chat senders when the custom filter is active.<br>"+
					"Hold control or shift to select multiple entries.",
			position = 0,
			section = tradeSettings
	)
	default Set<ChatTabFilterOptions> tradeChatFilterOptions()
	{
		Set<ChatTabFilterOptions> Default = new HashSet<>(EnumSet.allOf(ChatTabFilterOptions.class));
		Default.remove(ChatTabFilterOptions.PUBLIC);
		return Default;
	}

	@ConfigItem(
			keyName = "tradeWhitelist",
			name = "Custom whitelist",
			description = "Enable 'Custom whitelist' in the set above to also allow messages from these senders. Comma,separated,input",
			position = 1,
			section = tradeSettings
	)
	default String tradeWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "clearChannelSetHop",
			name = "Clear FC on hop",
			description = "Clear the list of the FC members when hopping. The list will always be cleared when fully logging out.",
			position = 0,
			section = advancedSettings
	)
	default boolean clearChannelSetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearClanSetHop",
			name = "Clear CC on hop",
			description = "Clear the list of the CC members when hopping. The list will always be cleared when fully logging out.<br>"
					+ "Keep in mind that this list also contains the GIM players, so if you are having trouble with those, make sure this setting is disabled.",
			position = 1,
			section = advancedSettings
	)
	default boolean clearClanSetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearGuestClanSetHop",
			name = "Clear Guest CC on hop",
			description = "Clear the list of the Guest CC members when hopping. These are the members of the guest CC, not the guests in your CC. The list will always be cleared when fully logging out.",
			position = 2,
			section = advancedSettings
	)
	default boolean clearGuestClanSetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearRaidPartySetHop",
			name = "Clear Raid on hop",
			description = "Clear the list of the Raid Party members when hopping. The list will always be cleared when fully logging out.",
			position = 3,
			section = advancedSettings
	)
	default boolean clearRaidPartySetHop()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clearRLPartySetHop",
			name = "Clear Party on hop",
			description = "Clear the list of the RuneLite Party members when hopping. The list will always be cleared when fully logging out.",
			position = 4,
			section = advancedSettings
	)
	default boolean clearRLPartySetHop()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clearChannelSetLeave",
			name = "Clear FC when leaving",
			description = "Clear the list of the FC members when leaving the friends chat (FC).",
			position = 5,
			section = advancedSettings
	)
	default boolean clearChannelSetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearClanSetLeave",
			name = "Clear CC when leaving",
			description = "Clear the list of the CC members when leaving the CC.<br>"
					+ "Keep in mind that this list also contains the GIM players, so if you are having trouble with those, make sure this setting is disabled.",
			position = 6,
			section = advancedSettings
	)
	default boolean clearClanSetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearGuestClanSetLeave",
			name = "Clear GCC when leaving",
			description = "Clear the list of the Guest CC members when leaving the Guest CC.",
			position = 7,
			section = advancedSettings
	)
	default boolean clearGuestClanSetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearRLPartySetLeave",
			name = "Clear RuneLite Party when leaving",
			description = "Clear the list of the RuneLite Party members when leaving the RuneLite Party.",
			position = 8,
			section = advancedSettings
	)
	default boolean clearRLPartySetLeave()
	{
		return false;
	}
}
