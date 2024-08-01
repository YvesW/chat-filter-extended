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
}