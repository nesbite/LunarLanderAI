package com.example.android.lunarlander.communication;

import com.example.android.lunarlander.LunarView;

public class ResetRequestMessage extends RequestMessage {

    public ResetRequestMessage() {
        super(MessageType.RESET_REQUEST);
    }

    @Override
    public void doAction(LunarView.LunarThread lunarThread) {
        lunarThread.doStart();
    }
}
