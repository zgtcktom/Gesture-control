package com.aidlab.gesturecontrol2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.Log;
import android.widget.ImageView;

import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.solutions.hands.Hands;

import java.util.List;

public class PreviewRenderer {

    private static final String TAG = "PreviewRenderer";
    private final ImageView imageView;
    int width;
    int height;
    Canvas canvas;
    Bitmap bitmap;

    PreviewRenderer(ImageView imageView, int width, int height) {
        this.imageView = imageView;
        this.width = width;
        this.height = height;
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        imageView.setImageBitmap(bitmap);
        canvas = new Canvas(bitmap);

    }

    public void clear() {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    public void renderAll(List<NormalizedLandmarkList> results) {
        clear();
        if (results.isEmpty()) {
            update();
            return;
        }
        for (NormalizedLandmarkList result : results) {
            render(result.getLandmarkList());
            break;
        }
        update();
    }

    public void render(List<NormalizedLandmark> result) {
        Paint linePaint = new Paint();
        linePaint.setColor(Color.rgb(210,216,227));
        linePaint.setStrokeWidth(10);
        for (Hands.Connection connection : Hands.HAND_CONNECTIONS) {
            NormalizedLandmark start = result.get(connection.start());
            NormalizedLandmark end = result.get(connection.end());
            canvas.drawLine(
                    start.getX() * width,
                    start.getY() * height,
                    end.getX() * width,
                    end.getY() * height,
                    linePaint);
        }

        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.rgb(246,196,35));
        for (NormalizedLandmark landmark : result) {
            Log.d(TAG, String.valueOf(landmark.getX()));
            canvas.drawCircle(
                    landmark.getX() * width,
                    landmark.getY() * height,
                    7*2,
                    landmarkPaint);
        }
    }

    public void drawCircle(int x, int y){
        Paint paint = new Paint();
        paint.setColor(Color.argb(200, 50, 50, 200));
        canvas.drawCircle(
                x,
                y,
                20,
                paint);
    }

    public void update() {
        imageView.postInvalidate();
    }
}
