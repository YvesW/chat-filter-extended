package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public enum ChatTabFilterOptionsOH {
    PUBLIC("Public OH", "Public OH", ChatTabFilterOptions.PUBLIC.getAbbreviation(), "puoh"),
    FRIENDS("Friends OH", "Friends OH", ChatTabFilterOptions.FRIENDS.getAbbreviation(), "froh"),
    FC("Friends chat OH", "FC OH", ChatTabFilterOptions.FC.getAbbreviation(), "fcoh"),
    CC("Clan OH", "CC OH", ChatTabFilterOptions.CC.getAbbreviation(), "ccoh"),
    GUEST_CC("Guest clan OH", "Guest OH", ChatTabFilterOptions.GUEST_CC.getAbbreviation(), "guoh"),
    RAID("Raid party OH", "Raid OH", ChatTabFilterOptions.RAID.getAbbreviation(), "raoh"),
    PARTY("RuneLite party OH", "Party OH", ChatTabFilterOptions.PARTY.getAbbreviation(), "paoh"),
    WHITELIST("Custom whitelist OH", "Whitelist OH", ChatTabFilterOptions.WHITELIST.getAbbreviation(), "whoh");

    private final String option;
    private final String abbreviation;
    private final String nonOHAbbreviation;
    private final String filteredRegionAbbreviation;

    private static final Map<String, ChatTabFilterOptionsOH> BY_FILTERED_REGION_ABBREVIATION = new HashMap<>();

    //Cache element value to enum element map
    static {
        for (ChatTabFilterOptionsOH chatTabFilterOptionOH: values()) {
            BY_FILTERED_REGION_ABBREVIATION.put(chatTabFilterOptionOH.filteredRegionAbbreviation, chatTabFilterOptionOH);
        }
    }

    //Get enum element based on filtered region abbreviation
    @Nullable
    public static ChatTabFilterOptionsOH getEnumElement(String filteredRegionAbbreviation) {
        return BY_FILTERED_REGION_ABBREVIATION.get(filteredRegionAbbreviation);
    }

    @Override
    public String toString() {
        return option;
    }
    //Separate enum because the chat in the config panel also needs to change compared to the non-OH set.
}