package com.apps.airobot;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import static com.apps.airobot.MyApplication.getContext;


/**
 * 吴小辉
 * <p>
 * log输出
 */
public final class LogUtil {

    private static String tag = "mylog-airobot";
    private static String className;//类名
    private static String methodName;//方法名
    private static int lineNumber;//行数
    private static String versionName;//版本名

    //输出log日志
    public static void init() {
        versionName = getVersionName(getContext());
        tag = tag + " " + versionName + " ";
    }

    //输出log日志
    public static void i() {
        StackTraceElement[] sElements = new Throwable().getStackTrace();
        className = sElements[1].getFileName();
        methodName = sElements[1].getMethodName();
        lineNumber = sElements[1].getLineNumber();
        if (!isOutputLog(getContext())) {
            return;
        }
        Log.i(tag, "--->" + methodName + "(" + className + ":" + lineNumber + ")----->");
    }

    //输出log日志
    public static void i(Object print) {
        StackTraceElement[] sElements = new Throwable().getStackTrace();
        className = sElements[1].getFileName();
        methodName = sElements[1].getMethodName();
        lineNumber = sElements[1].getLineNumber();
        if (!isOutputLog(getContext())) {
            return;
        }
        Log.i(tag, "--->" + methodName + "(" + className + ":" + lineNumber + ")--->" + print);
    }

    //输出log日志
    public static void d(Object print) {
        StackTraceElement[] sElements = new Throwable().getStackTrace();
        className = sElements[1].getFileName();
        methodName = sElements[1].getMethodName();
        lineNumber = sElements[1].getLineNumber();
        if (!isOutputLog(getContext())) {
            return;
        }
        Log.d(tag, "--->" + methodName + "(" + className + ":" + lineNumber + ")--->" + print);
    }

    //输出log日志
    public static void e(Object print) {
        StackTraceElement[] sElements = new Throwable().getStackTrace();
        className = sElements[1].getFileName();
        methodName = sElements[1].getMethodName();
        lineNumber = sElements[1].getLineNumber();
        if (!isOutputLog(getContext())) {
            return;
        }
        Log.e(tag, "--->" + methodName + "(" + className + ":" + lineNumber + ")---xxxxxxxxxx>" + print);
    }

    //把字符串转中文输出log.e日志
    public static void a(String string) {
        String newString = toChineseString(string);
        int num = (newString.length() % 3000 == 0) ? newString.length() / 3000 : (newString.length() / 3000 + 1);
        for (int i = 0; i < num; i++) {
            int idexEnd = newString.length() < 3000 * (i + 1) ? newString.length() : 3000 * (i + 1);
            int idexStart = 3000 * i;
            if (!isOutputLog(getContext())) {
                return;
            }
            StackTraceElement[] sElements = new Throwable().getStackTrace();
            className = sElements[1].getFileName();
            methodName = sElements[1].getMethodName();
            lineNumber = sElements[1].getLineNumber();
            Log.e(tag, methodName + "(" + className + ":" + lineNumber + ")" + newString.substring(idexStart, idexEnd));
        }
    }

    private static String toChineseString(String string) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == '\\') {
                if ((i < string.length() - 5) && ((string.charAt(i + 1) == 'u') || (string.charAt(i + 1) == 'U'))) {
                    try {
                        stringBuilder.append((char) Integer.parseInt(string.substring(i + 2, i + 6), 16));
                        i += 5;
                    } catch (NumberFormatException localNumberFormatException) {
                        stringBuilder.append(string.charAt(i));
                    }
                } else {
                    stringBuilder.append(string.charAt(i));
                }
            } else {
                stringBuilder.append(string.charAt(i));
            }
        }
        return stringBuilder.toString();
    }

    //判断当前应用是否是debug状态
    public static boolean isApkInDebug(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    //是否输出日志
    public static boolean isOutputLog(Context context) {
        return true;
        //        try {
        //            return !BuildConfig.environment.equals("prod") || isApkInDebug(context);
        //        } catch (Exception e) {
        //            return false;
        //        }
    }

    /**
     * [获取应用程序版本名称信息]
     *
     * @param context
     * @return 当前应用的版本名称
     */
    public static synchronized String getVersionName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            Log.e(tag, e.getMessage());
        }
        return "";
    }


}