package com.ywcode.chatfilterextended;

import lombok.*;

@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class ChatTabAlert {

    private boolean filtered; //Is the chatmessage filtered?
    private boolean ownMessage; //Is it a message by the local player?
    private int chatTabNumber; //1 = game. 2 = public. 3 = friends but does not show up when private is split (which is good, because the tab does also not flash then!). 4 = fc. 5 = cc. 6 = trade.
    private int varcIntCountdownValue; //The VarCIntCountdownValue

    public ChatTabAlert(boolean filtered, boolean ownMessage, int chatTabNumber, int varcIntCountdownValue) {
        this.filtered = false;
        this.ownMessage = false;
        this.chatTabNumber = chatTabNumber;
        this.varcIntCountdownValue = varcIntCountdownValue;
    }
}


