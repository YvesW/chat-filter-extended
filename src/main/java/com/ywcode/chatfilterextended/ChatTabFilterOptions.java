package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    @Override
    public String toString() {
        return option;
    }
}