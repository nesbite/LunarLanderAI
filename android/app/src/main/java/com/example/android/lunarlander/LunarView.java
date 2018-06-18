/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.lunarlander;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;

import com.example.android.lunarlander.communication.MessageType;
import com.example.android.lunarlander.communication.ObservationResponseMessage;
import com.example.android.lunarlander.communication.RequestMessage;
import com.example.android.lunarlander.communication.ResetRequestMessage;
import com.example.android.lunarlander.communication.StepRequestMessage;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CyclicBarrier;

import static com.example.android.lunarlander.LunarState.STATE_RUNNING;


/**
 * View that draws, takes keystrokes, etc. for a simple LunarLander game.
 * <p>
 * Has a mode which RUNNING, PAUSED, etc. Has a x, y, dx, dy, ... capturing the
 * current ship physics. All x/y etc. are measured with (0,0) at the lower left.
 * updatePhysics() advances the physics based on realtime. draw() renders the
 * ship, and does an invalidate() to prompt another draw() as soon as possible
 * by the system.
 */
public class LunarView extends SurfaceView implements SurfaceHolder.Callback {

    public static final String TAG = LunarView.class.getSimpleName();

    private boolean mqttRequest = false;

    enum LunarInputEvent {
        CONTROL(KeyEvent.KEYCODE_DPAD_UP),
        LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
        RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT),
        ENGINE(KeyEvent.KEYCODE_DPAD_CENTER);

        LunarInputEvent(int keyEvent) {
            this.keyEvent = keyEvent;
        }

        private final int keyEvent;

        public int getKeyEvent() {
            return keyEvent;
        }
    }

    public class LunarThread extends Thread {
        /*
         * Difficulty setting constants
         */
        public static final int DIFFICULTY_EASY = 0;
        public static final int DIFFICULTY_HARD = 1;
        public static final int DIFFICULTY_MEDIUM = 2;
        /*
         * Physics constants
         */
        public static final int PHYS_DOWN_ACCEL_SEC = 35;
        public static final int PHYS_FIRE_ACCEL_SEC = 80;
        public static final int PHYS_FUEL_INIT = 60;
        public static final int PHYS_FUEL_MAX = 100;
        public static final int PHYS_FUEL_SEC = 10;
        public static final int PHYS_SLEW_SEC = 120; // degrees/second rotate
        public static final int PHYS_SPEED_HYPERSPACE = 180;
        public static final int PHYS_SPEED_INIT = 30;
        public static final int PHYS_SPEED_MAX = 120;

        /*
         * Goal condition constants
         */
        public static final int TARGET_ANGLE = 25; // > this angle means crash
        public static final int TARGET_BOTTOM_PADDING = 17; // px below gear
        public static final int TARGET_PAD_HEIGHT = 8; // how high above ground
        public static final int TARGET_SPEED = 100; // > this speed means crash
        public static final double TARGET_WIDTH = 5; // width of target
        /*
         * UI constants (i.e. the speed & fuel bars)
         */
        public static final int UI_BAR = 100; // width of the bar(s)
        public static final int UI_BAR_HEIGHT = 10; // height of the bar(s)

        public final LunarState lunarState = new LunarState();

        /*
         * Member (state) fields
         */
        /**
         * The drawable to use as the background of the animation canvas
         */
        private Bitmap mBackgroundImage;

        /**
         * Current height of the surface/canvas.
         *
         * @see #setSurfaceSize
         */
        private int mCanvasHeight = 1;

        /**
         * Current width of the surface/canvas.
         *
         * @see #setSurfaceSize
         */
        private int mCanvasWidth = 1;

        /**
         * What to draw for the Lander when it has crashed
         */
        private Drawable mCrashedImage;

        /**
         * What to draw for the Lander when the engine is firing
         */
        private Drawable mFiringImage;

        /**
         * Message handler used by lunarThread to interact with TextView
         */
        private Handler mHandler;


        /**
         * What to draw for the Lander in its normal state
         */
        private Drawable mLanderImage;

        /**
         * Paint to draw the lines on screen.
         */
        private Paint mLinePaint;

        /**
         * "Bad" speed-too-high variant of the line color.
         */
        private Paint mLinePaintBad;

        /**
         * Indicate whether the surface has been created & is ready to draw
         */
        private boolean mRun = false;

        /**
         * Scratch rect object.
         */
        private RectF mScratchRect;

        /**
         * Handle to the surface manager object we interact with
         */
        private SurfaceHolder mSurfaceHolder;

        public LunarThread(SurfaceHolder surfaceHolder, Context context,
                           Handler handler) {
            // get handles to some important objects
            mSurfaceHolder = surfaceHolder;
            mHandler = handler;
            mContext = context;

            Resources res = context.getResources();
            // cache handles to our key sprites & other drawables
            mLanderImage = context.getResources().getDrawable(
                    R.drawable.lander_plain);
            mFiringImage = context.getResources().getDrawable(
                    R.drawable.lander_firing);
            mCrashedImage = context.getResources().getDrawable(
                    R.drawable.lander_crashed);

            // load background image as a Bitmap instead of a Drawable b/c
            // we don't need to transform it and it's faster to draw this way
            mBackgroundImage = BitmapFactory.decodeResource(res,
                    R.drawable.earthrise);

            // Use the regular lander image as the model size for all sprites
            lunarState.mLanderWidth = mLanderImage.getIntrinsicWidth();
            lunarState.mLanderHeight = mLanderImage.getIntrinsicHeight();

            // Initialize paints for speedometer
            mLinePaint = new Paint();
            mLinePaint.setAntiAlias(true);
            mLinePaint.setARGB(255, 0, 255, 0);

            mLinePaintBad = new Paint();
            mLinePaintBad.setAntiAlias(true);
            mLinePaintBad.setARGB(255, 120, 180, 0);

            mScratchRect = new RectF(0, 0, 0, 0);

            lunarState.mWinsInARow = 0;
            lunarState.mDifficulty = DIFFICULTY_MEDIUM;

            // initial show-up of lander (not yet playing)
            lunarState.mX = lunarState.mLanderWidth;
            lunarState.mY = lunarState.mLanderHeight * 2;
            lunarState.mFuel = PHYS_FUEL_INIT;
            lunarState.mDX = 0;
            lunarState.mDY = 0;
            lunarState.mHeading = 0;
            lunarState.mEngineFiring = true;
        }

        /**
         * Starts the game, setting parameters for the current difficulty.
         */
        public void doStart() {
            synchronized (mSurfaceHolder) {
                // First set the game for Medium difficulty
                lunarState.mFuel = PHYS_FUEL_INIT;
                lunarState.mEngineFiring = false;
                lunarState.mGoalWidth = (int) (lunarState.mLanderWidth * TARGET_WIDTH);
                lunarState.mGoalSpeed = TARGET_SPEED;
                lunarState.mGoalAngle = TARGET_ANGLE;
                int speedInit = PHYS_SPEED_INIT;

                // Adjust difficulty params for EASY/HARD
                if (lunarState.mDifficulty == DIFFICULTY_EASY) {
                    lunarState.mFuel = lunarState.mFuel * 3 / 2;
                    lunarState.mGoalWidth = lunarState.mGoalWidth * 4 / 3;
                    lunarState.mGoalSpeed = lunarState.mGoalSpeed * 3 / 2;
                    lunarState.mGoalAngle = lunarState.mGoalAngle * 4 / 3;
                    speedInit = speedInit * 3 / 4;
                } else if (lunarState.mDifficulty == DIFFICULTY_HARD) {
                    lunarState.mFuel = lunarState.mFuel * 7 / 8;
                    lunarState.mGoalWidth = lunarState.mGoalWidth * 3 / 4;
                    lunarState.mGoalSpeed = lunarState.mGoalSpeed * 7 / 8;
                    speedInit = speedInit * 4 / 3;
                }

                // pick a convenient initial location for the lander sprite
                lunarState.mX = mCanvasWidth / 2;
                lunarState.mY = mCanvasHeight - lunarState.mLanderHeight / 2;

                // start with a little random motion
                lunarState.mDY = Math.random() * -speedInit;
                lunarState.mDX = Math.random() * 2 * speedInit - speedInit;
                lunarState.mHeading = 0;

                // Figure initial spot for landing, not too near center
                while (true) {
                    lunarState.mGoalX = (int) (Math.random() * (mCanvasWidth - lunarState.mGoalWidth));
                    if (Math.abs(lunarState.mGoalX - (lunarState.mX - lunarState.mLanderWidth / 2)) > mCanvasHeight / 6)
                        break;
                }

                lunarState.mLastTime = System.currentTimeMillis() + 100;
                setState(STATE_RUNNING);
            }
        }

        /**
         * Pauses the physics update & animation.
         */
        public void pause() {
            synchronized (mSurfaceHolder) {
                if (lunarState.mMode == STATE_RUNNING) setState(LunarState.STATE_PAUSE);
            }
        }

        /**
         * Restores game state from the indicated Bundle. Typically called when
         * the Activity is being restored after having been previously
         * destroyed.
         *
         * @param savedState Bundle containing the game state
         */
        public synchronized void restoreState(Bundle savedState) {
            synchronized (mSurfaceHolder) {
                setState(LunarState.STATE_PAUSE);
                lunarState.mRotating = 0;
                lunarState.mEngineFiring = false;

                lunarState.mDifficulty = savedState.getInt(LunarState.KEY_DIFFICULTY);
                lunarState.mX = savedState.getDouble(LunarState.KEY_X);
                lunarState.mY = savedState.getDouble(LunarState.KEY_Y);
                lunarState.mDX = savedState.getDouble(LunarState.KEY_DX);
                lunarState.mDY = savedState.getDouble(LunarState.KEY_DY);
                lunarState.mHeading = savedState.getDouble(LunarState.KEY_HEADING);

                lunarState.mLanderWidth = savedState.getInt(LunarState.KEY_LANDER_WIDTH);
                lunarState.mLanderHeight = savedState.getInt(LunarState.KEY_LANDER_HEIGHT);
                lunarState.mGoalX = savedState.getInt(LunarState.KEY_GOAL_X);
                lunarState.mGoalSpeed = savedState.getInt(LunarState.KEY_GOAL_SPEED);
                lunarState.mGoalAngle = savedState.getInt(LunarState.KEY_GOAL_ANGLE);
                lunarState.mGoalWidth = savedState.getInt(LunarState.KEY_GOAL_WIDTH);
                lunarState.mWinsInARow = savedState.getInt(LunarState.KEY_WINS);
                lunarState.mFuel = savedState.getDouble(LunarState.KEY_FUEL);
            }
        }

        @Override
        public void run() {
            while (mRun) {
                Canvas c = null;
                try {
                    c = mSurfaceHolder.lockCanvas(null);
                    synchronized (mSurfaceHolder) {
                        if (lunarState.mMode == STATE_RUNNING) {
                            updatePhysics();
                        }
                        doDraw(c);
                    }
                } finally {
                    // do this in a finally so that if an exception is thrown
                    // during the above, we don't leave the Surface in an
                    // inconsistent state
                    if (c != null) {
                        mSurfaceHolder.unlockCanvasAndPost(c);
                    }
                }

                try {
                    if (mqttRequest) {
                        Log.i(TAG, "[CANVAS] cyclicBarrier - 1");
                        cyclicBarrier.await();
                        Log.i(TAG, "[CANVAS] cyclicBarrier - 2");

                        mqttThread.publishCurrentGameState();
                        mqttRequest = false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }

        /**
         * Dump game state to the provided Bundle. Typically called when the
         * Activity is being suspended.
         *
         * @return Bundle with this view's state
         */
        public Bundle saveState(Bundle map) {
            synchronized (mSurfaceHolder) {
                if (map != null) {
                    map.putInt(LunarState.KEY_DIFFICULTY, Integer.valueOf(lunarState.mDifficulty));
                    map.putDouble(LunarState.KEY_X, Double.valueOf(lunarState.mX));
                    map.putDouble(LunarState.KEY_Y, Double.valueOf(lunarState.mY));
                    map.putDouble(LunarState.KEY_DX, Double.valueOf(lunarState.mDX));
                    map.putDouble(LunarState.KEY_DY, Double.valueOf(lunarState.mDY));
                    map.putDouble(LunarState.KEY_HEADING, Double.valueOf(lunarState.mHeading));
                    map.putInt(LunarState.KEY_LANDER_WIDTH, Integer.valueOf(lunarState.mLanderWidth));
                    map.putInt(LunarState.KEY_LANDER_HEIGHT, Integer
                            .valueOf(lunarState.mLanderHeight));
                    map.putInt(LunarState.KEY_GOAL_X, Integer.valueOf(lunarState.mGoalX));
                    map.putInt(LunarState.KEY_GOAL_SPEED, Integer.valueOf(lunarState.mGoalSpeed));
                    map.putInt(LunarState.KEY_GOAL_ANGLE, Integer.valueOf(lunarState.mGoalAngle));
                    map.putInt(LunarState.KEY_GOAL_WIDTH, Integer.valueOf(lunarState.mGoalWidth));
                    map.putInt(LunarState.KEY_WINS, Integer.valueOf(lunarState.mWinsInARow));
                    map.putDouble(LunarState.KEY_FUEL, Double.valueOf(lunarState.mFuel));
                }
            }
            return map;
        }

        /**
         * Sets the current difficulty.
         *
         * @param difficulty
         */
        public void setDifficulty(int difficulty) {
            synchronized (mSurfaceHolder) {
                lunarState.mDifficulty = difficulty;
            }
        }

        /**
         * Sets if the engine is currently firing.
         */
        public void setFiring(boolean firing) {
            synchronized (mSurfaceHolder) {
                lunarState.mEngineFiring = firing;
            }
        }

        /**
         * Used to signal the lunarThread whether it should be running or not.
         * Passing true allows the lunarThread to run; passing false will shut it
         * down if it's already running. Calling start() after this was most
         * recently called with false will result in an immediate shutdown.
         *
         * @param b true to run, false to shut down
         */
        public void setRunning(boolean b) {
            mRun = b;
        }

        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         *
         * @param mode one of the STATE_* constants
         * @see #setState(int, CharSequence)
         */
        public void setState(int mode) {
            synchronized (mSurfaceHolder) {
                setState(mode, null);
            }
        }

        /**
         * Sets the game mode. That is, whether we are running, paused, in the
         * failure state, in the victory state, etc.
         *
         * @param mode    one of the STATE_* constants
         * @param message string to add to screen or null
         */
        public void setState(int mode, CharSequence message) {
            /*
             * This method optionally can cause a text message to be displayed
             * to the user when the mode changes. Since the View that actually
             * renders that text is part of the main View hierarchy and not
             * owned by this lunarThread, we can't touch the state of that View.
             * Instead we use a Message + Handler to relay commands to the main
             * lunarThread, which updates the user-text View.
             */
            synchronized (mSurfaceHolder) {
                lunarState.mMode = mode;

                if (lunarState.mMode == STATE_RUNNING) {
                    Message msg = mHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("text", "");
                    b.putInt("viz", View.INVISIBLE);
                    msg.setData(b);
                    mHandler.sendMessage(msg);
                } else {
                    lunarState.mRotating = 0;
                    lunarState.mEngineFiring = false;
                    Resources res = mContext.getResources();
                    CharSequence str = "";
                    if (lunarState.mMode == LunarState.STATE_READY)
                        str = res.getText(R.string.mode_ready);
                    else if (lunarState.mMode == LunarState.STATE_PAUSE)
                        str = res.getText(R.string.mode_pause);
                    else if (lunarState.mMode == LunarState.STATE_LOSE)
                        str = res.getText(R.string.mode_lose);
                    else if (lunarState.mMode == LunarState.STATE_WIN)
                        str = res.getString(R.string.mode_win_prefix)
                                + lunarState.mWinsInARow + " "
                                + res.getString(R.string.mode_win_suffix);

                    if (message != null) {
                        str = message + "\n" + str;
                    }

                    if (lunarState.mMode == LunarState.STATE_LOSE) lunarState.mWinsInARow = 0;

                    Message msg = mHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("text", str.toString());
                    b.putInt("viz", View.VISIBLE);
                    msg.setData(b);
                    mHandler.sendMessage(msg);
                }
            }
        }

        /* Callback invoked when the surface dimensions change. */
        public void setSurfaceSize(int width, int height) {
            // synchronized to make sure these all change atomically
            synchronized (mSurfaceHolder) {
                mCanvasWidth = width;
                mCanvasHeight = height;

                // don't forget to resize the background image
                mBackgroundImage = Bitmap.createScaledBitmap(
                        mBackgroundImage, width, height, true);
            }
        }

        /**
         * Resumes from a pause.
         */
        public void unpause() {
            // Move the real time clock up to now
            synchronized (mSurfaceHolder) {
                lunarState.mLastTime = System.currentTimeMillis() + 100;
            }
            setState(STATE_RUNNING);
        }

        /**
         * Handles a key-down event.
         *
         * @param keyCode the key that was pressed
         * @return true
         */
        public boolean doKeyDown(int keyCode) {
            synchronized (mSurfaceHolder) {
                boolean okStart = false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) okStart = true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) okStart = true;
                if (keyCode == KeyEvent.KEYCODE_S) okStart = true;

                if (okStart
                        && (lunarState.mMode == LunarState.STATE_READY || lunarState.mMode == LunarState.STATE_LOSE || lunarState.mMode == LunarState.STATE_WIN)) {
                    // ready-to-start -> start
                    doStart();
                    return true;
                } else if (lunarState.mMode == LunarState.STATE_PAUSE && okStart) {
                    // paused -> running
                    unpause();
                    return true;
                } else if (lunarState.mMode == STATE_RUNNING) {
                    // center/space -> fire
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_SPACE) {
                        setFiring(true);
                        return true;
                        // left/q -> left
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            || keyCode == KeyEvent.KEYCODE_Q) {
                        lunarState.mRotating = -1;
                        return true;
                        // right/w -> right
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            || keyCode == KeyEvent.KEYCODE_W) {
                        lunarState.mRotating = 1;
                        return true;
                        // up -> pause
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        pause();
                        return true;
                    }
                }

                return false;
            }
        }

        /**
         * Handles a key-up event.
         *
         * @param keyCode the key that was pressed
         * @return true if the key was handled and consumed, or else false
         */
        public boolean doKeyUp(int keyCode) {
            boolean handled = false;

            synchronized (mSurfaceHolder) {
                if (lunarState.mMode == STATE_RUNNING) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_SPACE) {
                        setFiring(false);
                        handled = true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            || keyCode == KeyEvent.KEYCODE_Q
                            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            || keyCode == KeyEvent.KEYCODE_W) {
                        lunarState.mRotating = 0;
                        handled = true;
                    }
                }
            }

            return handled;
        }

        /**
         * Draws the ship, fuel/speed bars, and background to the provided
         * Canvas.
         */
        private void doDraw(Canvas canvas) {
            // Draw the background image. Operations on the Canvas accumulate
            // so this is like clearing the screen.
            if (canvas == null) return;

            canvas.drawBitmap(mBackgroundImage, 0, 0, null);

            int yTop = mCanvasHeight - ((int) lunarState.mY + lunarState.mLanderHeight / 2);
            int xLeft = (int) lunarState.mX - lunarState.mLanderWidth / 2;

            // Draw the fuel gauge
            int fuelWidth = (int) (UI_BAR * lunarState.mFuel / PHYS_FUEL_MAX);
            mScratchRect.set(4, 4, 4 + fuelWidth, 4 + UI_BAR_HEIGHT);
            canvas.drawRect(mScratchRect, mLinePaint);

            // Draw the speed gauge, with a two-tone effect
            double speed = Math.sqrt(lunarState.mDX * lunarState.mDX + lunarState.mDY * lunarState.mDY);
            int speedWidth = (int) (UI_BAR * speed / PHYS_SPEED_MAX);

            if (speed <= lunarState.mGoalSpeed) {
                mScratchRect.set(4 + UI_BAR + 4, 4,
                        4 + UI_BAR + 4 + speedWidth, 4 + UI_BAR_HEIGHT);
                canvas.drawRect(mScratchRect, mLinePaint);
            } else {
                // Draw the bad color in back, with the good color in front of
                // it
                mScratchRect.set(4 + UI_BAR + 4, 4,
                        4 + UI_BAR + 4 + speedWidth, 4 + UI_BAR_HEIGHT);
                canvas.drawRect(mScratchRect, mLinePaintBad);
                int goalWidth = (UI_BAR * lunarState.mGoalSpeed / PHYS_SPEED_MAX);
                mScratchRect.set(4 + UI_BAR + 4, 4, 4 + UI_BAR + 4 + goalWidth,
                        4 + UI_BAR_HEIGHT);
                canvas.drawRect(mScratchRect, mLinePaint);
            }

            // Draw the landing pad
            canvas.drawLine(lunarState.mGoalX, 1 + mCanvasHeight - TARGET_PAD_HEIGHT,
                    lunarState.mGoalX + lunarState.mGoalWidth, 1 + mCanvasHeight - TARGET_PAD_HEIGHT,
                    mLinePaint);


            // Draw the ship with its current rotation
            canvas.save();
            canvas.rotate((float) lunarState.mHeading, (float) lunarState.mX, mCanvasHeight
                    - (float) lunarState.mY);
            if (lunarState.mMode == LunarState.STATE_LOSE) {
                mCrashedImage.setBounds(xLeft, yTop, xLeft + lunarState.mLanderWidth, yTop
                        + lunarState.mLanderHeight);
                mCrashedImage.draw(canvas);
            } else if (lunarState.mEngineFiring) {
                mFiringImage.setBounds(xLeft, yTop, xLeft + lunarState.mLanderWidth, yTop
                        + lunarState.mLanderHeight);
                mFiringImage.draw(canvas);
            } else {
                mLanderImage.setBounds(xLeft, yTop, xLeft + lunarState.mLanderWidth, yTop
                        + lunarState.mLanderHeight);
                mLanderImage.draw(canvas);
            }
            canvas.restore();
        }

        /**
         * Figures the lander state (x, y, fuel, ...) based on the passage of
         * realtime. Does not invalidate(). Called at the start of draw().
         * Detects the end-of-game and sets the UI to the next state.
         */
        private void updatePhysics() {
            long now = System.currentTimeMillis();

            // Do nothing if lunarState.mLastTime is in the future.
            // This allows the game-start to delay the start of the physics
            // by 100ms or whatever.
            if (lunarState.mLastTime > now) return;

            double elapsed = (now - lunarState.mLastTime) / 1000.0;

            // lunarState.mRotating -- update heading
            if (lunarState.mRotating != 0) {
                lunarState.mHeading += lunarState.mRotating * (PHYS_SLEW_SEC * elapsed);

                // Bring things back into the range 0..360
                if (lunarState.mHeading < 0)
                    lunarState.mHeading += 360;
                else if (lunarState.mHeading >= 360) lunarState.mHeading -= 360;
            }

            // Base accelerations -- 0 for x, gravity for y
            double ddx = 0.0;
            double ddy = -PHYS_DOWN_ACCEL_SEC * elapsed;

            if (lunarState.mEngineFiring) {
                // taking 0 as up, 90 as to the right
                // cos(deg) is ddy component, sin(deg) is ddx component
                double elapsedFiring = elapsed;
                double fuelUsed = elapsedFiring * PHYS_FUEL_SEC;

                // tricky case where we run out of fuel partway through the
                // elapsed
                if (fuelUsed > lunarState.mFuel) {
                    elapsedFiring = lunarState.mFuel / fuelUsed * elapsed;
                    fuelUsed = lunarState.mFuel;

                    // Oddball case where we adjust the "control" from here
                    lunarState.mEngineFiring = false;
                }

                lunarState.mFuel -= fuelUsed;

                // have this much acceleration from the engine
                double accel = PHYS_FIRE_ACCEL_SEC * elapsedFiring;

                double radians = 2 * Math.PI * lunarState.mHeading / 360;
                ddx = Math.sin(radians) * accel;
                ddy += Math.cos(radians) * accel;
            }

            double dxOld = lunarState.mDX;
            double dyOld = lunarState.mDY;

            // figure speeds for the end of the period
            lunarState.mDX += ddx;
            lunarState.mDY += ddy;

            // figure position based on average speed during the period
            lunarState.mX += elapsed * (lunarState.mDX + dxOld) / 2;
            lunarState.mY += elapsed * (lunarState.mDY + dyOld) / 2;

            lunarState.mLastTime = now;

            // Evaluate if we have landed ... stop the game
            double yLowerBound = TARGET_PAD_HEIGHT + lunarState.mLanderHeight / 2
                    - TARGET_BOTTOM_PADDING;
            if (lunarState.mY <= yLowerBound) {
                lunarState.mY = yLowerBound;

                int result = LunarState.STATE_LOSE;
                CharSequence message = "";
                Resources res = mContext.getResources();
                double speed = Math.sqrt(lunarState.mDX * lunarState.mDX + lunarState.mDY * lunarState.mDY);
                boolean onGoal = (lunarState.mGoalX <= lunarState.mX - lunarState.mLanderWidth / 2 && lunarState.mX
                        + lunarState.mLanderWidth / 2 <= lunarState.mGoalX + lunarState.mGoalWidth);
                // "Hyperspace" win -- upside down, going fast,
                // puts you back at the top.
                if (onGoal && Math.abs(lunarState.mHeading - 180) < lunarState.mGoalAngle
                        && speed > PHYS_SPEED_HYPERSPACE) {
                    result = LunarState.STATE_WIN;
                    lunarState.mWinsInARow++;
                    doStart();

                    return;
                    // Oddball case: this case does a return, all other cases
                    // fall through to setMode() below.
                } else if (!onGoal) {
                    message = res.getText(R.string.message_off_pad);
                } else if (!(lunarState.mHeading <= lunarState.mGoalAngle || lunarState.mHeading >= 360 - lunarState.mGoalAngle)) {
                    message = res.getText(R.string.message_bad_angle);
                } else if (speed > lunarState.mGoalSpeed) {
                    message = res.getText(R.string.message_too_fast);
                } else {
                    result = LunarState.STATE_WIN;
                    lunarState.mWinsInARow++;
                }

                setState(result, message);
            }
        }
    }

    class MqttThread extends Thread {
        private static final String BROKER = "tcp://192.168.1.14:1883";
        private static final String CLIENT_ID = "AndroidLunarLander";

        private MqttClient mqttClient;
        private MqttTopic mqttTopic;
        private String pub_topic = "DATA_FROM_ANDROID";
        private String sub_topic = "DATA_FROM_AI";

        @Override
        public void run() {

            try {
                MemoryPersistence persistence = new MemoryPersistence();
                mqttClient = new MqttClient(BROKER, CLIENT_ID, persistence);
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);
                System.out.println("Connecting to broker: " + BROKER);
                mqttClient.connect(connOpts);
                System.out.println("Connected");
                mqttClient.subscribe(sub_topic);

                mqttTopic = mqttClient.getTopic(pub_topic);
                mqttClient.setCallback(new DefaultMqttCallback());

            } catch (MqttException me) {
                System.out.println("reason " + me.getReasonCode());
                System.out.println("msg " + me.getMessage());
                System.out.println("loc " + me.getLocalizedMessage());
                System.out.println("cause " + me.getCause());
                System.out.println("excep " + me);
                me.printStackTrace();
            }
        }

        public void publishCurrentGameState() throws MqttException, JSONException {
            if (mqttTopic != null) {
                JSONObject gameStateJson = new ObservationResponseMessage(lunarThread.lunarState).toJson();
                MqttMessage androidMessage = new MqttMessage(gameStateJson.toString().getBytes());
                androidMessage.setQos(2);
                mqttTopic.publish(androidMessage);

                Log.d(TAG, "Message with current game state published");
            }
        }

        class DefaultMqttCallback implements MqttCallback {

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("Connection lost");
            }

            @Override
            public void messageArrived(String topic, MqttMessage m) throws Exception {
                Log.d(TAG, "Message arrived: " + m + ", topic:" + topic);
                JSONObject json = new JSONObject(new String(m.getPayload()));
                RequestMessage message = parseMessage(json);

                mqttRequest = true;

                message.doAction(lunarThread);
                Log.i(TAG, "[MQTT] cyclicBarrier - 1");
                cyclicBarrier.await();
                Log.i(TAG, "[MQTT] cyclicBarrier - 2");
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("Delivery complete");
            }

            private RequestMessage parseMessage(JSONObject json) throws JSONException {
                switch (MessageType.valueOf(json.optString("type"))) {
                    case RESET_REQUEST:
                        return new ResetRequestMessage();
                    case STEP_REQUEST:
                        return new StepRequestMessage(json.getInt("action"));
                    default:
                        throw new IllegalArgumentException("Unkown type of message: " + json);

                }
            }
        }
    }

    /**
     * Handle to the application context, used to e.g. fetch Drawables.
     */
    private Context mContext;

    /**
     * Pointer to the text view to display "Paused.." etc.
     */
    private TextView mStatusText;

    /**
     * The lunarThread that actually draws the animation
     */
    private LunarThread lunarThread;

    private MqttThread mqttThread;

    // CyclicBarrier is used for synchronization between Mqtt nad Lunar threads
    private CyclicBarrier cyclicBarrier;

    public LunarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // register our interest in hearing about changes to our surface
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

        // create lunarThread only; it's started in surfaceCreated()
        lunarThread = new LunarThread(holder, context, new Handler() {
            @Override
            public void handleMessage(Message m) {
                mStatusText.setVisibility(m.getData().getInt("viz"));
                mStatusText.setText(m.getData().getString("text"));
            }
        });

        mqttThread = new MqttThread();
        cyclicBarrier = new CyclicBarrier(2);
    }

    /**
     * Fetches the animation lunarThread corresponding to this LunarView.
     *
     * @return the animation lunarThread
     */
    public LunarThread getThread() {
        return lunarThread;
    }

    public void onInputEventDown(LunarInputEvent event) {
        lunarThread.doKeyDown(event.getKeyEvent());
    }

    public void onInputEventUp(LunarInputEvent event) {
        lunarThread.doKeyUp(event.getKeyEvent());
    }

    /**
     * Standard window-focus override. Notice focus lost so we can pause on
     * focus lost. e.g. user switches to take a call.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (!hasWindowFocus) lunarThread.pause();
    }

    /**
     * Installs a pointer to the text view used for messages.
     */
    public void setTextView(TextView textView) {
        mStatusText = textView;
    }

    /* Callback invoked when the surface dimensions change. */
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        lunarThread.setSurfaceSize(width, height);
    }

    /*
     * Callback invoked when the Surface has been created and is ready to be
     * used.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // start the lunarThread here so that we don't busy-wait in run()
        // waiting for the surface to be created
        lunarThread.setRunning(true);
        lunarThread.start();

        mqttThread.start();
    }

    /*
     * Callback invoked when the Surface has been destroyed and must no longer
     * be touched. WARNING: after this method returns, the Surface/Canvas must
     * never be touched again!
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // we have to tell lunarThread to shut down & wait for it to finish, or else
        // it might touch the Surface after we return and explode
        boolean retry = true;
        lunarThread.setRunning(false);
        mqttThread.interrupt();
        while (retry) {
            try {
                lunarThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }
}
