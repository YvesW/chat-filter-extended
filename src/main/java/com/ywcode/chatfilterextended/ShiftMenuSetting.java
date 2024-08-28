package com.ywcode.chatfilterextended;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ShiftMenuSetting {
    HOLD_SHIFT("Hold Shift"),
    ALWAYS("Always");

    private final String option;

    @Override
    public String toString() {
        return option;
    }
}