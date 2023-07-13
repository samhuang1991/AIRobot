package com.apps.airobot.ui.widget;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.apps.airobot.MyApplication;
import com.apps.airobot.R;
import com.apps.airobot.ui.activity.MainActivity;

import org.libpag.PAGFile;
import org.libpag.PAGView;

public class SpeakingDialog extends Dialog {


    private TextView mTvTip;
    private PAGView pagView;

    public SpeakingDialog(Context context) {
        super(context);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        // 设置对话框内容视图
        setContentView(R.layout.dialog_speaking);

        // 设置对话框宽度和位置
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);

        // 设置背景渐变透明
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x11000000, 0xFF000000});
        gradientDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        window.setBackgroundDrawable(gradientDrawable);

        mTvTip = (TextView) findViewById(R.id.tvTip);
        pagView = (PAGView) findViewById(R.id.pagView);

    }


    public void setTip(String tip){
        mTvTip.setText(tip);
    }
    @Override
    public void show() {
        super.show();
        setTip("");
        PAGFile pagFile = PAGFile.Load(MyApplication.getContext().getAssets(), "lines.pag");
        pagView.setComposition(pagFile);
        pagView.setRepeatCount(10);
        pagView.play();
    }



    public void dismissAndSetTip(){
        setTip("");
        dismiss();
    }

}
