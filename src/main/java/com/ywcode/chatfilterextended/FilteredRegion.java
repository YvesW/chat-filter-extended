package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class FilteredRegion {

    private int regionId;
    private boolean publicChatCustomOnly; //Only activate custom, don't check sets
    private HashSet<ChatTabFilterOptionsOH> publicChatSetOH;
    private HashSet<ChatTabFilterOptions> publicChatSet;
    private boolean privateChatCustomOnly; //Only activate custom, don't check sets
    private HashSet<ChatTabFilterOptions> privateChatSet;
    private boolean channelChatCustomOnly; //Only activate custom, don't check sets
    private HashSet<ChatTabFilterOptions> channelChatSet;
    private boolean clanChatCustomOnly; //Only activate custom, don't check sets
    private HashSet<ChatTabFilterOptions> clanChatSet;
    private boolean tradeChatCustomOnly; //Only activate custom, don't check sets
    private HashSet<ChatTabFilterOptions> tradeChatSet;

    public FilteredRegion(int regionId) {
        this.regionId = regionId;
    }
}