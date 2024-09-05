package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter(AccessLevel.PACKAGE)
public enum ShiftMenuSettingOptional {
    DISABLED("Disabled"),
    HOLD_SHIFT(ShiftMenuSetting.HOLD_SHIFT.toString()),
    ALWAYS(ShiftMenuSetting.ALWAYS.toString());

    private final String option;

    @Override
    public String toString() {
        return option;
    }
}