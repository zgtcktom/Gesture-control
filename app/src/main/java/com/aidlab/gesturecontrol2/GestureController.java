package com.aidlab.gesturecontrol2;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.AccessibilityService.GestureResultCallback;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class GestureController {
    private static final String TAG = "GestureController";

    // (x, y) in screen coordinates
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static GestureDescription createClick(float x, float y) {
        // for a single tap a duration of 1 ms is enough
        final int DURATION = 10;

        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.StrokeDescription clickStroke =
                new GestureDescription.StrokeDescription(clickPath, 0, DURATION);
        GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
        clickBuilder.addStroke(clickStroke);
        return clickBuilder.build();
    }

    private enum TouchState {
        START, END, MOVE
    }

    TouchState state = TouchState.END;

    static int DEFAULT_DURATION = 10;
    int prevX, prevY;
    int currentX, currentY;
    @RequiresApi(api = Build.VERSION_CODES.O)
    GestureDescription touchDownEvent(int x, int y, StrokeDescription prev){
        Path path = new Path();
        path.moveTo(x, y);
        prevX=x;
        prevY=y;
        StrokeDescription stroke;
        if(prev==null) {
            stroke = new StrokeDescription(path, 0, DEFAULT_DURATION, false);
        }else{
            stroke = prev.continueStroke(path, 0, DEFAULT_DURATION, false);
        }
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(stroke);
        return gesture.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    GestureDescription touchMoveEvent(int x, int y, StrokeDescription prev){
        Path path = new Path();
        path.moveTo(prevX, prevY);
        path.lineTo(x, y);
        prevX=x;
        prevY=y;
        StrokeDescription stroke;
        if(prev==null) {
            stroke = new StrokeDescription(path, 0, DEFAULT_DURATION, true);
        }else{
            stroke = prev.continueStroke(path, 0, DEFAULT_DURATION, true);
        }
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(stroke);
        return gesture.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    GestureDescription touchEndEvent(StrokeDescription prev){
        Path path = new Path();
        path.moveTo(prevX, prevY);
        StrokeDescription stroke = prev.continueStroke(path, 0, DEFAULT_DURATION, false);
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(stroke);
        return gesture.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void touchStart(int x, int y){
        Log.d(TAG, "touchStart");
        if(state != TouchState.END){
            return;
        }
        state = TouchState.START;
        currentX=x;
        currentY=y;
        boolean result = service.dispatchGesture(touchDownEvent(x, y, null), touchStartCallback, handler);
        Log.d(TAG, "touchDownEvent: "+result);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void touchMove(int x, int y){
        Log.d(TAG, "touchMove");
//        if(state!=TouchState.START){
//            return;
//        }
        state = TouchState.MOVE;
        currentX=x;
        currentY=y;
        if(prevStroke==null) {
            boolean result = service.dispatchGesture(touchMoveEvent(x, y, prevStroke), touchStartCallback, handler);
            Log.d(TAG, "touchMoveEvent: "+result);
        }
    }


    private StrokeDescription prevStroke;
    @RequiresApi(api = Build.VERSION_CODES.O)
    void touchEnd(){
        state = TouchState.END;
        Log.d(TAG, "touchEnd");
        prevStroke=null;
    }


//    Path dragPath;
    boolean isDragStart = false;
    @RequiresApi(api = Build.VERSION_CODES.O)
    void dragStart(int x, int y){
        prevDragX=x;
        prevDragY=y;
        isDragStart = true;
        isDragEnd=false;
        savedStroke=null;
    }

    int dragX, dragY;
    int prevDragX, prevDragY;
    StrokeDescription savedStroke;
    @RequiresApi(api = Build.VERSION_CODES.O)
    void dragMove(int x, int y){
        dragX=x;
        dragY=y;
        Log.d(TAG, "dragMove: "+isDragStart);
        if(isDragStart) {
            if(savedStroke==null){

                boolean result = service.dispatchGesture(dragStartEvent(prevDragX, prevDragY, dragX, dragY, savedStroke), dragCallback, handler);
                Log.d(TAG, "dragStart0Event: " + result);
                isDragStart=false;
            }else if(prevDragX != dragX || prevDragY != dragY) {
                boolean result = service.dispatchGesture(dragStartEvent(prevDragX, prevDragY, dragX, dragY, savedStroke), dragCallback, handler);
                Log.d(TAG, "dragStart0Event: " + result);
                isDragStart=false;
            }else{
                Log.d(TAG, "dragStart0Event: " + "holding");
            }
//            Log.d(TAG, "coords: "+prevDragX+", "+prevDragY+", "+dragX+", "+dragY);
            prevDragX=dragX;
            prevDragY=dragY;
        }
    }

    boolean isDragEnd;
    @RequiresApi(api = Build.VERSION_CODES.O)
    void dragEnd(int x, int y){
        isDragEnd=true;
        dragX=x;
        dragY=y;
        if(savedStroke!=null) {
            boolean result = service.dispatchGesture(dragStartEvent(prevDragX, prevDragY, dragX, dragY, savedStroke), dragCallback, handler);
            Log.d(TAG, "dragEnd: " + result);
        }
    }

    GestureResultCallback dragCallback = new AccessibilityService.GestureResultCallback(){
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);
            StrokeDescription prevStroke = gestureDescription.getStroke(0);
//            dragY+=10;
            if(isDragEnd) {
                savedStroke=null;
                if(prevStroke.willContinue()) {
                    boolean result = service.dispatchGesture(dragStartEvent(prevDragX, prevDragY, dragX, dragY, prevStroke), dragCallback, handler);
                    Log.d(TAG, "dragEndEvent: " + result);
                }
            }else if(prevDragX==dragX && prevDragY==dragY){
                isDragStart=true;
                savedStroke = prevStroke;
                Log.d(TAG, "dragMoveEvent: " + "holding");
            }else {
                savedStroke=null;
                boolean result = service.dispatchGesture(dragStartEvent(prevDragX, prevDragY, dragX, dragY, prevStroke), dragCallback, handler);
                Log.d(TAG, "dragMoveEvent: " + result);
            }
            prevDragX=dragX;
            prevDragY=dragY;
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
            Log.d(TAG, "onCancelled");
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    GestureDescription dragStartEvent(int x0, int y0, int x1, int y1, StrokeDescription prevStroke){
        Path path = new Path();
        path.moveTo(x0, y0);
        if(x0!=x1 || y0!=y1) {
            path.lineTo(x1, y1);
        }
        StrokeDescription stroke;
        if(prevStroke!=null){
            stroke=prevStroke.continueStroke(path,0,DEFAULT_DURATION, !isDragEnd);
        }else {
            stroke = new StrokeDescription(path, 0, DEFAULT_DURATION, !isDragEnd);
        }
        GestureDescription.Builder gesture = new GestureDescription.Builder();
        gesture.addStroke(stroke);
        return gesture.build();
    }



    private HandlerThread handlerThread;
    private Handler handler;
    GestureResultCallback touchStartCallback = new AccessibilityService.GestureResultCallback(){
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            super.onCompleted(gestureDescription);

            StrokeDescription prevStroke = gestureDescription.getStroke(0);
            if(!prevStroke.willContinue()){
                prevStroke=null;
            }
            if(state == TouchState.START){
                boolean result = service.dispatchGesture(touchMoveEvent(currentX, currentY, prevStroke), touchStartCallback, handler);
                Log.d(TAG, "touchMoveEvent: "+result);
            }else if(state==TouchState.MOVE){
                boolean result = service.dispatchGesture(touchMoveEvent(currentX, currentY, prevStroke), touchStartCallback, handler);
                Log.d(TAG, "touchMoveEvent: "+result+", "+prevStroke);
            }else if(state==TouchState.END){
                // end
                boolean result = service.dispatchGesture(touchEndEvent(prevStroke), null, null);
                Log.d(TAG, "touchEndEvent: "+result);
                prevStroke=null;
            }
            GestureController.this.prevStroke = prevStroke;
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            super.onCancelled(gestureDescription);
        }
    };

    AccessibilityService service;

// callback invoked either when the gesture has been completed or cancelled
    AccessibilityService.GestureResultCallback callback;
    @RequiresApi(api = Build.VERSION_CODES.N)
    GestureController(AccessibilityService service) {
        this.service = service;
        callback = new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "gesture completed");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d(TAG, "gesture cancelled");
            }
        };

        handlerThread = new HandlerThread("ControllerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }



    void click(int x, int y){
        boolean result = service.dispatchGesture(createClick(x, y), callback, null);
        Log.d(TAG, "Click" + result);
    }

    void close(){

        handlerThread.quitSafely();
        try {
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        handlerThread = null;
        handler = null;

        Log.d(TAG, "closed");
    }



    private AudioManager am;
    void dispatch(int key) {
        if(am == null){
            am = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
        }

//        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, key);
//        am.dispatchMediaKeyEvent(downEvent);

//        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, key);
//        am.dispatchMediaKeyEvent(upEvent);
//
        am.adjustVolume(key, AudioManager.FLAG_SHOW_UI);
//        new Thread(() -> {
//            Instrumentation inst = new Instrumentation();
//            //This is for Volume Down, change to
//            //KEYCODE_VOLUME_UP for Volume Up.
//            inst.sendKeyDownUpSync(key);
//        }).start();
    }


    void dispatch2(int key) {
        if(am == null){
            am = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
        }
        new Thread(() -> {
            Instrumentation inst = new Instrumentation();
            inst.sendKeyDownUpSync(key);
        }).start();
    }


    void skipForward() {
        Log.d(TAG, "skipForward");
//        dispatch2(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD);
//
//        Intent i = new Intent("com.spotify.mobile.android.ui.widget.NEXT");
//        i.setPackage("com.spotify.music");
//        service.sendBroadcast(i);



        if(am == null){
            am = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
        }

        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT);
        am.dispatchMediaKeyEvent(downEvent);

        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT);
        am.dispatchMediaKeyEvent(upEvent);
    }

    void skipBackward() {
        Log.d(TAG, "skipBackward");
//        dispatch2(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD);

//        Intent i = new Intent("com.spotify.mobile.android.ui.widget.PREVIOUS");
//        i.setPackage("com.spotify.music");
//        service.sendBroadcast(i);


        if(am == null){
            am = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
        }

        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        am.dispatchMediaKeyEvent(downEvent);

        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        am.dispatchMediaKeyEvent(upEvent);
    }


    void playPause() {
//        Intent i = new Intent("com.spotify.mobile.android.ui.widget.NEXT");
//        i.setPackage("com.spotify.music");
//        service.sendBroadcast(i);

        if(am == null){
            am = (AudioManager) service.getSystemService(Service.AUDIO_SERVICE);
        }

        KeyEvent downEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        am.dispatchMediaKeyEvent(downEvent);

        KeyEvent upEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        am.dispatchMediaKeyEvent(upEvent);

//        Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON);
//        i.setComponent(new ComponentName("com.spotify.music", "com.spotify.music.internal.receiver.MediaButtonReceiver"));
//        i.putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
//        service.sendOrderedBroadcast(i, null);
//
//        i = new Intent(Intent.ACTION_MEDIA_BUTTON);
//        i.setComponent(new ComponentName("com.spotify.music", "com.spotify.music.internal.receiver.MediaButtonReceiver"));
//        i.putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
//        service.sendOrderedBroadcast(i, null);
    }

    // accessibilityService: contains a reference to an accessibility service
// callback: can be null if you don't care about gesture termination

}
