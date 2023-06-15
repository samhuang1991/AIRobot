package com.apps.airobot;

import android.app.Application;
import android.content.Context;

import com.iflytek.cloud.Setting;
import com.iflytek.cloud.SpeechUtility;


public class MyApplication extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        LogUtil.init();
        SpeechUtility.createUtility(MyApplication.this, "appid=5f4c99f4");
        Setting.setShowLog(true);
    }

    public static Context getContext() {
        return context;
    }

}
