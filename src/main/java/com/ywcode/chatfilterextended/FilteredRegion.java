package com.ywcode.chatfilterextended;

import com.google.gson.annotations.JsonAdapter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

@JsonAdapter(FilteredRegionAdapter.class)
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class FilteredRegion {

    //private final int regionID; //Removed regionID because I'm always getting it via the regionID, thus storing it in a map that has the regionIDs as keys
    private boolean publicChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilter> publicOHChatSet = EnumSet.noneOf(ChatTabFilter.class); //Set is always initialized, but empty when it should not be used
    private Set<ChatTabFilter> publicChatSet = EnumSet.noneOf(ChatTabFilter.class); //Set is always initialized, but empty when it should not be used
    private boolean privateChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilter> privateChatSet = EnumSet.noneOf(ChatTabFilter.class); //Set is always initialized, but empty when it should not be used
    private boolean channelChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilter> channelChatSet = EnumSet.noneOf(ChatTabFilter.class); //Set is always initialized, but empty when it should not be used
    private boolean clanChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilter> clanChatSet = EnumSet.noneOf(ChatTabFilter.class); //Set is always initialized, but empty when it should not be used
    private boolean tradeChatCustomOnly; //Only activate custom, don't check sets
    private Set<ChatTabFilter> tradeChatSet = EnumSet.noneOf(ChatTabFilter.class); //Set is always initialized, but empty when it should not be used

    FilteredRegion() {
    }
}