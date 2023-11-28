package com.ywcode.chatfilterextended;

import lombok.*;

@RequiredArgsConstructor
@Getter
public enum ShiftMenuSetting {
    DISABLED("Disabled"),
    HOLD_SHIFT("Hold Shift"),
    ALWAYS("Always");

    private final String option;

    @Override
    public String toString() {
        return option;
    }
}