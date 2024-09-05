package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public enum FilteredRegionField {

    PUBLIC_CHAT_CUSTOM_ONLY("puB"),
    PUBLIC_OH_CHAT_SET("puOHS"),
    PUBLIC_CHAT_SET("puS"),
    PRIVATE_CHAT_CUSTOM_ONLY("prB"),
    PRIVATE_CHAT_SET("prS"),
    CHANNEL_CHAT_CUSTOM_ONLY("chB"),
    CHANNEL_CHAT_SET("chS"),
    CLAN_CHAT_CUSTOM_ONLY("clB"),
    CLAN_CHAT_SET("clS"),
    TRADE_CHAT_CUSTOM_ONLY("trB"),
    TRADE_CHAT_SET("trS");

    private final String serializedName;

    private static final Map<String, FilteredRegionField> BY_FILTERED_REGION_FIELD_SERIALIZED_NAME = new HashMap<>();

    //Cache element value to enum element map. toLowerCase to prevent potential case issues
    static {
        for (FilteredRegionField filteredRegionField : values()) {
            BY_FILTERED_REGION_FIELD_SERIALIZED_NAME.put(filteredRegionField.serializedName.toLowerCase(), filteredRegionField);
        }
    }

    //Get enum element based on serializedName. toLowerCase to prevent potential case issues
    @Nullable
    static FilteredRegionField getEnumElement(String serializedName) {
        return BY_FILTERED_REGION_FIELD_SERIALIZED_NAME.get(serializedName.toLowerCase());
    }
}