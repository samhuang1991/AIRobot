package com.apps.airobot.socket;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.net.URISyntaxException;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class WebSocketAdapter {
    private static WebSocketAdapter instance;
    private WebSocketClient webSocketClient;
    private WebSocketAdapterListener messageListener;

    public interface WebSocketAdapterListener {
        void onMessageReceived(String message);
    }

    private WebSocketAdapter() {
        // 私有构造函数，防止外部创建实例
    }

    public static WebSocketAdapter getInstance() {
        if (instance == null) {
            synchronized (WebSocketAdapter.class) {
                if (instance == null) {
                    instance = new WebSocketAdapter();
                }
            }
        }
        return instance;
    }

    public Observable<Boolean> connect(String url) {
        return Observable.create(new ObservableOnSubscribe<Boolean>() {
            @Override
            public void subscribe(ObservableEmitter<Boolean> emitter) throws Exception {
                try {
                    webSocketClient = new WebSocketClient(new URI(url)) {
                        @Override
                        public void onOpen(ServerHandshake handshakedata) {
                            connectionState = ConnectionState.CONNECTED;
                            emitter.onNext(true); // WebSocket连接成功
                            emitter.onComplete();
                        }

                        @Override
                        public void onMessage(String message) {
                            if (messageListener != null) {
                                messageListener.onMessageReceived(message);
                            }
                        }

                        @Override
                        public void onClose(int code, String reason, boolean remote) {
                            // 连接关闭回调
                            connectionState = ConnectionState.CLOSED;
                        }

                        @Override
                        public void onError(Exception ex) {
                            emitter.onError(ex); // WebSocket连接错误
                            connectionState = ConnectionState.ERROR;
                        }
                    };
                    webSocketClient.connect();
                } catch (URISyntaxException e) {
                    connectionState = ConnectionState.ERROR;
                    emitter.onError(e);
                }
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
            webSocketClient = null;
            connectionState = ConnectionState.CLOSED;
        }
    }

    public Observable<String> observeMessages() {
        return Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                setMessageListener(new WebSocketAdapterListener() {
                    @Override
                    public void onMessageReceived(String message) {
                        emitter.onNext(message);
                    }
                });
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public void setMessageListener(WebSocketAdapterListener listener) {
        messageListener = listener;
    }

    public void send(String message) {
        if (webSocketClient != null) {
            webSocketClient.send(message);
        }
    }

    public enum ConnectionState {
        CONNECTING,
        CONNECTED,
        CLOSING,
        CLOSED,
        RECONNECTING,
        ERROR
    }

    private ConnectionState connectionState = ConnectionState.CLOSED;

    public ConnectionState getConnectionState() {
        return connectionState;
    }

}

