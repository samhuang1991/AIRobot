package com.apps.airobot.ui.activity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.apps.airobot.R;
import com.apps.airobot.mApi;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mApi.showMsg(this, "开始载入本地配置");
        mApi.setFullscreen(this);
        new Handler().postDelayed(()->{
            startActivity(new Intent(this, ChatActivity.class));
        }, 200);
    }

}