package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public enum ChatTabFilterOptions {
    PUBLIC("Public", "Public", "pu"),
    FRIENDS("Friends", "Friends", "fr"),
    FC("Friends chat", "FC", "fc"),
    CC("Clan", "CC", "cc"),
    GUEST_CC("Guest clan", "Guest", "gu"),
    RAID("Raid party", "Raid", "ra"),
    PARTY("RuneLite party", "Party", "pa"),
    WHITELIST("Custom whitelist", "Whitelist", "wh");

    private final String option;
    private final String abbreviation;
    private final String filteredRegionAbbreviation;

    private static final Map<String, ChatTabFilterOptions> BY_FILTERED_REGION_ABBREVIATION = new HashMap<>();

    //Cache element value to enum element map
    static {
        for (ChatTabFilterOptions chatTabFilterOption: values()) {
            BY_FILTERED_REGION_ABBREVIATION.put(chatTabFilterOption.filteredRegionAbbreviation, chatTabFilterOption);
        }
    }

    //Get enum element based on filtered region abbreviation
    @Nullable
    public static ChatTabFilterOptions getEnumElement(String filteredRegionAbbreviation) {
        return BY_FILTERED_REGION_ABBREVIATION.get(filteredRegionAbbreviation);
    }

    @Override
    public String toString() {
        return option;
    }
}