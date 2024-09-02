package com.ywcode.chatfilterextended;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class FilteredRegion {

    //private final int regionID; //Removed regionID because I'm always getting it via the regionID, thus storing it in a map that has the regionIDs as keys
    private boolean publicChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptionsOH> publicChatSetOH = EnumSet.noneOf(ChatTabFilterOptionsOH.class); //Set is always initialized, but empty when it should not be used
    private Set<ChatTabFilterOptions> publicChatSet = EnumSet.noneOf(ChatTabFilterOptions.class); //Set is always initialized, but empty when it should not be used
    private boolean privateChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> privateChatSet = EnumSet.noneOf(ChatTabFilterOptions.class); //Set is always initialized, but empty when it should not be used
    private boolean channelChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> channelChatSet = EnumSet.noneOf(ChatTabFilterOptions.class); //Set is always initialized, but empty when it should not be used
    private boolean clanChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> clanChatSet = EnumSet.noneOf(ChatTabFilterOptions.class); //Set is always initialized, but empty when it should not be used
    private boolean tradeChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilterOptions> tradeChatSet = EnumSet.noneOf(ChatTabFilterOptions.class); //Set is always initialized, but empty when it should not be used

    public FilteredRegion() {
    }
}