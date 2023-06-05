package com.apps.airobot.ui.widget;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.Random;

public class WaveView extends View {

    private Paint mPaint;
    private Path mPath;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mOffset;
    private LinearGradient mLinearGradient;
    private Visualizer mVisualizer;
    private int mAmplitude;
    private Paint mBlurPaint;
    private Random mRandom = new Random();


    public WaveView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5f);
        mPaint.setShadowLayer(14, 0, 14, 0x66000000); // 设置阴影效果

        mPath = new Path();
        mOffset = 0;
        mAmplitude = 50;


        mBlurPaint = new Paint();
        mBlurPaint.setAntiAlias(true);
        mBlurPaint.setStyle(Paint.Style.STROKE);
        mBlurPaint.setStrokeWidth(5f);
        mBlurPaint.setMaskFilter(new BlurMaskFilter(44, BlurMaskFilter.Blur.OUTER));

    }

    public void setAudioSessionId(int sessionId) {
        if (mVisualizer != null) {
            mVisualizer.release();
        }

        mVisualizer = new Visualizer(sessionId);
        mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override
            public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
                mAmplitude = ((waveform[0] & 0xFF) - 128) * 2;
                mHandler.sendEmptyMessage(0);
            }

            @Override
            public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            }
        }, Visualizer.getMaxCaptureRate() / 2, true, false);
        mVisualizer.setEnabled(true);
    }

    /**
     * SpeechRecognizer类的onRmsChanged回调方法用于通知应用程序音频输入的音量变化。"RMS"代表"Root Mean Square"，是一种测量音频信号振幅的方式
     */
    public void setRmsdB(float rmsdB){
        mAmplitude = (int) Math.abs(rmsdB*5);
        mHandler.sendEmptyMessage(0);
    }

    public void releaseVisualizer() {
        if (mVisualizer != null) {
            mVisualizer.release();
            mVisualizer = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mScreenWidth = w;
        mScreenHeight = h;
        mLinearGradient = new LinearGradient(0, 0, mScreenWidth, 0,
                new int[]{0xFFE91E63, 0xFFF06292, 0xFFE91E63},
                new float[]{0, 0.5f, 1},
                Shader.TileMode.CLAMP);
        mPaint.setShader(mLinearGradient);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Log.e(">>>>>","mAmplitude = " + mAmplitude);

        if (mAmplitude != 0){
//            float hue = (mOffset % 360 + 360) % 360; // 将mOffset映射到0-360的范围内
//            int backgroundColor = Color.HSVToColor(128, new float[]{hue, 0.4f, 0.5f}); // 128是透明度，取值范围0-255
//            canvas.drawColor(backgroundColor);
        }else {
            return;
        }

        int segmentCount = 6;
        float hueRange = 180f;

        int particleX = mRandom.nextInt(mScreenWidth-50+1) + 50;;
        int particleY= 0;

        for (int i = 0; i < 3; i++) {
            for (int seg = 0; seg < segmentCount; seg++) {
                mPath.reset();
                int startX = mScreenWidth * seg / segmentCount;
                int endX = mScreenWidth * (seg + 1) / segmentCount;
                float startY = (float) (mScreenHeight / 2 + mAmplitude * Math.sin(4 * Math.PI * startX / mScreenWidth + mOffset * Math.PI / (90 * (i + 1))));
                mPath.moveTo(startX, startY);

                for (int x = startX; x <= endX; x++) {
                    float y = (float) (mScreenHeight / 2 + mAmplitude * Math.sin(4 * Math.PI * x / mScreenWidth + mOffset * Math.PI / (90 * (i + 1))));
                    mPath.lineTo(x, y);
                }

                float hueOffset = (mOffset % 360 + 360) % 360;
                float startHue = ((hueRange * startX) / mScreenWidth + hueOffset) % 360;
                float endHue = ((hueRange * endX) / mScreenWidth + hueOffset) % 360;

                Log.e(">>>>>","hueOffset = "+hueOffset + "   startHue = "+startHue + "   endHue = "+endHue + "   mOffset = "+mOffset);

                mLinearGradient = new LinearGradient(
                        startX, 0, endX, 0,
                        Color.HSVToColor(new float[]{startHue, 1f, 1f}),
                        Color.HSVToColor(new float[]{endHue, 1f, 1f}),
                        Shader.TileMode.CLAMP
                );
                mPaint.setShader(mLinearGradient);

                // 将模糊效果的颜色设置为波形颜色
                mBlurPaint.setColor(Color.HSVToColor(new float[]{startHue, 1f, 1f}));
                mBlurPaint.setAlpha(128); // 设置透明度，可根据需要调整

                // 绘制模糊效果
                canvas.drawPath(mPath, mBlurPaint);

                canvas.drawPath(mPath, mPaint);
            }
            mOffset -= 15;
        }

        //发射粒子的条件
        if (mOnDrawListener != null && mAmplitude >200 && mAmplitude != 0) {
            particleY =  (int) (mScreenHeight / 2 + mAmplitude * Math.sin(4 * Math.PI * particleX / mScreenWidth + mOffset * Math.PI / 90 ));
            mOnDrawListener.onDraw(particleX, particleY);
            Log.e(">>>>>>>","particleX = "+particleX +"  particleY = "+particleY);
        }

        mHandler.sendEmptyMessageDelayed(0, 50);
    }


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            invalidate();
        }
    };


    public interface OnDrawListener {
        void onDraw(float x, float y);
    }

    private OnDrawListener mOnDrawListener;

    public void setOnDrawListener(OnDrawListener listener) {
        mOnDrawListener = listener;
    }



}