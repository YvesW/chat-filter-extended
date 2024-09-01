package com.ywcode.chatfilterextended;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("ChatFilterExtended")
public interface ChatFilterExtendedConfig extends Config
{
	@ConfigSection(
			name = "General",
			description = "General settings",
			position = 0
	)
	String generalSettings = "generalSettings";

	@ConfigSection(
			name = "Whitelists",
			description = "Per chat tab whitelists",
			position = 1,
			closedByDefault = true
	)
	String whitelistSettings = "whitelistSettings";

	@ConfigSection(
			name = "Clear chat sets",
			description = "When should the sets that contain the usernames be cleared? E.g. when to clear the set of FC usernames.<br>"
					+ "Don't change if you do not understand what you are doing.",
			position = 2,
			closedByDefault = true
	)
	String clearChatSetsSettings = "clearChatSetsSettings";

	@ConfigSection(
			name = "Advanced",
			description = "Advanced settings. Don't change if you do not understand what you are doing.",
			position = 3,
			closedByDefault = true
	)
	String advancedSettings = "advancedSettings";

	@ConfigItem(
			keyName = "forcePrivateOn",
			name = "Force private to on when filtering",
			description = "When you tick this option, you force your private to 'show all' when you enable the custom filter for your private chat ingame.<br>"
					+ "Remember that this means everyone can see you online in their Friends List and get your world when the custom filter is enabled.<br>"
					+ "This might have consequences if you do dangerous activities in the wilderness (e.g. getting scouted/sniped)!<br>"
					+ "If this option is disabled, private will only be properly filtered if you set it to 'show all' and then set it to 'custom'.",
			position = 0,
			section = generalSettings
	)
	default boolean forcePrivateOn() {
		return false;
	}

	@ConfigItem(
			keyName = "showGuestTrades",
			name = "Show (guest) clan guest trades",
			description = "Also show trades by clan guests or guest clan guests when clan/guest clan are included in the set,<br>"
					+ "and the filter is active. Disabled by default so people cannot impersonate cc/guest cc members by renaming to<br>"
					+ "a name close to a cc/guest cc member, and joining the cc/guest cc as guest.",
			position = 1,
			section = generalSettings
	)
	default boolean showGuestTrades()
	{
		return false;
	}

	@ConfigItem(
			keyName = "changeChatSetsShiftMenuSetting",
			name = "Show Change Chat Sets menu option",
			description = "When to show the Change Sets menu option.<br>"
					+ "Holding shift: when holding shift and right clicking on a chat tab/stone. Always: when right clicking on a chat tab/stone.",
			position = 2,
			section = generalSettings
	)
	default ShiftMenuSetting changeChatSetsShiftMenuSetting()
	{
		return ShiftMenuSetting.ALWAYS;
	}

	@ConfigItem(
			keyName = "autoEnableFilteredRegionShiftMenuSetting",
			name = "Show Auto-enable Custom menu option",
			description = "When to show the Auto-enable Custom for current region menu option, assuming the custom filter is enabled for this chat.<br>"
					+ "Disabled: never. Holding shift: when holding shift and right clicking on a chat tab/stone. Always: when right clicking on a chat tab/stone.",
			position = 3,
			section = generalSettings
	)
	default ShiftMenuSettingOptional autoEnableFilteredRegionShiftMenuSetting()
	{
		return ShiftMenuSettingOptional.HOLD_SHIFT;
	}

	@ConfigItem(
			keyName = "publicWhitelist",
			name = "Public custom whitelist",
			description = "Enable 'Custom whitelist' in the public chat set to also allow messages from these senders. Comma,separated,input",
			position = 0,
			section = whitelistSettings
	)
	default String publicWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "privateWhitelist",
			name = "Private custom whitelist",
			description = "Enable 'Custom whitelist' in the private chat set to also allow messages from these senders. Comma,separated,input",
			position = 1,
			section = whitelistSettings
	)
	default String privateWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "channelWhitelist",
			name = "Channel custom whitelist",
			description = "Enable 'Custom whitelist' in the channel (FC) chat set to also allow messages from these senders. Comma,separated,input",
			position = 2,
			section = whitelistSettings
	)
	default String channelWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "clanWhitelist",
			name = "Clan custom whitelist",
			description = "Enable 'Custom whitelist' in the clan (CC) chat set to also allow messages from these senders. Comma,separated,input",
			position = 3,
			section = whitelistSettings
	)
	default String clanWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "tradeWhitelist",
			name = "Trade custom whitelist",
			description = "Enable 'Custom whitelist' in the trade chat set to also allow messages from these senders. Comma,separated,input",
			position = 4,
			section = whitelistSettings
	)
	default String tradeWhitelist()
	{
		return "";
	}

	@ConfigItem(
			keyName = "clearChannelSetHop",
			name = "Clear FC on hop",
			description = "Clear the set of the FC members when hopping. The set will always be cleared when fully logging out.",
			position = 0,
			section = clearChatSetsSettings
	)
	default boolean clearChannelSetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearClanSetHop",
			name = "Clear CC on hop",
			description = "Clear the set of the CC members when hopping. The set will always be cleared when fully logging out.<br>"
					+ "Keep in mind that this set also contains the GIM players, so if you are having trouble with those, make sure this setting is disabled.",
			position = 1,
			section = clearChatSetsSettings
	)
	default boolean clearClanSetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearGuestClanSetHop",
			name = "Clear Guest CC on hop",
			description = "Clear the set of the Guest CC members when hopping. These are the members of the guest CC, not the guests in your CC.<br>"
					+ "The set will always be cleared when fully logging out.",
			position = 2,
			section = clearChatSetsSettings
	)
	default boolean clearGuestClanSetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearRaidPartySetHop",
			name = "Clear Raid on hop",
			description = "Clear the set of the Raid Party members when hopping. The set will always be cleared when fully logging out.",
			position = 3,
			section = clearChatSetsSettings
	)
	default boolean clearRaidPartySetHop()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearRaidPartyShiftMenuSetting",
			name = "Show Clear Raid Party menu option",
			description = "When to show the Clear Raid Party members menu option, assuming the custom filter is enabled for this chat.<br>"
					+ "Disabled: never. Holding shift: when holding shift and right clicking on a chat tab/stone. Always: when right clicking on a chat tab/stone.",
			position = 4,
			section = clearChatSetsSettings
	)
	default ShiftMenuSettingOptional clearRaidPartyShiftMenuSetting()
	{
		return ShiftMenuSettingOptional.HOLD_SHIFT;
	}

	@ConfigItem(
			keyName = "clearRLPartySetHop",
			name = "Clear RuneLite Party on hop",
			description = "Clear the set of the RuneLite Party members when hopping. The set will always be cleared when fully logging out.",
			position = 5,
			section = clearChatSetsSettings
	)
	default boolean clearRLPartySetHop()
	{
		return true;
	}

	@ConfigItem(
			keyName = "clearChannelSetLeave",
			name = "Clear FC when leaving",
			description = "Clear the set of the FC members when leaving the friends chat (FC).",
			position = 6,
			section = clearChatSetsSettings
	)
	default boolean clearChannelSetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearClanSetLeave",
			name = "Clear CC when leaving",
			description = "Clear the set of the CC members when leaving the CC.<br>"
					+ "Keep in mind that this set also contains the GIM players, so if you are having trouble with those, make sure this setting is disabled.",
			position = 7,
			section = clearChatSetsSettings
	)
	default boolean clearClanSetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearGuestClanSetLeave",
			name = "Clear Guest CC when leaving",
			description = "Clear the set of the Guest CC members when leaving the Guest CC.",
			position = 8,
			section = clearChatSetsSettings
	)
	default boolean clearGuestClanSetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "clearRLPartySetLeave",
			name = "Clear RuneLite Party when leaving",
			description = "Clear the set of the RuneLite Party members when leaving the RuneLite Party.",
			position = 9,
			section = clearChatSetsSettings
	)
	default boolean clearRLPartySetLeave()
	{
		return false;
	}

	@ConfigItem(
			keyName = "fixChatTabAlert",
			name = "Fix chat tab alert",
			description = "Prevent a filtered message from causing an incorrect chat tab alert (blinking chat tab).<br>"
					+ "This setting does not impact the 'Chat Filter' plugin (RuneLite core). Thus, if a message is not filtered by Chat Filter Extended,<br>"
					+ "but is filtered by the Chat Filter plugin, it might cause an incorrect chat tab alert.",
			position = 0,
			section = advancedSettings
	)
	default boolean fixChatTabAlert() {
		return true;
	}

	@ConfigItem(
			keyName = "preventLocalPlayerChatTabAlert",
			name = "Prevent own chat tab alert",
			description = "Prevent one of your own messages causing a chat tab alert (blinking chat tab).<br>"
					+ "Requires 'Fix chat tab alert' to be enabled.",
			position = 1,
			section = advancedSettings
	)
	default boolean preventLocalPlayerChatTabAlert()
	{
		return true;
	}

	@ConfigItem(
			keyName = "filteredRegionsData",
			name = "Automatically enabled regions",
			description = "String that contains the data for the regions in which you enabled automatic custom filtering.<br>"
					+ "It's recommended to use the menu options to modify this string (shift + right click a chat tab/stone by default).<br>"
					+ "Do NOT modify this string manually. It's only exposed to easily copy and paste it between profiles,<br>"
					+ "or to remove ALL the regions in which you enabled automatic custom filtering.",
			position = 2,
			section = advancedSettings
	)
	default String filteredRegionsData()
	{
		return "";
	}
}