package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    @Override
    public String toString() {
        return option;
    }
    //Separate enum because the chat in the config panel also needs to change compared to the non-OH set.
}