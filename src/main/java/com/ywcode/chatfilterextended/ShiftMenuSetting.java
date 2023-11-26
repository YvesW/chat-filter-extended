package com.ywcode.chatfilterextended;

import lombok.*;

@RequiredArgsConstructor
@Getter
public enum ShiftMenuSetting {
    DISABLED("Disabled"),
    HOLDING_SHIFT("Holding Shift"),
    ALWAYS("Always enabled");

    private final String option;

    @Override
    public String toString() {
        return option;
    }
}