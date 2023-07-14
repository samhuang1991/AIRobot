package com.apps.airobot.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.apps.airobot.ActivityController;
import com.apps.airobot.ChatItem;
import com.apps.airobot.LogUtil;
import com.apps.airobot.MyApplication;
import com.apps.airobot.ifly.IflyTts;
import com.apps.airobot.mApi;
import com.apps.airobot.socket.WebSocketAdapter;
import com.apps.airobot.ui.widget.SpeakingDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

public abstract class BaseChatActivity extends BaseActivity {

    private static UUID uuid = UUID.randomUUID();
    protected static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    protected static final int LISTENING_TIMEOUT = 1500; // 设置超时时间为5秒

    protected SpeakingDialog speakingDialog;
    protected SpeechRecognizer speechRecognizer;
    protected boolean isBotTalking = false,
            isFetchingSound = false,
            isPartialResult = false;

    private IflyTts mIflyTts;
    WebSocketAdapter webSocketAdapter;
    String serverURL = "wss://ai.dp.qhmoka.com/ai-service/chatgpt/websocket/",
            bot_record = "",
            SEND_END = "[DONE]",
            SEND_STOP = "[STOP]",
            SEND_STOP_SUCCESS = "[STOP-SUCCESS]";

    static final int BOT_BEGIN = 0,
            BOT_CONTINUE = 1,
            USER_MSG = 2,
            BOT_END = 3,
            CLEAR_HISTORY = 4,
            BOT_OK_END = 5;

    int reconnectCount = 0; // 重连次数
    long mBackPressed;

    /**
     * 断线重连继续发送的文本
     */
    String reconnectText = "";

    Handler handler;

    protected abstract void handleCustomMessage(Message msg);

    protected abstract void initRecord();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        speakingDialog = new SpeakingDialog(BaseChatActivity.this);
        speakingDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                LogUtil.i("onDismiss isFetchingSound " + isFetchingSound);
                if (isFetchingSound) {
                    if (speechRecognizer != null) {
                        speechRecognizer.stopListening();
                        speechRecognizer.cancel();
                    }
                    LogUtil.i("onDismiss ");
                }
            }
        });

        webSocketAdapter = WebSocketAdapter.getInstance();
        connectWebSocket();

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                handleCustomMessage(msg);

            }
        };

        initRecord();
    }


    @SuppressLint("CheckResult")
    private void connectWebSocket() {

        webSocketAdapter.connect(serverURL + uuid)
                .subscribe(new Observer<Boolean>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        // 订阅开始时的回调
                    }

                    @Override
                    public void onNext(Boolean connected) {
                        // 连接成功时的回调
                        if (connected) {
                            // WebSocket连接成功
                            // 可以在这里执行一些操作或显示连接成功的提示
                            mApi.showMsg(MyApplication.getContext(), "成功连接至服务器");
                            if (reconnectText != null && reconnectText.length() > 0) {
                                sendMessage(reconnectText);
                                reconnectText = "";
                            }
                        } else {
                            // WebSocket连接失败
                            // 可以在这里执行一些操作或显示连接失败的提示
                            sendHandlerMsg(BOT_BEGIN, null);
                            sendHandlerMsg(BOT_CONTINUE, "Failed to connect to the server");
                            sendHandlerMsg(BOT_END, null);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        // 连接错误时的回调
                        LogUtil.i();
                        sendHandlerMsg(BOT_END, null);
                    }

                    @Override
                    public void onComplete() {
                        // 连接完成时的回调
                        LogUtil.i();
                    }
                });
    }


    void sendHandlerMsg(int what, String msg) {
        Message message = new Message();
        message.what = what;
        if (null == msg) {
            msg = "";
        }
        message.obj = msg;
        handler.sendMessage(message);
    }


    /**
     * 发送消息
     *
     * @param msg
     */
    void sendMessage(String msg) {

        if (msg == null && msg.length() == 0) {
            mApi.showMsg(this, "请先输入文本");
            return;
        }
        reconnectText = msg;
        if (webSocketAdapter.getConnectionState() == WebSocketAdapter.ConnectionState.CONNECTED) {
            if (isBotTalking) {
                mApi.showMsg(this, "请等待 AI 回答结束");
                return;
            }
            webSocketAdapter.send(msg);
            LogUtil.i("发送消息：" + msg);
            sendHandlerMsg(USER_MSG, msg);
            sendHandlerMsg(BOT_BEGIN, null);
        } else {
            if (reconnectCount == 0) {
                mApi.showMsg(this, "重新连接至服务器...");
                connectWebSocket();
            }
        }

    }

    /**
     * 发送消息（隐式发送，发送后不需要处理UI，用于终止聊天等命令的发送）
     *
     * @param msg
     */
    void sendImplicitMessage(String msg) {

        if (msg == null && msg.length() == 0) {
            mApi.showMsg(this, "请先输入文本");
            return;
        }
        reconnectText = msg;
        if (webSocketAdapter.getConnectionState() == WebSocketAdapter.ConnectionState.CONNECTED) {
            webSocketAdapter.send(msg);
            LogUtil.i("发送消息：" + msg);
        } else {
            if (reconnectCount == 0) {
                mApi.showMsg(this, "重新连接至服务器...");
                connectWebSocket();
            }
        }

    }

    @Override
    public void onBackPressed() {
        if (mBackPressed > System.currentTimeMillis() - 2000) {
            ActivityController.getInstance().killAllActivity();
            super.onBackPressed();
        } else {
            mApi.showMsg(this, "连续返回两次退出APP");
            mBackPressed = System.currentTimeMillis();
        }
    }


//    private void initRecord() {
//        // 检查录音权限
//        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, REQUEST_RECORD_AUDIO_PERMISSION);
//        }
//        if (speechRecognizer == null) {
//            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
//        }
//    }


}