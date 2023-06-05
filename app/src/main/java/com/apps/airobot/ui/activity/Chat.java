package com.apps.airobot.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.VerticalGridView;

import com.alibaba.fastjson.JSONObject;
import com.apps.airobot.ActivityController;
import com.apps.airobot.ChatItem;
import com.apps.airobot.LogUtil;
import com.apps.airobot.MessageListAdapter;
import com.apps.airobot.R;
import com.apps.airobot.mApi;
import com.apps.airobot.ui.widget.SpeakingDialog;
import com.apps.airobot.util.NetStateUtils;


import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Chat extends AppCompatActivity implements RecognitionListener {
    static UUID uuid;
    int selectedItemPosition = -1;
    private static boolean useWebSocket = true;
    private static final int LISTENING_TIMEOUT = 1500; // 设置超时时间为5秒
    private static final int SILENCE_THRESHOLD = 1; // 设置音量阈值

    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();
    boolean isBotTalking = false,
            isConnecting = false,
            isFetchingSound = false,
            isPartialResult = false;
    AsyncPlayer asyncPlayer;
    MediaPlayer mediaPlayer;
    ArrayList<String> history;
    ChatItem current_bot_chat;
    SpeakingDialog speakingDialog;
    //    Iflytek mIflytek;
    WebSocketClient webSocketClient;
    int reconnectCount = 0; // 重连次数
    String onPartialRecognizedText;


    private VerticalGridView verticalGridView;
    private MessageListAdapter messageListAdapter;
    EditText input;
    ImageView help,
            start,
            config,
            del_history,
            connect,
            mBtInput;
    Handler handler;
    long mBackPressed;
    static final int BOT_BEGIN = 0,
            BOT_CONTINUE = 1,
            USER_MSG = 2,
            BOT_END = 3,
            CLEAR_HISTORY = 4;

    String serverURL = "",
            bot_record = "",
            soundFilePath,
            SEND_END = "[DONE]",
            FILE_END = "///**END_OF_FILE**///",
            FILE_ERROR = "///**FILE_ERROR**///";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        mApi.setFullscreen(this);
        asyncPlayer = new AsyncPlayer("AudioPlayer");
        mediaPlayer = new MediaPlayer();
        mApi.chatItems = new ArrayList<>();
        history = new ArrayList<>();

        verticalGridView = findViewById(R.id.vGridView);
        messageListAdapter = new MessageListAdapter();
        verticalGridView.setAdapter(messageListAdapter);
        verticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE);

        speakingDialog = new SpeakingDialog(Chat.this);
        speakingDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                LogUtil.i("onDismiss isFetchingSound " + isFetchingSound);

                if (isFetchingSound) {
//                    handler.post(stopListeningRunnable);
//                    initRecord();
                    if (speechRecognizer != null) {
                        speechRecognizer.stopListening();
                        speechRecognizer.cancel();
                    }
                    LogUtil.i("onDismiss ");
                }
            }
        });
//         mIflytek = new Iflytek(this,mRecognizerListener );
        input = findViewById(R.id.input);
        help = findViewById(R.id.help);
        start = findViewById(R.id.start);
        config = findViewById(R.id.config);
        mBtInput = findViewById(R.id.bt_input);
        connect = findViewById(R.id.connect);
        del_history = findViewById(R.id.del_history);
        del_history.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("是否清除AI之前对话的记忆？");
            b.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
            b.setPositiveButton("清除", (dialog, which) -> {
                sendHandlerMsg(CLEAR_HISTORY, "");
            });
            b.show();
        });
        if (useWebSocket) {
            if (null == webSocketClient) {
                mApi.showMsg(this, "尝试连接至服务器...");
                connectToVps();
            } else if (!webSocketClient.isClosed()) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("已连接至服务器，确定强制重新连接吗？");
                builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
                builder.setPositiveButton("确定", (dialog, which) -> {
                    mApi.showMsg(this, "重新连接服务器...");
                    webSocketClient = null;
                    isConnecting = false;
                    if (isBotTalking) {
                        sendHandlerMsg(BOT_END, "");
                    }
                    connectToVps();
                });
                builder.show();
            }
        }
        config.setOnClickListener(v -> {
            showConfig();
        });
        start.setOnClickListener(v -> {
//            chatGPT_direct();
        });
        mBtInput.setOnClickListener(v -> {
            //检查网络
            if (NetStateUtils.getNetworkType(Chat.this) == 0) {
                Toast.makeText(Chat.this, "请检查网络", Toast.LENGTH_SHORT).show();
                return;
            }
            startSpeechToText();
            speakingDialog.show();
            LogUtil.i("mBtInput.setOnClickListener");
            mBtInput.setEnabled(false);
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
                        closeInputMethod();
                        input.setText("");
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
                        mApi.showMsg(Chat.this, "记忆已清除");
                        break;

                    default:
                        break;
                }
            }
        };
        handler.sendEmptyMessage(BOT_END);
        initRecord();
    }

    void connectToVps() {
        if ((null != webSocketClient) && isConnecting) {
            return;
        }
        new Thread(() -> {
            uuid = UUID.randomUUID();
            serverURL = "wss://ai.dp.qhmoka.com/ai-service/chatgpt/websocket/";

            try {
                webSocketClient = new WebSocketClientEx(new URI(serverURL + uuid));
                webSocketClient.setConnectionLostTimeout((int) mApi.RequestTimeout);
                if (webSocketClient.connectBlocking()) {
                    runOnUiThread(() -> {
                        mApi.showMsg(this, "成功连接至服务器");
                    });
                } else {
                    sendHandlerMsg(BOT_BEGIN, null);
                    sendHandlerMsg(BOT_CONTINUE, "Failed to connect to the server");
                    sendHandlerMsg(BOT_END, null);
                }
            } catch (URISyntaxException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        isConnecting = true;
    }

    class WebSocketClientEx extends WebSocketClient {
        WebSocketClientEx(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshakeData) {
            isConnecting = false;
            isBotTalking = false;
        }

        @Override
        public void onMessage(String message) {
            Log.e("MSG1", message);
            if (isBotTalking) {
                if (message.equals(SEND_END)) {
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
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            new Thread(() -> {
                isConnecting = false;
                isBotTalking = false;
                isFetchingSound = false;
                webSocketClient = null;
                mApi.showMsg(Chat.this, "服务器连接断开");
                Log.e("Close", reason);
            }).start();
        }

        @Override
        public void onError(Exception ex) {
            new Thread(() -> {
                isConnecting = false;
                isBotTalking = false;
                isFetchingSound = false;
                webSocketClient = null;
                mApi.showMsg(Chat.this, "服务器连接错误： " + ex.getMessage());
                Log.e("Exception", ex.getMessage());
            }).start();
        }
    }

    void closeInputMethod() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm.isActive()) {
            View v = getWindow().peekDecorView();
            if (null != v) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
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


    String buildPrompt(String context) {
        StringBuilder prompt = new StringBuilder();
        if (history.size() > 0) {
            for (String s : history) {
                prompt.append(s);
            }
        }
        prompt.append("Q: ").append(context).append("\n\nA:");
        LogUtil.i("prompt == " + prompt.toString());
        return prompt.toString();
    }

    void chatGPT_direct(String context) {
        if (context == null || context.isEmpty()) {
            Toast.makeText(Chat.this, "请输入有效的内容", Toast.LENGTH_SHORT).show();
            return;
        }

        String endpoint;
        OkHttpClient client = new OkHttpClient().newBuilder()
                .connectTimeout(mApi.RequestTimeout, TimeUnit.SECONDS)
                .writeTimeout(mApi.RequestTimeout, TimeUnit.SECONDS)
                .readTimeout(mApi.RequestTimeout, TimeUnit.SECONDS)
                .build();
        com.alibaba.fastjson.JSONObject jsonObject = new com.alibaba.fastjson.JSONObject();
        jsonObject.put("model", mApi.model);
        jsonObject.put("prompt", buildPrompt(context));
        jsonObject.put("max_tokens", mApi.max_token);
        jsonObject.put("temperature", mApi.temperature);
        jsonObject.put("top_p", 1);
        jsonObject.put("stream", mApi.stream);
        if (mApi.model.equals("gpt-3.5-turbo") || mApi.model.equals("gpt-3.5-turbo-0301")) {
            endpoint = "https://api.openai.com/v1/chat/completions";
            List<JSONObject> list = new ArrayList<>();
            JSONObject msg1 = new JSONObject();
            msg1.put("role", "user");
            msg1.put("content", "我是Moka");
            list.add(msg1);
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", jsonObject.getString("prompt"));
            list.add(msg);
            jsonObject.put("messages", list);
            jsonObject.remove("prompt");
        } else {
            endpoint = "https://api.openai.com/v1/completions";
        }
        sendHandlerMsg(USER_MSG, context);
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + mApi.API_KEY)
                .post(RequestBody.create(jsonObject.toString(),
                        MediaType.parse("application/json")))
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                sendHandlerMsg(BOT_BEGIN, null);
                if (response.isSuccessful()) {
                    InputStream inputStream = response.body().byteStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    StringBuilder res = new StringBuilder();
                    try {
                        while ((line = reader.readLine()) != null) {
                            if (line.length() < 50) {
                                continue;
                            }
                            JSONObject object = JSONObject.parseObject(line.substring(6));
                            JSONObject choices = JSONObject.parseObject(object.getString("choices")
                                    .replace('[', ' ')
                                    .replace(']', ' '));
                            LogUtil.i("object == " + object.toString());
                            //{"created":1685088378,"model":"gpt-3.5-turbo-0301","id":"chatcmpl-7KMkkvyX9jbHAqVz3k96vjMiLmBG1","choices":[{"delta":{"role":"assistant"},"index":0}],"object":"chat.completion.chunk"}
                            //{"created":1685088378,"model":"gpt-3.5-turbo-0301","id":"chatcmpl-7KMkkvyX9jbHAqVz3k96vjMiLmBG1","choices":[{"delta":{"content":"连接"},"index":0}],"object":"chat.completion.chunk"}
                            //{"created":1685088378,"model":"gpt-3.5-turbo-0301","id":"chatcmpl-7KMkkvyX9jbHAqVz3k96vjMiLmBG1","choices":[{"finish_reason":"stop","delta":{},"index":0}],"object":"chat.completion.chunk"}
                            String s;
                            if (mApi.model.equals("gpt-3.5-turbo") || mApi.model.equals("gpt-3.5-turbo-0301")) {
                                s = JSONObject.parseObject(choices.getString("delta")).getString("content");
                                LogUtil.i("s == " + s);
                                if (s == null) {
                                    s = "";
                                }
                            } else {
                                s = choices.getString("text");
                            }
                            res.append(s);
                            sendHandlerMsg(BOT_CONTINUE, s);
                        }
                        reader.close();
                        inputStream.close();
                        sendHandlerMsg(BOT_END, res.toString());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    sendHandlerMsg(BOT_CONTINUE, "SERVER ERROR 0X2H\n");
                    sendHandlerMsg(BOT_CONTINUE, response.body().source().readUtf8());
                    sendHandlerMsg(BOT_END, "");
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                sendHandlerMsg(BOT_CONTINUE, "SERVER ERROR 0X3H\n");
                sendHandlerMsg(BOT_END, "");
            }
        });

    }


    private void chatGPT_webSocket(String recognizedText) {
        if (recognizedText.equals("")) {
            mApi.showMsg(this, "请先输入文本");
        } else {
            if (!(null == webSocketClient) && webSocketClient.isOpen()) {
                if (isBotTalking) {
                    mApi.showMsg(this, "请等待 AI 回答结束");
                    return;
                }
                webSocketClient.send(recognizedText);
                sendHandlerMsg(USER_MSG, recognizedText);
                sendHandlerMsg(BOT_BEGIN, null);
            } else {
                if (reconnectCount == 0) {
                    mApi.showMsg(this, "未连接至服务器");
                    //尝试重连一次
                    connectToVpsAndSengMsg(recognizedText);
                }
            }
        }
    }

    void connectToVpsAndSengMsg(String recognizedText) {
        reconnectCount++;
        if ((null != webSocketClient) && isConnecting) {
            return;
        }
        new Thread(() -> {
            uuid = UUID.randomUUID();

            serverURL = "wss://ai.dp.qhmoka.com/ai-service/chatgpt/websocket/";

            try {
                webSocketClient = new WebSocketClientEx(new URI(serverURL + uuid));
                webSocketClient.setConnectionLostTimeout((int) mApi.RequestTimeout);
                if (webSocketClient.connectBlocking()) {
                    runOnUiThread(() -> {
                        mApi.showMsg(this, "已重新连接至服务器");
                        isConnecting = true;
                        chatGPT_webSocket(recognizedText);
                    });
                }
            } catch (URISyntaxException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                reconnectCount = 0;
            }
        }, 10000);
    }

    void initConfigs(View view) {
        ArrayList<ArrayList<?>> list = new ArrayList<>(Arrays.asList(
                new ArrayList<>(Arrays.asList(3, 5, 10, 30, 50, 70, 100, 200)),
                new ArrayList<>(Arrays.asList(20, 50, 100, 200, 500, 1000, 2000)),
                new ArrayList<>(Arrays.asList(0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6,
                        0.7, 0.8, 0.9, 1.0)),
                new ArrayList<>(Arrays.asList("gpt-3.5-turbo", "gpt-3.5-turbo-0301",
                        "text-davinci-003", "text-davinci-002")),
                new ArrayList<>(Arrays.asList(true)),
                new ArrayList<>(Arrays.asList(10, 20, 30, 50, 70, 100)),
                new ArrayList<>(Arrays.asList("不使用中转", "自定义服务器", "英国 S1", "美国 S1", "美国 S2")),
                new ArrayList<>(Arrays.asList(true, false)),
                //new ArrayList<>(Arrays.asList("派蒙"))
                new ArrayList<>(Arrays.asList("派蒙", "可莉", "纳西妲", "荧", "刻晴"))
        ));
        ArrayList<Spinner> spinners = new ArrayList<>();
        spinners.add(view.findViewById(R.id.config_timeout));
        spinners.add(view.findViewById(R.id.config_max_token));
        spinners.add(view.findViewById(R.id.config_temperature));
        spinners.add(view.findViewById(R.id.config_model));
        spinners.add(view.findViewById(R.id.config_stream));
        spinners.add(view.findViewById(R.id.config_history));
        spinners.add(view.findViewById(R.id.config_useVps));
        spinners.add(view.findViewById(R.id.config_turn_on_vits));
        spinners.add(view.findViewById(R.id.config_vits_model));

        EditText text = view.findViewById(R.id.config_custom_vps);
        text.setText(mApi.custom_url);
        for (int i = 0; i < list.size(); i++) {
            setSpinnerAdapter(spinners.get(i), list.get(i), i, view);
        }
    }

    void setSpinnerAdapter(Spinner sp, ArrayList<?> arrayList, int flag, View parent) {
        ArrayAdapter<?> starAdapter =
                new ArrayAdapter<>(this, R.layout.item_select, arrayList);
        starAdapter.setDropDownViewResource(R.layout.item_dropdown);
        sp.setAdapter(starAdapter);
        switch (flag) {
            case 0:
                sp.setSelection(arrayList.indexOf((int) mApi.RequestTimeout));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.RequestTimeout = (long) (int) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 1:
                sp.setSelection(arrayList.indexOf(mApi.max_token));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.max_token = (int) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 2:
                sp.setSelection(arrayList.indexOf(mApi.temperature));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.temperature = (double) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 3:
                sp.setSelection(arrayList.indexOf(mApi.model));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.model = (String) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 4:
                sp.setSelection(arrayList.indexOf(mApi.stream));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.stream = (boolean) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 5:
                sp.setSelection(arrayList.indexOf(mApi.max_history));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.max_history = (int) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 6:
                sp.setSelection(arrayList.indexOf(mApi.use_vps));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.use_vps = (String) sp.getSelectedItem();
                        if (mApi.use_vps.equals("自定义服务器")) {
                            parent.findViewById(R.id.child_layout_custom_vps).setVisibility(View.VISIBLE);
                        } else {
                            parent.findViewById(R.id.child_layout_custom_vps).setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 7:
                sp.setSelection(arrayList.indexOf(mApi.use_vits));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.use_vits = (boolean) sp.getSelectedItem();
                        if (mApi.use_vits) {
                            parent.findViewById(R.id.child_layout_vits_model).setVisibility(View.VISIBLE);
                        } else {
                            parent.findViewById(R.id.child_layout_vits_model).setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
            case 8:
                sp.setSelection(arrayList.indexOf(mApi.vits_speaker));
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        mApi.vits_speaker = (String) sp.getSelectedItem();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
                break;
        }
    }

    private void showConfig() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = View.inflate(this, R.layout.layout_config, null);
        initConfigs(view);
        final EditText config_key = view.findViewById(R.id.config_key);
        if (!(null == mApi.API_KEY) && !mApi.API_KEY.equals("")) {
            config_key.setText(mApi.API_KEY);
        }
        builder.setView(view);
        builder.setTitle("设置");
        builder.setNegativeButton("取 消", (dialog, which) -> dialog.dismiss());
        builder.setPositiveButton("确 定", (dialog, which) -> {
            mApi.API_KEY = config_key.getText().toString().trim();
            SharedPreferences.Editor ed = mApi.sharedPreferences.edit();
            ed.putString("key", mApi.API_KEY);
            ed.putString("timeout", String.valueOf(mApi.RequestTimeout));
            ed.putString("max_token", String.valueOf(mApi.max_token));
            ed.putString("temperature", String.valueOf(mApi.temperature));
            ed.putString("model", mApi.model);
            ed.putString("stream", String.valueOf(mApi.stream));
            ed.putString("max_history", String.valueOf(mApi.max_history));
            ed.putString("use_vps", String.valueOf(mApi.use_vps));
            ed.putString("use_vits", String.valueOf(mApi.use_vits));
            if (mApi.use_vps.equals("自定义服务器")) {
                ed.putString("custom_url", mApi.custom_url);
                EditText custom_url = view.findViewById(R.id.config_custom_vps);
                mApi.custom_url = custom_url.getText().toString().trim();
            }
            if (mApi.use_vits) {
                ed.putString("vits_model", mApi.vits_speaker);
            }
            ed.apply();
        }).show();
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

        if (useWebSocket) {
            //websocket 发送消息
            chatGPT_webSocket(onPartialRecognizedText);
        } else {
            chatGPT_direct(onPartialRecognizedText);
        }

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
//        mIatResults.clear();
//        mIflytek.startVoice();


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
        isFetchingSound = true;
        isPartialResult = false;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @Override
    public void onBeginningOfSpeech() {
        // 在开始说话时调用
        LogUtil.i("在开始说话时调用");
        //设置3s后，还没有onPartialResults语音部分识别回调，则关闭dialog
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isPartialResult) {
                    //3s后还没有任何识别
                    if (speechRecognizer != null) {
                        speechRecognizer.stopListening();
                        speechRecognizer.cancel();
                        stopSpeechEvent();
                    }
                    mApi.showMsg(Chat.this, "识别超时");
                }
            }
        }, 3000);

    }

    @Override
    public void onRmsChanged(float rmsdB) {

//        if (Math.abs(rmsdB) > SILENCE_THRESHOLD) {
////            if (speakingDialog != null && speakingDialog.isShowing()){
////                speakingDialog.getmWaveView().setRmsdB(rmsdB);
////            }
//            LogUtil.i("有效语音: " + rmsdB);
//            // 当检测到有效语音输入时，取消计时
//            handler.removeCallbacks(stopListeningRunnable);
//        }else {
//            // 无效的语音的时候开始倒计时
//            LogUtil.i("无效语音: " + rmsdB);
//            handler.postDelayed(stopListeningRunnable,LISTENING_TIMEOUT);
//        }

    }

    private Runnable stopListeningRunnable = this::stopSpeechToText;

    @Override
    public void onBufferReceived(byte[] buffer) {
        // 在获取到语音输入的音频数据时调用
        LogUtil.i();
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
                errMsg = "recognizer network timeout";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                errMsg = "recognizer network error";
                break;
            case SpeechRecognizer.ERROR_AUDIO:
                errMsg = "recognizer audio error";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                errMsg = "recognizer server error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                errMsg = "recognizer client error";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                errMsg = "recognizer speech timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                errMsg = "recognizer no match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                errMsg = "recognizer busy";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                errMsg = "recognizer insufficient permissions";
                break;
            default:
                errMsg = "recognizer other error";
                break;
        }
        mApi.showMsg(Chat.this, errMsg);
        LogUtil.i(errMsg);
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
            if (useWebSocket) {
                //websocket 发送消息
                chatGPT_webSocket(recognizedText);
            } else {
                chatGPT_direct(recognizedText);
            }
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
        //2014
        LogUtil.i("keyCode：" + keyCode);
        return super.onKeyDown(keyCode, event);
    }

}