package com.aidlab.gesturecontrol2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE;
import static android.hardware.camera2.CaptureRequest.JPEG_ORIENTATION;

public class CameraHandler {
    // helper class that allows adding multiple output surface, that's all

    private static final String TAG = "CameraHandler";
    private final Context context;
    private final CameraManager manager;
    private final List<Surface> targets = new ArrayList<>();
    private HandlerThread handlerThread;
    private Handler handler;
    private CameraCaptureSession captureSession;
    private final CameraDevice.StateCallback callback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            Log.d(TAG, "CameraDevice.onOpened");

            try {
                createSession(camera);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.d(TAG, "CameraDevice.onDisconnected");

            camera.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.d(TAG, "CameraDevice.onError: " + error);

            camera.close();
        }
    };

    public CameraHandler(Context context) {
        this.context = context;
        this.manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

//        Toast.makeText(context, String.valueOf(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_EXTERNAL)), Toast.LENGTH_LONG).show();
//        manager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
//                                                 @Override
//                                                 public void onCameraAvailable(@NonNull String cameraId) {
//                                                     super.onCameraAvailable(cameraId);
//                                                     Toast.makeText(context, cameraId, Toast.LENGTH_LONG).show();
//                                                 }
//                                             }
//        , null);
        Log.d(TAG, "Camera size: " + getSize());
    }

    public String defaultId() {
        try {
//            Log.d(TAG, Arrays.toString(manager.getCameraIdList()));
//            Toast.makeText(context, Arrays.toString(manager.getCameraIdList()), Toast.LENGTH_LONG).show();
//            int id = manager.getCameraIdList().length > 2? 3:0;
//            if(id == 0){
//                return "/dev/video2";
//            }
            return manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void open() {
        open(defaultId());
    }

    public Size getSize() {
        return getSize(defaultId());
    }

    public Size getSize(String cameraId) {
        CameraCharacteristics characteristics;
        try {
            characteristics = manager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return null;
        }
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        assert map != null;
        Log.d(TAG, Arrays.toString(map.getOutputSizes(SurfaceTexture.class)));
        return map.getOutputSizes(SurfaceTexture.class)[0];
    }

    public boolean isOpened() {
        return handler != null;
    }

    public void open(String cameraId) {
        if (isOpened()) {
            throw new RuntimeException("Camera thread already started");
        }

        try {
            Log.d(TAG, "CAMERA: "+ Arrays.toString(manager.getCameraIdList()));
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        handlerThread = new HandlerThread("CameraThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        try {
            manager.openCamera(cameraId, callback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (!isOpened()) {
            return;
        }

        if (captureSession != null) {
            captureSession.close();
        }

        handlerThread.quitSafely();
        try {
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        handlerThread = null;
        handler = null;

        for(Surface target : targets){
            target.release();
        }
        targets.clear();
    }

    public void addOutput(Surface target) {
        targets.add(target);
    }

    private void createSession(CameraDevice camera) throws CameraAccessException {
        CaptureRequest.Builder captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        captureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequest.set(CONTROL_AE_TARGET_FPS_RANGE,new Range<>(10, 15));

        for (Surface target : targets) {
            captureRequest.addTarget(target);
        }

        camera.createCaptureSession(targets, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.d(TAG, "CameraCaptureSession.onConfigured");

                if (handler == null) {
                    session.close();
                    return;
                }
                captureSession = session;
                try {
                    session.setRepeatingRequest(captureRequest.build(), null, handler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClosed(@NonNull CameraCaptureSession session) {
                Log.d(TAG, "CameraCaptureSession.onClosed");

                super.onClosed(session);
                captureSession = null;
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Log.d(TAG, "CameraCaptureSession.onConfigureFailed");

                Toast.makeText(context, "Configuration change", Toast.LENGTH_SHORT).show();
            }
        }, handler);
    }
}
