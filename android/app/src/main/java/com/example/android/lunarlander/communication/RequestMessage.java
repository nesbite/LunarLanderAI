package com.example.android.lunarlander.communication;

import com.example.android.lunarlander.LunarView;

public abstract class RequestMessage extends LunarMessage {
    RequestMessage(MessageType messageType) {
        super(messageType);
    }

    public abstract void doAction(LunarView.LunarThread lunarThread) throws InterruptedException;
}
