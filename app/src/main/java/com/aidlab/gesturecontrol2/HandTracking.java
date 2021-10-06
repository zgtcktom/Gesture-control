package com.aidlab.gesturecontrol2;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;


import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.solutioncore.ResultListener;
import com.google.mediapipe.solutions.hands.Hands;
import com.google.mediapipe.solutions.hands.HandsOptions;
import com.google.mediapipe.solutions.hands.HandsResult;

import java.util.List;

public class HandTracking {
    // Modified mediapipe hand tracking pipeline, allow running in service

    private static final String TAG = "HandTracking";

    private final Hands hands;
    public ExternalTextureConverter converter;

    public HandTracking(Context context, ResultListener<HandsResult> resultListener) {
        HandsOptions handsOptions =
                HandsOptions.builder()
                        .setMode(HandsOptions.STREAMING_MODE)  // API soon to become
                        .setMaxNumHands(1)                     // setStaticImageMode(false)
                        .setMinDetectionConfidence(0.5f)
                        .setRunOnGpu(true).build();
        hands = new Hands(context, handsOptions);
        hands.setErrorListener(
                (message, e) -> Log.e(TAG, "MediaPipe Hands error:" + message));

        hands.setResultListener(resultListener);
    }

    public SurfaceTexture start(int width, int height) {
        converter = new ExternalTextureConverter(hands.getGlContext(), 2);
//        converter.setDestinationSize(width, height);
        converter.setDestinationSize(width, height);
        converter.setConsumer(hands::send);

        return converter.getSurfaceTexture();
    }

    public void stop() {
        if (converter != null) {
            converter.close();
        }
    }

    public String getMultiHandLandmarksDebugString(List<NormalizedLandmarkList> multiHandLandmarks) {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks";
        }
        StringBuilder multiHandLandmarksStr = new StringBuilder("Number of hands detected: " + multiHandLandmarks.size() + "\n");
        int handIndex = 0;
        for (NormalizedLandmarkList landmarks : multiHandLandmarks) {
            multiHandLandmarksStr.append("\t#Hand landmarks for hand[").append(handIndex).append("]: ").append(landmarks.getLandmarkCount()).append("\n");
            int landmarkIndex = 0;
            for (NormalizedLandmark landmark : landmarks.getLandmarkList()) {
                multiHandLandmarksStr.append("\t\tLandmark [").append(landmarkIndex).append("]: (").append(landmark.getX()).append(", ").append(landmark.getY()).append(", ").append(landmark.getZ()).append(")\n");
                ++landmarkIndex;
            }
            ++handIndex;
        }
        return multiHandLandmarksStr.toString();
    }
}
