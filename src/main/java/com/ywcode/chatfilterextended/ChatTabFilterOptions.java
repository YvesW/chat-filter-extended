package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ChatTabFilterOptions {
    PUBLIC("Public", "Public"),
    FRIENDS("Friends", "Friends"),
    FC("Friends chat", "FC"),
    CC("Clan", "CC"),
    GUEST_CC("Guest clan", "Guest"),
    RAID("Raid party", "Raid"),
    PARTY("RuneLite party", "Party"),
    WHITELIST("Custom whitelist", "Whitelist");

    private final String option;
    private final String abbreviation;

    @Override
    public String toString() {
        return option;
    }

    public String toAbbreviationString() {
        return abbreviation;
    }
}