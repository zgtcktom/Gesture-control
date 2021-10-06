package com.aidlab.gesturecontrol2;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.mediapipe.formats.proto.LandmarkProto;

import java.util.List;

import static com.aidlab.gesturecontrol2.GestureClassifier.classes;
import static com.aidlab.gesturecontrol2.GestureClassifier.landmark_shape;

public class GestureDetector {
    private static final String TAG = "GestureDetector";
    private final CircularQueue<Float[][]> landmarkQueue;
    private final GestureClassifier classifier;
    private final Float[][] emptyLandmarks;
    private final Context context;

    GestureDetector(Context context, GestureClassifier classifier){
        this.context = context;
        this.classifier = classifier;
        this.emptyLandmarks = new Float[landmark_shape[0]][landmark_shape[1]];
        for (int i = 0; i < landmark_shape[0]; i++) {
            for (int j = 0; j < landmark_shape[1]; j++) {
                emptyLandmarks[i][j]=0.0f;
            }
        }
        this.landmarkQueue = new CircularQueue<>(classifier.TIME_STEP, emptyLandmarks);
        this.landmarkArray = new NDArray<>(new int[]{classifier.BATCH_SIZE, classifier.TIME_STEP, landmark_shape[0], landmark_shape[1]});
    }


    boolean isFirstResult = true;
    NDArray<Float> smoothedResult;

    private final NDArray<Float> landmarkArray;
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onResult(List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks){
        if (multiHandLandmarks.isEmpty()){
            landmarkQueue.add(emptyLandmarks);
            isFirstResult = true;
        }else {
            for (LandmarkProto.NormalizedLandmarkList landmarkList : multiHandLandmarks) {
                Float[][] landmarks = new Float[landmark_shape[0]][landmark_shape[1]];
                int i = 0;
                for (LandmarkProto.NormalizedLandmark landmark : landmarkList.getLandmarkList()) {
                    landmarks[i][0] = landmark.getX();
                    landmarks[i][1] = landmark.getY();
                    i++;
                }

                if(!isFirstResult){
                    Float[][] prevLandmarks = landmarkQueue.get(-1);

                    NDArray<Float> prevLandmarksNDArray = NDArray.array(prevLandmarks);
                    NDArray<Float> landmarksNDArray = NDArray.array(landmarks);
                    NDArray<Double> diff = NDArray.sub(prevLandmarksNDArray.astype(Float::doubleValue), landmarksNDArray.astype(Float::doubleValue));
                    double norm = Math.sqrt(NDArray.sum(NDArray.mul(diff, diff)));
                    double alpha = Math.exp(-norm * norm * 1);
//                    int j = 0;
//                    for (LandmarkProto.NormalizedLandmark landmark : landmarkList.getLandmarkList()) {
//                        landmarks[j][0] = (float)((1-alpha) * landmarks[j][0] + alpha * prevLandmarksNDArray.get(j).getValue(0));
//                        landmarks[j][1] = (float)((1-alpha) * landmarks[j][1] + alpha * prevLandmarksNDArray.get(j).getValue(1));
//                        j++;
//                    }
                    Log.d(TAG, "alpha: "+Math.exp(-norm*norm));
                }
                isFirstResult = false;

                landmarkQueue.add(landmarks);
                break; // only 1 hand
            }
        }
        for(int i=0;i<landmarkQueue.maxLength;i++){
            NDArray<Float> landmarks = NDArray.array(landmarkQueue.get(i));
            generate(landmarks, landmarks);
            landmarkArray.get(0).get(i).set(landmarks);
        }
//        Log.d(TAG, String.valueOf(landmarkArray));

        classifier.predict(landmarkArray);
        Log.d(TAG, classes[classifier.getPredictedClass()]);

        ((ControlService)context).onResult(classes[classifier.getPredictedClass()], NDArray.array(landmarkQueue.get(-1)));
    }

    private void generate(NDArray<Float> landmarks_f, NDArray<Float> output){
        NDArray<Double> landmarks = landmarks_f.astype(Float::doubleValue);
        NDArray<Double> min = NDArray.amin(landmarks, 0);
        NDArray<Double> max = NDArray.amax(landmarks, 0);
//        Log.d(TAG, min+", " + max);

        double ratio = NDArray.amax(NDArray.sub(max, min));
        if(ratio > 0.0) {
            landmarks = NDArray.div(NDArray.sub(landmarks, min), NDArray.array(new Double[]{ratio}));
        }

        output.set(landmarks.astype(Double::floatValue));
//        Log.d(TAG, "landmarks: " + landmarks);
    }

    private static class CircularQueue<T> {
        private final Object[] array;
        private int index;
        public final int maxLength;
        CircularQueue(int maxLength, T defaultValue){
            this.maxLength = maxLength;
            array = new Object[maxLength];
            for (int i = 0; i < maxLength; i++) {
                array[i] = defaultValue;
            }
            index = 0;
        }

        public void add(T item){
            array[index] = item;
            index = (index + 1) % array.length;
        }

        private int getIndex(int i){
            return (index + i) % array.length;
        }

        @SuppressWarnings("unchecked")
        public T get(int i){
            if(i<0){
                i=i+maxLength;
            }
            return (T) array[getIndex(i)];
        }
    }
}
