package com.aidlab.gesturecontrol2;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class GestureClassifier {
    // Tensorflow lite model handler

    static String[] classes = new String[]{
            "cursor", "fist", "grab", "negative", "palm", "point", "thumb"
    };
    int TIME_STEP = 10;
    static int[] landmark_shape = new int[]{21, 2};
    private final Interpreter tflite;
    int BATCH_SIZE = 1;
    int n_classes = classes.length;
    int[] input_shape = new int[]{BATCH_SIZE, TIME_STEP, 1, landmark_shape[0], landmark_shape[1]};
    float[][][][][] inputs = new float[BATCH_SIZE][TIME_STEP][1][landmark_shape[0]][landmark_shape[1]];
    float[][] outputs = new float[BATCH_SIZE][n_classes];

    public GestureClassifier(Context activity) {
        tflite = new Interpreter(Objects.requireNonNull(loadModelFile(activity)));
    }

    /**
     * Memory-map the model file in Assets.
     */

    private ByteBuffer loadModelFile(Context activity) {
        try {
            AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getModelPath() {
        return "gesture.tflite";
    }

    public void predict(NDArray<Float> ndarray) {
        int[] indices = new int[4];
        for (int i = 0; i < BATCH_SIZE; i++) {
            indices[0] = i;
            for (int j = 0; j < TIME_STEP; j++) {
                indices[1] = j;
                for (int k = 0; k < landmark_shape[0]; k++) {
                    indices[2] = k;
                    for (int l = 0; l < landmark_shape[1]; l++) {
                        indices[3] = l;
                        double value = ndarray.getValue(indices);
                        inputs[i][j][0][k][l] = (float) value;
                    }
                }
            }
        }
        tflite.run(inputs, outputs);
    }

    public int getPredictedClass() {
        return getPredictedClass(outputs[0]);
    }

    public int getPredictedClass(float[] outputs) {
        Double[] _outputs = new Double[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            _outputs[i] = (double) outputs[i];
        }
        NDArray<Double> ndArray = NDArray.array(_outputs);
        return NDArray.argmax(ndArray);
    }
}
