package com.example.android.lunarlander.communication;

import com.example.android.lunarlander.LunarState;

import org.json.JSONException;
import org.json.JSONObject;

import static com.example.android.lunarlander.LunarState.STATE_RUNNING;
import static com.example.android.lunarlander.LunarState.STATE_WIN;

public class ObservationResponseMessage extends LunarMessage {

    private LunarState observation;

    public ObservationResponseMessage(LunarState observation) {
        super(MessageType.OBSERVATION_RESPONSE);
        this.observation = observation;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("type", getMessageType().name())
                .put("observation", observation.toJson())
                .put("done", observation.mMode != STATE_RUNNING)
                .put("reward", observation.mMode == STATE_WIN ? 1 : 0);
    }
}
