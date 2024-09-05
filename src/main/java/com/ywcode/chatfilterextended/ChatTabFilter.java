package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public enum ChatTabFilter {
    PUBLIC("Public", "Public", "pu"),
    FRIENDS("Friends", "Friends", "fr"),
    FC("Friends chat", "FC", "fc"),
    CC("Clan", "CC", "cc"),
    GUEST_CC("Guest clan", "Guest", "gu"),
    RAID("Raid party", "Raid", "ra"),
    PARTY("RuneLite party", "Party", "pa"),
    WHITELIST("Custom whitelist", "Whitelist", "wh");

    private final String menuName; //To be used in i.a. menus like "Add Public"
    private final String abbreviation; //To be used in i.a. menus like "Custom: Show FC/CC/Guest/Party"
    private final String filteredRegionAbbreviation; //Abbreviation to be used in the FilteredRegion JSON

    //menuName, but the overhead variant
    final String getOHMenuName() {
        return menuName + " OH";
    }

    //abbreviation, but the overhead variant
    final String getOHAbbreviation() {
        return abbreviation + " OH";
    }

    /*
    //Could technically replace filteredRegionAbbreviation with
    final String getFilteredRegionAbbreviation() {
        return menuName.toLowerCase().substring(0, 2);
    }
     */

    private static final Map<String, ChatTabFilter> BY_FILTERED_REGION_ABBREVIATION = new HashMap<>();

    //Cache element value to enum element map
    static {
        for (ChatTabFilter chatTabFilter : values()) {
            BY_FILTERED_REGION_ABBREVIATION.put(chatTabFilter.filteredRegionAbbreviation, chatTabFilter);
        }
    }

    //Get enum element based on filteredRegionAbbreviation
    @Nullable
    static ChatTabFilter getEnumElement(String filteredRegionAbbreviation) {
        return BY_FILTERED_REGION_ABBREVIATION.get(filteredRegionAbbreviation.toLowerCase()); //toLowerCase() it to prevent potential case problems
    }
}