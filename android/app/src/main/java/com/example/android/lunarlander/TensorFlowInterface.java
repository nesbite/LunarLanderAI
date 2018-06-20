package com.example.android.lunarlander;

import android.content.Context;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

public class TensorFlowInterface {
    static {
        System.loadLibrary("tensorflow_inference");
    }

    private TensorFlowInferenceInterface inferenceInterface;
//    private static final String MODEL_FILE = "file:///android_asset/frozen_model.pb";
    private static final String MODEL_FILE = "file:///android_asset/frozen_har_4.pb";
    private static final String INPUT_NODE = "x";
    private static final String[] OUTPUT_NODES = {"MatMul_3"};
    private static final String OUTPUT_NODE = "MatMul_3";
    private static final long[] INPUT_SIZE = {1, 6};
    private static final int OUTPUT_SIZE = 1;

    public TensorFlowInterface(final Context context) {
        inferenceInterface = new TensorFlowInferenceInterface(context.getAssets(), MODEL_FILE);
    }

//    np.array([state['mX'], state['mY'], state['mDX'], state['mDY'], state['mHeading'], state['mOnGoal']])
    public float[] predictProbabilities(float[] data) {
        float[] result = new float[OUTPUT_SIZE];
        inferenceInterface.feed(INPUT_NODE, data, INPUT_SIZE);
        inferenceInterface.run(OUTPUT_NODES);
        inferenceInterface.fetch(OUTPUT_NODE, result);

        //Jogging	  Sitting	Standing	Walking
        return result;
    }
}
