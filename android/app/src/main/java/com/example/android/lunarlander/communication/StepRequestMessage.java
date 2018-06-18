package com.example.android.lunarlander.communication;

import android.util.Log;

import com.example.android.lunarlander.LunarView;

public class StepRequestMessage extends RequestMessage {

    private final int action;

    public StepRequestMessage(int action) {
        super(MessageType.STEP_REQUEST);
        this.action = action;
    }

    public int getAction() {
        return action;
    }

    @Override
    public void doAction(LunarView.LunarThread lunarThread) {
        Log.d("DUPA", "doAction: " + action);
        lunarThread.doKeyDown(action);
    }
}
