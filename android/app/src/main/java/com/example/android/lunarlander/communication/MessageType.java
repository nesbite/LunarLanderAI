package com.example.android.lunarlander.communication;


/**
 * Request:
 * <p>
 * 1) reset
 * {
 * type: "RESET_REQUEST"
 * }
 * <p>
 * 2) step
 * {
 * type: "STEP_REQUEST",
 * action: 2
 * }
 * <p>
 * <p>
 * Responses:
 * 1) observation
 * {
 * type: "OBSERVATION_RESPONSE",
 * observation: { ... },
 * reward: 1,
 * done: false
 * }
 */
public enum MessageType {

    STEP_REQUEST,
    RESET_REQUEST,
    OBSERVATION_RESPONSE

}
