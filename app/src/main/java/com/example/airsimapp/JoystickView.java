package com.example.airsimapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class JoystickView extends View {

    private Bitmap baseBitmap;
    private Bitmap handleBitmap;

    private float centerX, centerY;
    private float baseRadius;
    private float handleX, handleY;

    private JoystickListener listener;


    public JoystickView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private Paint basePaint;
    private Paint handlePaint;

    private void init() {
        basePaint = new Paint();
        basePaint.setColor(Color.GRAY);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setAlpha(150); // transparency (0 = fully transparent, 255 = fully opaque)

        handlePaint = new Paint();
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setAlpha(255); // fully opaque
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f * 0.8f;
        resetHandle();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        // Draw base circle (grey, semi-transparent)
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);

        // Draw handle circle (white)
        canvas.drawCircle(handleX, handleY, baseRadius / 2, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        double distance = Math.hypot(dx, dy);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (distance < baseRadius) {
                    handleX = event.getX();
                    handleY = event.getY();
                } else {
                    // Clamp handle to edge of base circle
                    double ratio = baseRadius / distance;
                    handleX = (float) (centerX + dx * ratio);
                    handleY = (float) (centerY + dy * ratio);
                }
                invalidate();

                double angle = Math.toDegrees(Math.atan2(dy, dx));
                double strength = (distance / baseRadius) * 100;
                if (listener != null) {
                    listener.onMove(angle, strength);
                }
                break;

            case MotionEvent.ACTION_UP:
                resetHandle();
                invalidate();
                if (listener != null) {
                    listener.onMove(0, 0);
                }
                break;
        }
        return true;
    }

    private void resetHandle() {
        handleX = centerX;
        handleY = centerY;
    }

    public void setJoystickListener(JoystickListener listener) {
        this.listener = listener;
    }

    public interface JoystickListener {
        void onMove(double angle, double strength);
    }

}
