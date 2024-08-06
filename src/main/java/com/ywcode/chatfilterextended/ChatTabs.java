package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.widgets.ComponentID;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public enum ChatTabs {
    PUBLIC(ComponentID.CHATBOX_TAB_PUBLIC, "pu", "publicChatFilterOptions"),
    PRIVATE(ComponentID.CHATBOX_TAB_PRIVATE, "pr", "privateChatFilterOptions"),
    CHANNEL(ComponentID.CHATBOX_TAB_CHANNEL, "ch", "channelChatFilterOptions"),
    CLAN(ComponentID.CHATBOX_TAB_CLAN, "cl", "clanChatFilterOptions"),
    TRADE(ComponentID.CHATBOX_TAB_TRADE, "tr", "tradeChatFilterOptions");

    private final int componentID;
    private final String abbreviation;
    private final String chatFilterOptionsKeyName;

    private static final Map<Integer, ChatTabs> BY_COMPONENT_ID = new HashMap<>();
    private static final Map<String, ChatTabs> BY_ABBREVIATION = new HashMap<>();

    //Cache element value to enum element map
    static {
        for (ChatTabs chatTab: values()) {
            BY_COMPONENT_ID.put(chatTab.componentID, chatTab);
            BY_ABBREVIATION.put(chatTab.abbreviation, chatTab);
        }
    }

    //Get enum element based on componentID
    @Nullable
    public static ChatTabs getEnumElement(int componentID) {
        return BY_COMPONENT_ID.get(componentID);
    }

    //Get enum element based on abbreviation
    @Nullable
    public static ChatTabs getEnumElement(String abbreviation) {
        return BY_ABBREVIATION.get(abbreviation);
    }
}