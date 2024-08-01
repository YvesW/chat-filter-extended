package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum AutoFilterChatType {
    PUBLIC("pu"),
    PRIVATE("pr"),
    CHANNEL("ch"),
    CLAN("cl"),
    TRADE("tr");

    private final String abbreviation;

    @Override
    public String toString() {
        return abbreviation;
    }

    //Convert abbreviation to enum element by looping through enum.values()
    static AutoFilterChatType abbreviationToEnum(String input) {
        for (AutoFilterChatType autoFilterChatType : AutoFilterChatType.values()) {
            if (autoFilterChatType.getAbbreviation().equals(input)) {
                return autoFilterChatType;
            }
        }
        return null;
    }
}