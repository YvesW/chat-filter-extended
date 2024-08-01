package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ChatTabFilterOptionsOH {
    PUBLIC("Public OH", "Public OH", "puoh"),
    FRIENDS("Friends OH", "Friends OH", "froh"),
    FC("Friends chat OH", "FC OH", "fcoh"),
    CC("Clan OH", "CC OH", "ccoh"),
    GUEST_CC("Guest clan OH", "Guest OH", "guoh"),
    RAID("Raid party OH", "Raid OH", "raoh"),
    PARTY("RuneLite party OH", "Party OH", "paoh"),
    WHITELIST("Custom whitelist OH", "Whitelist OH", "whoh");

    private final String option;
    private final String abbreviation;
    private final String filteredRegionAbbreviation;

    @Override
    public String toString() {
        return option;
    }

    String toNonOHAbbreviationString() {
        return abbreviation.replace(" OH", "");
    }
    //Separate enum because the chat in the config panel also needs to change compared to the non-OH set.
}