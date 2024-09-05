package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.widgets.ComponentID;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public enum ChatTab {
    PUBLIC(ComponentID.CHATBOX_TAB_PUBLIC, "pu", "publicChatFilters", "publicFilterEnabled"),
    PRIVATE(ComponentID.CHATBOX_TAB_PRIVATE, "pr", "privateChatFilters", "privateFilterEnabled"),
    CHANNEL(ComponentID.CHATBOX_TAB_CHANNEL, "ch", "channelChatFilters", "channelFilterEnabled"),
    CLAN(ComponentID.CHATBOX_TAB_CLAN, "cl", "clanChatFilters", "clanFilterEnabled"),
    TRADE(ComponentID.CHATBOX_TAB_TRADE, "tr", "tradeChatFilters", "tradeFilterEnabled");

    private final int componentID; //The ComponentID of the chat tab/stone
    private final String abbreviation; //Abbreviation to be used in the JSON of FilteredRegions
    private final String chatTabFiltersKeyName; //The RSProfile Config name for the set of options
    private final String filterEnabledKeyName; //The RSProfile Config name for the enabled boolean

    @Getter(AccessLevel.PACKAGE)
    private static final String publicOHChatFiltersKeyName = "publicOHChatFilters";
    private static final Map<Integer, ChatTab> BY_COMPONENT_ID = new HashMap<>();

    //Cache element value to enum element map
    static {
        for (ChatTab chatTab: values()) {
            BY_COMPONENT_ID.put(chatTab.componentID, chatTab);
        }
    }

    //Get enum element based on componentID
    @Nullable
    static ChatTab getEnumElement(int componentID) {
        return BY_COMPONENT_ID.get(componentID);
    }
}