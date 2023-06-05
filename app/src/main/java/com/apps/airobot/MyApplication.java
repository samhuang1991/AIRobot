package com.apps.airobot;

import android.app.Application;
import android.content.Context;


public class MyApplication extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        LogUtil.init();

    }

    public static Context getContext() {
        return context;
    }

}
