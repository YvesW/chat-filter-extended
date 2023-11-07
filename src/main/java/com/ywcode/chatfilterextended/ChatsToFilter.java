package com.ywcode.chatfilterextended;

import lombok.*;

    @RequiredArgsConstructor
    @Getter
    public enum ChatsToFilter {
        PUBLIC("Public"),
        PRIVATE("Private"),
        CHANNEL("Channel"),
        CLAN("Clan"),
        TRADE("Trade");

        private final String option;

        @Override
        public String toString() {
            return option;
        }
    }
    //todo: probably remove if redundant
