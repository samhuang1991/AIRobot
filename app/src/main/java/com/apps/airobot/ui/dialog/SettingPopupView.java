package com.apps.airobot.ui.dialog;

import android.animation.Animator;
import android.content.Context;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.CheckBox;

import com.apps.airobot.R;
import com.apps.airobot.ui.widget.SmoothCheckBox;

import razerdp.basepopup.BasePopupWindow;
import razerdp.util.animation.AnimationHelper;
import razerdp.util.animation.TranslationConfig;

public class SettingPopupView extends BasePopupWindow {
    SmoothCheckBox checkBox;
    OnCheckListener monCheckListener;
    public SettingPopupView(Context context,OnCheckListener onCheckListener) {
        super(context);
        setContentView(R.layout.popup_setting);
        checkBox = findViewById(R.id.checkbox);
        monCheckListener = onCheckListener;
    }

    public void setSetting(Boolean setting){
        checkBox.setChecked(setting);
        checkBox.requestFocus();
    }

    @Override
    public void dismiss() {
        super.dismiss();
        boolean checked = checkBox.isChecked();
        monCheckListener.onCheck(checked);
    }

    @Override
    protected Animation onCreateShowAnimation() {
        return AnimationHelper.asAnimation()
                .withTranslation(TranslationConfig.FROM_LEFT)
                .toShow();
    }

    @Override
    protected Animation onCreateDismissAnimation() {
        return AnimationHelper.asAnimation()
                .withTranslation(TranslationConfig.TO_LEFT)
                .toDismiss();
    }

    public interface OnCheckListener{
        void onCheck(Boolean onCheck);
    }

}
