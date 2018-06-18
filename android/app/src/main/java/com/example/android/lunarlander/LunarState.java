package com.example.android.lunarlander;

import org.json.JSONException;
import org.json.JSONObject;

public class LunarState {
    /*
     * State-tracking constants
     */
    public static final int STATE_LOSE = 1;
    public static final int STATE_PAUSE = 2;
    public static final int STATE_READY = 3;
    public static final int STATE_RUNNING = 4;
    public static final int STATE_WIN = 5;

    static final String KEY_DIFFICULTY = "mDifficulty";
    static final String KEY_DX = "mDX";
    static final String KEY_DY = "mDY";
    static final String KEY_FUEL = "mFuel";
    static final String KEY_GOAL_ANGLE = "mGoalAngle";
    static final String KEY_GOAL_SPEED = "mGoalSpeed";
    static final String KEY_GOAL_WIDTH = "mGoalWidth";
    static final String KEY_GOAL_X = "mGoalX";
    static final String KEY_HEADING = "mHeading";
    static final String KEY_LANDER_HEIGHT = "mLanderHeight";
    static final String KEY_LANDER_WIDTH = "mLanderWidth";
    static final String KEY_WINS = "mWinsInARow";
    static final String KEY_X = "mX";
    static final String KEY_Y = "mY";

    /**
     * Current difficulty -- amount of fuel, allowed angle, etc. Default is
     * MEDIUM.
     */
    public int mDifficulty;

    /**
     * Velocity dx.
     */
    public double mDX;

    /**
     * Velocity dy.
     */
    public double mDY;

    /**
     * Is the engine burning?
     */
    public boolean mEngineFiring;

    /**
     * Fuel remaining
     */
    public double mFuel;

    /**
     * Allowed angle.
     */
    public int mGoalAngle;

    /**
     * Allowed speed.
     */
    public int mGoalSpeed;

    /**
     * Width of the landing pad.
     */
    public int mGoalWidth;

    /**
     * X of the landing pad.
     */
    public int mGoalX;

    /**
     * Lander heading in degrees, with 0 up, 90 right. Kept in the range
     * 0..360.
     */
    public double mHeading;

    /**
     * Pixel height of lander image.
     */
    public int mLanderHeight;

    /**
     * Pixel width of lander image.
     */
    public int mLanderWidth;

    /**
     * Used to figure out elapsed time between frames
     */
    public long mLastTime;

    /**
     * The state of the game. One of READY, RUNNING, PAUSE, LOSE, or WIN
     */
    public int mMode;

    /**
     * Currently rotating, -1 left, 0 none, 1 right.
     */
    public int mRotating;

    /**
     * Number of wins in a row.
     */
    public int mWinsInARow;

    /**
     * X of lander center.
     */
    public double mX;

    /**
     * Y of lander center.
     */
    public double mY;

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put(KEY_DIFFICULTY, mDifficulty)
                .put(KEY_DX, mDX)
                .put(KEY_DY, mDY)
                .put(KEY_FUEL, mFuel)
                .put(KEY_GOAL_ANGLE, mGoalAngle)
                .put(KEY_GOAL_SPEED, mGoalSpeed)
                .put(KEY_GOAL_WIDTH, mGoalWidth)
                .put(KEY_GOAL_X, mGoalX)
                .put(KEY_HEADING, mHeading)
                .put(KEY_LANDER_HEIGHT, mLanderHeight)
                .put(KEY_LANDER_WIDTH, mLanderWidth)
                .put(KEY_WINS, mWinsInARow)
                .put(KEY_X, mX)
                .put(KEY_Y, mY);
    }

}
