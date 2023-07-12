package com.apps.airobot.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.apps.airobot.MyApplication;
import com.apps.airobot.R;

import org.libpag.PAGFile;
import org.libpag.PAGView;

public class MiniBottomLayout extends LinearLayout {
    private TextView textView;

    public MiniBottomLayout(Context context) {
        super(context);
        init(context);
    }

    public MiniBottomLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MiniBottomLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.layout_bottom_linear, this);
        textView = findViewById(R.id.textView);

        PAGFile pagFile = PAGFile.Load(MyApplication.getContext().getAssets(), "lines.pag");

        PAGView pagView = findViewById(R.id.pagView);
        pagView.setComposition(pagFile);
        pagView.setRepeatCount(10);
        pagView.play();
    }

    public void setTextViewText(String text) {
        textView.setText(text);
    }
}
