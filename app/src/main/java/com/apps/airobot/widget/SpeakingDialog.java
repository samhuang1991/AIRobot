package com.apps.airobot.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;

import com.apps.airobot.R;

public class SpeakingDialog extends Dialog {

    private WaveView mWaveView;

    public SpeakingDialog(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();

        // 设置背景透明
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // 设置对话框内容视图
        setContentView(R.layout.dialog_speaking);

        // 设置对话框宽度和位置
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);

        mWaveView = findViewById(R.id.waveView);
    }

    public WaveView getmWaveView() {
        return mWaveView;
    }

    @Override
    public void show() {
        super.show();
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

}
