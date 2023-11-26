package com.ywcode.chatfilterextended;

import lombok.*;

@RequiredArgsConstructor
@Getter
public enum ChatTabFilterOptionsOH {
    PUBLIC("Public OH", "Public OH"),
    FRIENDS("Friends OH", "Friends OH"),
    FC("Friends chat OH", "FC OH"),
    CC("Clan OH", "CC OH"),
    GUEST_CC("Guest clan OH", "Guest OH"),
    RAID("Raid party OH", "Raid OH"),
    PARTY("RuneLite party OH", "Party OH"),
    WHITELIST("Custom whitelist OH", "Whitelist OH");

    private final String option;
    private final String abbreviation;

    @Override
    public String toString() {
        return option;
    }

    public String toAbbreviationString() {
        return abbreviation;
    }

    public String toNonOHAbbreviationString() {
        return abbreviation.replace(" OH", "");
    }
    //Separate enum because the chat in the config panel also needs to change compared to the non-OH set.
}
