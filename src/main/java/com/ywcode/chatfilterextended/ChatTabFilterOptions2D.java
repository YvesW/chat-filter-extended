package com.ywcode.chatfilterextended;

import lombok.*;

@RequiredArgsConstructor
@Getter
public enum ChatTabFilterOptions2D {
    PUBLIC("Public 2D", "Public 2D"),
    FRIENDS("Friends 2D", "Friends 2D"),
    FC("Friends chat 2D", "FC 2D"),
    CC("Clan 2D", "CC 2D"),
    GUEST_CC("Guest Clan 2D", "Guest 2D"),
    RAID("Raid Party 2D", "Raid 2D"),
    WHITELIST ("Custom whitelist 2D", "Whitelist 2D");

    private final String option;
    private final String abbreviation;

    @Override
    public String toString() {
        return option;
    }

    public String toAbbreviationString() {
        return abbreviation;
    }

    public String toNon2DAbbreviationString() {
        return abbreviation.replace(" 2D", "");
    }
    //Separate enum because the chat in the config panel also needs to change compared to the non-2D set.
}
