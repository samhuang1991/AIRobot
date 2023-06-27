package com.apps.airobot.ui.activity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.VerticalGridView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AsyncPlayer;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONObject;
import com.apps.airobot.ActivityController;
import com.apps.airobot.ChatItem;
import com.apps.airobot.LogUtil;
import com.apps.airobot.MessageListAdapter;
import com.apps.airobot.R;
import com.apps.airobot.bus.RxBus;
import com.apps.airobot.bus.RxSubscriptions;
import com.apps.airobot.ifly.IflyTts;
import com.apps.airobot.mApi;
import com.apps.airobot.socket.WebSocketAdapter;
import com.apps.airobot.ui.dialog.SettingPopupView;
import com.apps.airobot.ui.fragment.PromptFragment;
import com.apps.airobot.ui.widget.SpeakingDialog;
import com.apps.airobot.util.NetStateUtils;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SynthesizerListener;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.UUID;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

public class ChatActivity extends AppCompatActivity implements RecognitionListener, SynthesizerListener {

    static UUID uuid = UUID.randomUUID();
    int selectedItemPosition = -1;
    private static final int LISTENING_TIMEOUT = 1500; // 设置超时时间为5秒
    private static final int SILENCE_THRESHOLD = 1; // 设置音量阈值

    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();
    boolean isBotTalking = false,
            isFetchingSound = false,
            isPartialResult = false;
    AsyncPlayer asyncPlayer;
    MediaPlayer mediaPlayer;
    ArrayList<String> history;
    ChatItem current_bot_chat;

    ChatItem mLastBotChat;
    SpeakingDialog speakingDialog;
    WebSocketAdapter webSocketAdapter;
    private Disposable messageDisposable;

    int reconnectCount = 0; // 重连次数
    String onPartialRecognizedText;
    // 发送定时请求
    Timer timer = new Timer();

    private VerticalGridView verticalGridView;
    private MessageListAdapter messageListAdapter;
    ImageView mBtInput, mBtVoice , mSetting;
    Handler handler;
    long mBackPressed;
    static final int BOT_BEGIN = 0,
            BOT_CONTINUE = 1,
            USER_MSG = 2,
            BOT_END = 3,
            CLEAR_HISTORY = 4;

    String serverURL = "wss://ai.dp.qhmoka.com/ai-service/chatgpt/websocket/",
            bot_record = "",
            SEND_END = "[DONE]",
            SEND_STOP = "[STOP]",
            SEND_STOP_SUCCESS = "[STOP-SUCCESS]";

    /**
     * 是否需要断线重连。默认ture
     */
    boolean isNeedReconnect = true;
    /**
     * 断线重连继续发送的文本
     */
    String reconnectText = "";
    /**
     * 提示词Fragment
     */
    PromptFragment promptFragment;
    private boolean isSpeak = true;

    private IflyTts mIflyTts;

    // 缓冲进度
    private int mPercentForBuffering = 0;
    // 播放进度
    private int mPercentForPlaying = 0;

    private File pcmFile;

    private Disposable mSubscription;
    SettingPopupView settingPopupView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        mApi.setFullscreen(this);
        asyncPlayer = new AsyncPlayer("AudioPlayer");
        mediaPlayer = new MediaPlayer();
        mIflyTts = new IflyTts(ChatActivity.this, ChatActivity.this);
        mApi.chatItems = new ArrayList<>();
        history = new ArrayList<>();

        verticalGridView = findViewById(R.id.vGridView);
        mSetting = findViewById(R.id.bt_setting);
        messageListAdapter = new MessageListAdapter();
        verticalGridView.setAdapter(messageListAdapter);
        verticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE);
        messageListAdapter.setOnItemClickListener(new MessageListAdapter.OnItemClickListener() {
            @Override
            public void onItemClickListener(View v) {
                LogUtil.i("item onClick!");
                startSpeech();
            }
        });
        mSetting.setOnClickListener(v -> {
            if(settingPopupView == null) {
                settingPopupView = new SettingPopupView(ChatActivity.this, new SettingPopupView.OnCheckListener() {
                    @Override
                    public void onCheck(Boolean onCheck) {
                       isSpeak = onCheck;
                    }
                });
            }
            settingPopupView.setSetting(isSpeak);
            settingPopupView.setPopupGravity(Gravity.RIGHT | Gravity.CLIP_HORIZONTAL);
            settingPopupView.showPopupWindow(mSetting);
        });

        speakingDialog = new SpeakingDialog(ChatActivity.this);
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
        mBtInput = findViewById(R.id.bt_input);
        mBtVoice = findViewById(R.id.bt_voice);
        webSocketAdapter = WebSocketAdapter.getInstance();
        connectWebSocket();
        subscribeToMessages();

        mBtInput.setOnClickListener(v -> {
            startSpeech();
        });
        mBtVoice.setOnClickListener(v -> {
            switchVoice();
        });
        mBtInput.requestFocus();
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case BOT_BEGIN:
                        // Bot begin printing
                        Log.e("BOT", "BEGIN");
                        isBotTalking = true;
                        mApi.chatItems.add(current_bot_chat);
                        refreshListview();
                        break;
                    case BOT_CONTINUE:
                        // Bot continue printing
                        Log.e("BOT", "printing: " + msg.obj.toString());
                        current_bot_chat.appendText(msg.obj.toString());
                        refreshListview();
                        break;
                    case USER_MSG:
                        // User' msg
                        removePromptFragment();
                        if (history.size() >= mApi.max_history) {
                            history.remove(0);
                            history.remove(0);
                        }
                        history.add("Q: " + msg.obj.toString() + "<|endoftext|>\n\n");
                        ChatItem chatItem = new ChatItem();
                        chatItem.setType(1);
                        chatItem.setText(msg.obj.toString());
                        mApi.chatItems.add(chatItem);
                        refreshListview();
                        break;
                    case BOT_END:
                        // Bot end printing
                        Log.e("BOT", "END");
                        isBotTalking = false;
                        if (!(null == msg.obj)) {
                            history.add("A: " + msg.obj + "<|endoftext|>\n\n");
                        }
                        mLastBotChat = current_bot_chat;
                        if (mLastBotChat != null) {
                            String text = mLastBotChat.getText();
                            if (text != null && !text.isEmpty() && isSpeak) {
                                pcmFile = new File(getExternalCacheDir().getAbsolutePath(), "tts_pcmFile.pcm");
                                pcmFile.delete();
                                mIflyTts.startSpeaking(text);
                            }
                        }
                        current_bot_chat = new ChatItem();
                        current_bot_chat.setType(0);
                        current_bot_chat.setText("\t\t");
                        refreshListview();
                        mBtInput.setEnabled(true);
                        mBtInput.requestFocus();
                        break;
                    case CLEAR_HISTORY:
                        // Delete History
                        history.clear();
                        mApi.chatItems.clear();
                        refreshListview();
                        mApi.showMsg(ChatActivity.this, "记忆已清除");
                        break;

                    default:
                        break;
                }
            }
        };
        handler.sendEmptyMessage(BOT_END);
        initRecord();

        if (savedInstanceState == null) {
            initPromptFragment();
        }
        try {
            // 订阅RxBus并处理事件的代码
            subscribePromptClick();
        } catch (Exception e) {
            // 处理异常
            e.printStackTrace();
        }

    }

    /**
     * 订阅提示词item点击事件
     */
    public void subscribePromptClick() {
        mSubscription = RxBus.getDefault().toObservable(String.class).subscribe(new Consumer<String>() {
            @Override
            public void accept(String prompt) throws Exception {
                sendMessage(prompt);
                removePromptFragment();
            }

        });
        RxSubscriptions.add(mSubscription);
    }

    /**
     * 取消订阅，防止内存泄漏
     */
    public void unsubscribe() {
        if (mSubscription!=null){
            RxSubscriptions.remove(mSubscription);
        }
    }

    private void switchVoice() {
        isSpeak = !isSpeak;
        mBtVoice.setImageResource(isSpeak ? R.drawable.slector_voice_on : R.drawable.slector_voice_off);
    }

    private void removePromptFragment() {
        // 使用FragmentManager移除当前Fragment
        if (promptFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(promptFragment)
                    .commit();
        }
    }

    private void initPromptFragment() {
        promptFragment = new PromptFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, promptFragment)
                .commit();
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
                            mApi.showMsg(ChatActivity.this, "成功连接至服务器");
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
                    }

                    @Override
                    public void onComplete() {
                        // 连接完成时的回调
                        LogUtil.i();
                    }
                });
    }


    private void startSpeech() {
        if (mIflyTts != null) {
            mIflyTts.stopSpeaking();
        }
        //检查网络
        if (NetStateUtils.getNetworkType(ChatActivity.this) == 0) {
            Toast.makeText(ChatActivity.this, "请检查网络", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isBotTalking) {
            webSocketAdapter.send(SEND_STOP);
        }
        speakingDialog.show();
        startSpeechToText();
        LogUtil.i("mBtInput.setOnClickListener");
        mBtInput.setEnabled(false);
    }

    @SuppressLint("NotifyDataSetChanged")
    void refreshListview() {
        messageListAdapter.notifyDataSetChanged();
        verticalGridView.setAdapter(messageListAdapter);
        verticalGridView.scrollToPosition(mApi.chatItems.size() - 1);
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

    void connectToVpsAndSengMsg(String recognizedText) {
        mApi.showMsg(this, "重新连接至服务器...");
        reconnectText = recognizedText;
        connectWebSocket();
//        reconnectCount++;
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

    void deleteCacheFiles() {
        System.out.println(getExternalCacheDir().getPath());
        for (File file : new File(getExternalCacheDir().toString()).listFiles()) {
            if (file.exists() && file.isFile()) {
                System.out.println(file.getPath());
                file.delete();
            }
        }
    }

    @Override
    protected void onDestroy() {
        deleteCacheFiles();
        disconnectWebSocket();
        unsubscribeFromMessages();
        unsubscribe();
        super.onDestroy();
    }


    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private SpeechRecognizer speechRecognizer;

    private void initRecord() {
        // 检查录音权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }
    }


    private void stopSpeechToText() {
        LogUtil.d("语音录入超时的时候调用");

        sendMessage(onPartialRecognizedText);

        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            stopSpeechEvent();
        }
    }

    /**
     * 语音转换文字q
     */
    private void startSpeechToText() {

        //检查当前系统有没有语音识别服务,该方法返回false在我们调用*SpeechRecognizer.startListening();*方法的时候会日志中发现这行log
        //no selected voice recognition service
        boolean recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(this);
        if (recognitionAvailable) {
            // 设置语音识别监听器
            speechRecognizer.setRecognitionListener(this);
            // 创建意图进行语音识别
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);

            // 开始语音识别
            speechRecognizer.startListening(intent);
        } else {
            LogUtil.d("该手机没有录音服务");
        }

    }


    @Override
    public void onReadyForSpeech(Bundle params) {
        // 在准备开始说话时调用
        LogUtil.i("在准备开始说话时调用");
        speakingDialog.setTip("请说，我在听...");
        isFetchingSound = true;
        isPartialResult = false;
    }

    private Runnable recognizeTimeoutRunnable = this::recognizeTimeout;

    private void recognizeTimeout() {
        if (!isPartialResult) {
            //3s后还没有任何识别
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                stopSpeechEvent();
            }
            mApi.showMsg(ChatActivity.this, "识别超时");
            LogUtil.i("识别超时");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onBeginningOfSpeech() {
        // 在开始说话时调用
        LogUtil.i("在开始说话时调用");
        //设置3s后，还没有onPartialResults语音部分识别回调，则关闭dialog
        handler.postDelayed(recognizeTimeoutRunnable, 3000);
    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    private Runnable stopListeningRunnable = this::stopSpeechToText;

    @Override
    public void onBufferReceived(byte[] buffer) {
        // 在获取到语音输入的音频数据时调用
        LogUtil.i(buffer);

    }

    @Override
    public void onEndOfSpeech() {
        stopSpeechEvent();
        // 在说话结束时调用
        LogUtil.i("说话结束时调用");
    }

    private void stopSpeechEvent() {
        isFetchingSound = false;
        if (speakingDialog != null && speakingDialog.isShowing()) {
            speakingDialog.dismissAndSetTip();
        }
        mBtInput.setEnabled(true);
        mBtInput.requestFocus();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onError(int error) {
        stopSpeechEvent();
        // 在发生错误时调用
        LogUtil.i("错误码:" + error);
        mBtInput.setEnabled(true);
        mBtInput.requestFocus();
        String errMsg = "";
        switch (error) {
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                errMsg = "网络链接超时";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                errMsg = "网络错误或者没有权限";
                break;
            case SpeechRecognizer.ERROR_AUDIO:
                errMsg = "音频发生错误";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errMsg = ("连接出错或取消");
                break;
            case SpeechRecognizer.ERROR_SERVER:
                errMsg = ("服务器出错");
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                errMsg = ("什么也没有听到");
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errMsg = ("没有匹配到合适的结果");
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                errMsg = ("RecognitionService已经启动,请稍后");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errMsg = ("请赋予APP权限,另请（Android6.0以上）确认动态申请权限");
                break;
            default:
                break;
        }
        mApi.showMsg(ChatActivity.this, errMsg);
        LogUtil.i(errMsg);
        if (handler.hasCallbacks(recognizeTimeoutRunnable)) {
            handler.removeCallbacks(recognizeTimeoutRunnable);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onResults(Bundle results) {
        if (handler.hasCallbacks(stopListeningRunnable)) {
            handler.removeCallbacks(stopListeningRunnable);
        }
        // 在识别结果可用时调用
        ArrayList<String> result = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (result != null && !result.isEmpty()) {
            String recognizedText = result.get(0);
            LogUtil.d("语音输出：" + recognizedText);
            speakingDialog.setTip(recognizedText);
            sendMessage(recognizedText);
        }
        stopSpeechEvent();
    }


    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onPartialResults(Bundle partialResults) {
        // 在部分识别结果可用时调用
        isPartialResult = true;
        ArrayList<String> partialResultList = partialResults.getStringArrayList("android.speech.extra.UNSTABLE_TEXT");
        if (partialResultList != null && !partialResultList.isEmpty()) {
            String recognizedText = partialResultList.get(0);
            if (recognizedText != null && !recognizedText.isEmpty()) {
                LogUtil.d("语音部分输出：" + recognizedText);
                speakingDialog.setTip(recognizedText);
                onPartialRecognizedText = recognizedText;
            }
        }
        LogUtil.i();

        if (handler.hasCallbacks(stopListeningRunnable)) {
            handler.removeCallbacks(stopListeningRunnable);
        }
        handler.postDelayed(stopListeningRunnable, LISTENING_TIMEOUT);

    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        // 在识别事件时调用
        LogUtil.i("事件：" + eventType);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("keyCode：" + keyCode);

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            startSpeech();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    private void disconnectWebSocket() {
        webSocketAdapter.disconnect();
    }

    private void subscribeToMessages() {
        messageDisposable = webSocketAdapter.observeMessages()
                .subscribe(
                        message -> {
                            // 处理接收到的消息
                            Log.e("MSG1", message);
                            if (message.equals("[PING-ALIVE]")) {
                                return;
                            }
                            if (isBotTalking) {
                                if (message.equals(SEND_END) || message.equals(SEND_STOP_SUCCESS)) {
                                    sendHandlerMsg(BOT_END, bot_record);
                                    //Log.e("Msg", bot_record);
                                    bot_record = "";
                                } else {
                                    JSONObject object = JSONObject.parseObject(message);
                                    if (object != null) {
                                        Log.e("object  ", object.toString());
                                        String role = object.getString("role");
                                        LogUtil.i("role == " + role);
                                        String content = object.getString("content");
                                        LogUtil.i("content == " + content);
                                        bot_record += content;
                                        if (content != null) {
                                            sendHandlerMsg(BOT_CONTINUE, content);
                                        }
                                        int code = object.getIntValue("code");
                                        if (code != 0) {
                                            sendHandlerMsg(BOT_END, content);
                                        }
                                    }
                                }
                            }
                        },
                        throwable -> {
                            // 处理订阅错误
                            LogUtil.i();
                        }
                );
    }

    private void unsubscribeFromMessages() {
        if (messageDisposable != null && !messageDisposable.isDisposed()) {
            messageDisposable.dispose();
        }
    }

    @Override
    public void onSpeakBegin() {
        LogUtil.d("开始语音播放");
    }

    @Override
    public void onBufferProgress(int percent, int beginPos, int endPos,
                                 String info) {
        // 合成进度
        mPercentForBuffering = percent;
        String format = String.format("缓冲进度为%d%%，播放进度为%d%%", mPercentForBuffering, mPercentForPlaying);
        LogUtil.d(format);
        LogUtil.d("开始语音播放");
    }

    @Override
    public void onSpeakPaused() {
        LogUtil.d("暂停语音播放");
    }

    @Override
    public void onSpeakResumed() {
        LogUtil.d("继续语音播放");
    }

    @Override
    public void onSpeakProgress(int percent, int beginPos, int endPos) {
        // 播放进度
        mPercentForPlaying = percent;
        String format = String.format("缓冲进度为%d%%，播放进度为%d%%", mPercentForBuffering, mPercentForPlaying);
        LogUtil.d(format);
    }

    @Override
    public void onCompleted(SpeechError error) {
        LogUtil.d("语音播放完成");
        if (error != null) {
            Toast.makeText(ChatActivity.this, error.getPlainDescription(true), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        //	 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
        //	 若使用本地能力，会话id为null
        if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            LogUtil.d("session id =" + sid);
        }
        // 当设置 SpeechConstant.TTS_DATA_NOTIFY 为1时，抛出buf数据
        if (SpeechEvent.EVENT_TTS_BUFFER == eventType) {
            byte[] buf = obj.getByteArray(SpeechEvent.KEY_EVENT_TTS_BUFFER);
            LogUtil.e("EVENT_TTS_BUFFER = " + buf.length);
            // 保存文件
            appendFile(pcmFile, buf);
        }
    }

    /**
     * 给file追加数据
     */
    private void appendFile(File file, byte[] buffer) {
        try {
            if (!file.exists()) {
                boolean b = file.createNewFile();
            }
            RandomAccessFile randomFile = new RandomAccessFile(file, "rw");
            randomFile.seek(file.length());
            randomFile.write(buffer);
            randomFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送消息
     * @param msg
     */
    private void sendMessage(String msg) {

        if (msg == null && msg.length() == 0) {
            mApi.showMsg(this, "请先输入文本");
            return;
        }
        if (webSocketAdapter.getConnectionState() == WebSocketAdapter.ConnectionState.CONNECTED) {
            if (isBotTalking) {
                mApi.showMsg(this, "请等待 AI 回答结束");
                return;
            }
            webSocketAdapter.send(msg);
            sendHandlerMsg(USER_MSG, msg);
            sendHandlerMsg(BOT_BEGIN, null);
        } else {
            if (reconnectCount == 0) {
                mApi.showMsg(this, "未连接至服务器");
                //尝试重连一次
                connectToVpsAndSengMsg(msg);
            }
        }

    }
}