package com.example.android.lunarlander.communication;


abstract class LunarMessage {

    private final MessageType messageType;

    LunarMessage(MessageType messageType) {
        this.messageType = messageType;
    }

    MessageType getMessageType() {
        return messageType;
    }

}
