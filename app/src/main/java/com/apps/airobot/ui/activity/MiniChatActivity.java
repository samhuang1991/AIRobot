package com.apps.airobot.ui.activity;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.VerticalGridView;

import com.alibaba.fastjson.JSONObject;
import com.apps.airobot.ChatItem;
import com.apps.airobot.LogUtil;
import com.apps.airobot.MyApplication;
import com.apps.airobot.adapter.MessageListAdapter;
import com.apps.airobot.R;
import com.apps.airobot.adapter.MiniMessageListAdapter;
import com.apps.airobot.ifly.IflyTts;
import com.apps.airobot.mApi;
import com.apps.airobot.socket.WebSocketAdapter;
import com.apps.airobot.ui.dialog.SettingPopupView;
import com.apps.airobot.ui.widget.MiniBottomLayout;
import com.apps.airobot.util.NetStateUtils;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SynthesizerListener;

import org.libpag.PAGFile;
import org.libpag.PAGView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import io.reactivex.disposables.Disposable;

public class MiniChatActivity extends BaseChatActivity implements RecognitionListener, SynthesizerListener {

    private View rightView;
    private VerticalGridView verticalGridView;
    private MiniMessageListAdapter messageListAdapter;
    private IflyTts mIflyTts;

    ChatItem current_bot_chat;

    ChatItem mLastBotChat;
    private File pcmFile;
    private boolean isSpeak = true;
    String onPartialRecognizedText;
    private Disposable messageDisposable;
    private ImageView mSetting;
    private MiniBottomLayout mBottomLL;
    SettingPopupView settingPopupView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minichat);
        mIflyTts = new IflyTts(MiniChatActivity.this, MiniChatActivity.this);
        mApi.chatItems = new ArrayList<>();

        initUI();

        showRightView();

        subscribeToMessages();

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

    @Override
    protected void handleCustomMessage(Message msg) {
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
//                current_bot_chat.setText("\t\t");
                refreshListview();

                break;
            case CLEAR_HISTORY:
                // Delete History
                mApi.chatItems.clear();
                refreshListview();
                mApi.showMsg( MiniChatActivity.this, "记忆已清除");
                break;

            default:
                break;
        }
    }

    @Override
    protected void initRecord() {
            // 检查录音权限
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET}, REQUEST_RECORD_AUDIO_PERMISSION);
            }
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }

    }

    protected void initUI() {
        rightView = findViewById(R.id.rightView);
        verticalGridView = findViewById(R.id.vGridView);
        messageListAdapter = new MiniMessageListAdapter();
        verticalGridView.setAdapter(messageListAdapter);
        verticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE);
        messageListAdapter.setOnItemClickListener(new MiniMessageListAdapter.OnItemClickListener() {
            @Override
            public void onItemClickListener(View v) {
                LogUtil.i("item onClick!");
                startSpeech();
            }
        });

        mSetting = findViewById(R.id.bt_setting);
        mSetting.setOnClickListener(v -> {
            if(settingPopupView == null) {
                settingPopupView = new SettingPopupView(MiniChatActivity.this, new SettingPopupView.OnCheckListener() {
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
        verticalGridView.requestFocus();

        mBottomLL = findViewById(R.id.bottomLL);
        PAGFile pagFile = PAGFile.Load(MyApplication.getContext().getAssets(), "lines.pag");
        PAGView pagView = findViewById(R.id.pagView);
        pagView.setComposition(pagFile);
        pagView.setRepeatCount(10);
        pagView.play();

        handler.sendEmptyMessage(BOT_END);

    }


    private void startSpeech() {
        if (mIflyTts != null) {
            mIflyTts.stopSpeaking();
        }
        //检查网络
        if (NetStateUtils.getNetworkType(MiniChatActivity.this) == 0) {
            Toast.makeText(MiniChatActivity.this, "请检查网络", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isBotTalking) {
            sendHandlerMsg(BOT_END, null);
            sendImplicitMessage(SEND_STOP);
        }
//        speakingDialog.show();
        showBottomLayout("");
        startSpeechToText();
    }

    void refreshListview() {
        messageListAdapter.notifyDataSetChanged();
        verticalGridView.setAdapter(messageListAdapter);
        verticalGridView.scrollToPosition(mApi.chatItems.size() - 1);
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

    private void showRightView() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        rightView.setTranslationX(screenWidth);
        ObjectAnimator animator = ObjectAnimator.ofFloat(rightView, "translationX", 0);
        animator.setDuration(1000); // 设置动画时长，单位为毫秒
        animator.start();
    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {
        // 在准备开始说话时调用
        LogUtil.i("在准备开始说话时调用");
//        speakingDialog.setTip("请说，我在听...");
        showBottomLayout("请说，我在听...");
        isFetchingSound = true;
        isPartialResult = false;
    }
    private Runnable recognizeTimeoutRunnable = this::recognizeTimeout;
    private Runnable stopListeningRunnable = this::stopSpeechToText;

    private void recognizeTimeout() {
        if (!isPartialResult) {
            //3s后还没有任何识别
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                stopSpeechEvent();
            }
            mApi.showMsg(this, "识别超时");
            LogUtil.i("识别超时");
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

    private void stopSpeechEvent() {
        isFetchingSound = false;
//        if (speakingDialog != null && speakingDialog.isShowing()) {
//            speakingDialog.dismissAndSetTip();
//        }
        hideBottomLayout();
    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onError(int error) {
        stopSpeechEvent();
        // 在发生错误时调用
        LogUtil.i("错误码:" + error);
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
        mApi.showMsg(this, errMsg);
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
//            speakingDialog.setTip(recognizedText);
            showBottomLayout(recognizedText);
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
//                speakingDialog.setTip(recognizedText);
                showBottomLayout(recognizedText);
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
    public void onEvent(int eventType, Bundle bundle) {
        LogUtil.i("事件：" + eventType);

    }

    @Override
    public void onSpeakBegin() {
        LogUtil.d("开始语音播放");
    }


    @Override
    public void onBufferProgress(int i, int i1, int i2, String s) {

    }

    @Override
    public void onSpeakPaused() {

    }

    @Override
    public void onSpeakResumed() {

    }

    @Override
    public void onSpeakProgress(int i, int i1, int i2) {

    }

    @Override
    public void onCompleted(SpeechError speechError) {

    }

    @Override
    public void onEvent(int i, int i1, int i2, Bundle bundle) {

    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtil.i("keyCode：" + keyCode);

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            startSpeech();
            return true;
        }else if (keyCode == KeyEvent.KEYCODE_DPAD_UP){
            LogUtil.i("SelectedPosition：" + verticalGridView.getSelectedPosition());
            if (verticalGridView.getSelectedPosition() == 0){
                mSetting.requestFocus();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        unsubscribeFromMessages();
        super.onDestroy();
    }

    private void unsubscribeFromMessages() {
        if (messageDisposable != null && !messageDisposable.isDisposed()) {
            messageDisposable.dispose();
        }
    }

    private void showBottomLayout(String text){
        mBottomLL.setVisibility(View.VISIBLE);
        mBottomLL.setTextViewText(text);
    }

    private void hideBottomLayout(){
        mBottomLL.setVisibility(View.GONE);
    }
}
