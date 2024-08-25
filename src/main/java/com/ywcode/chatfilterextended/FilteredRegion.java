package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class FilteredRegion {

    private final int regionID;
    private boolean publicChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptionsOH> publicChatSetOH = new HashSet<>(); //Set is always initialized, but empty when it should not be used
    private Set<ChatTabFilterOptions> publicChatSet = new HashSet<>(); //Set is always initialized, but empty when it should not be used
    private boolean privateChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> privateChatSet = new HashSet<>(); //Set is always initialized, but empty when it should not be used
    private boolean channelChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> channelChatSet = new HashSet<>(); //Set is always initialized, but empty when it should not be used
    private boolean clanChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> clanChatSet = new HashSet<>(); //Set is always initialized, but empty when it should not be used
    private boolean tradeChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> tradeChatSet = new HashSet<>(); //Set is always initialized, but empty when it should not be used

    public FilteredRegion(int regionID) {
        this.regionID = regionID;
    }
}