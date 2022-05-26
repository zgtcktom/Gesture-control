package com.aidlab.gesturecontrol2;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

//import com.serenegiant.common.BaseActivity;
//
//import com.serenegiant.usb.USBMonitor;
//import com.serenegiant.usb.UVCCamera;

public class MainActivity  extends AppCompatActivity{

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final boolean DEBUG = true;
    private Intent service;
    private Context context;

//    private final Object mSync = new Object();
//    // for accessing USB and USB camera
//    private USBMonitor mUSBMonitor;
//    private UVCCamera mUVCCamera;
//    private SurfaceView mUVCCameraView;
//    // for open&start / stop&close camera preview
//    private ImageButton mCameraButton;
//    private Surface mPreviewSurface;
//    private boolean isActive, isPreview;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        Log.d(TAG, String.valueOf(Settings.canDrawOverlays(this)));

        service = new Intent(this, ControlService.class);
        service.setFlags(FLAG_ACTIVITY_NEW_TASK);
//        startService(service);
        ContextCompat.startForegroundService(this, service);

        context = this;
//        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
    }

//    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
//        @Override
//        public void onAttach(final UsbDevice device) {
//            if (DEBUG) Log.v(TAG, "onAttach:");
//            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
//        }
//
//        @Override
//        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
//            if (DEBUG) Log.v(TAG, "onConnect:");
//            synchronized (mSync) {
//                if (mUVCCamera != null) {
//                    mUVCCamera.destroy();
//                }
//                isActive = isPreview = false;
//            }
//            queueEvent((Runnable) () -> {
//                synchronized (mSync) {
//                    final UVCCamera camera = new UVCCamera();
//                    camera.open(ctrlBlock);
//                    if (DEBUG) Log.i(TAG, "supportedSize:" + camera.getSupportedSize());
//                    try {
//                        camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
//                    } catch (final IllegalArgumentException e) {
//                        try {
//                            // fallback to YUV mode
//                            camera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.DEFAULT_PREVIEW_MODE);
//                        } catch (final IllegalArgumentException e1) {
//                            camera.destroy();
//                            return;
//                        }
//                    }
//                    mPreviewSurface = mUVCCameraView.getHolder().getSurface();
//                    if (mPreviewSurface != null) {
//                        isActive = true;
//                        camera.setPreviewDisplay(mPreviewSurface);
//                        camera.startPreview();
//                        isPreview = true;
//                    }
//                    synchronized (mSync) {
//                        mUVCCamera = camera;
//                    }
//                }
//            }, 0);
//        }
//
//        @Override
//        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
//            if (DEBUG) Log.v(TAG, "onDisconnect:");
//            // XXX you should check whether the comming device equal to camera device that currently using
//            queueEvent(new Runnable() {
//                @Override
//                public void run() {
//                    synchronized (mSync) {
//                        if (mUVCCamera != null) {
//                            mUVCCamera.close();
//                            if (mPreviewSurface != null) {
//                                mPreviewSurface.release();
//                                mPreviewSurface = null;
//                            }
//                            isActive = isPreview = false;
//                        }
//                    }
//                }
//            }, 0);
//        }
//
//        @Override
//        public void onDettach(final UsbDevice device) {
//            if (DEBUG) Log.v(TAG, "onDettach:");
//            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
//        }
//
//        @Override
//        public void onCancel(final UsbDevice device) {
//        }
//    };

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "destroy");
        super.onDestroy();
    }

    @Override
    public void onBackPressed(){
        stopService(service);
        super.onBackPressed();
    }
}