package com.apps.airobot.ui.activity;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.apps.airobot.R;

public class MiniChatActivity extends AppCompatActivity {
    private View rightView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minichat);
        rightView = findViewById(R.id.rightView);

        showRightView();

    }

    private void showRightView() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        rightView.setTranslationX(screenWidth);
        ObjectAnimator animator = ObjectAnimator.ofFloat(rightView, "translationX", 0);
        animator.setDuration(1000); // 设置动画时长，单位为毫秒
        animator.start();
    }
}
