package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public enum AutoFilterChatType {
    PUBLIC("pu"),
    PRIVATE("pr"),
    CHANNEL("ch"),
    CLAN("cl"),
    TRADE("tr");

    private final String abbreviation;
    private static final Map<String, AutoFilterChatType> BY_ABBREVIATION = new HashMap<>();

    //Cache abbreviation to enum element map
    static {
        for (AutoFilterChatType autoFilterChatType: values()) {
            BY_ABBREVIATION.put(autoFilterChatType.abbreviation, autoFilterChatType);
        }
    }

    @Override
    public String toString() {
        return abbreviation;
    }

    //Get enum element based on abbreviation
    @Nullable
    public static AutoFilterChatType getEnumElement(String abbreviation) {
        return BY_ABBREVIATION.get(abbreviation);
    }
}