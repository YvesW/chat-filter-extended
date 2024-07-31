package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class FilteredRegion {

    private int regionId;
    private boolean publicChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptionsOH> publicChatSetOH = new HashSet<>();
    private Set<ChatTabFilterOptions> publicChatSet = new HashSet<>();
    private boolean privateChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> privateChatSet = new HashSet<>();
    private boolean channelChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> channelChatSet = new HashSet<>();
    private boolean clanChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> clanChatSet = new HashSet<>();
    private boolean tradeChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> tradeChatSet = new HashSet<>();

    public FilteredRegion(int regionId) {
        this.regionId = regionId;
    }
}