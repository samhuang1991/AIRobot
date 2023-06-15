package com.apps.airobot.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;

import com.apps.airobot.R;

public class GradientUnderlineTextView extends AppCompatTextView {

    private Rect rect;
    private Paint paint;
    private int viewWidth;
    private int viewHeight;
    private LinearGradient gradient;
    private float  underlineHeight;
    private float  underlineOffset;

    public GradientUnderlineTextView(Context context) {
        super(context);
        init(null);
    }

    public GradientUnderlineTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public GradientUnderlineTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        rect = new Rect();
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);

        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.GradientUnderlineTextView);
            underlineHeight = typedArray.getDimension(R.styleable.GradientUnderlineTextView_underlineHeight,  convertDpToPx(12));
            underlineOffset = typedArray.getDimension(R.styleable.GradientUnderlineTextView_underlineOffset,  convertDpToPx(12));
            typedArray.recycle();
        } else {
            underlineHeight = 4; // 默认下划线高度为4px
        }


    }

    @Override
    protected void onDraw(Canvas canvas) {
        int count = getLineCount();
        final Layout layout = getLayout();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();

        for (int i = 0; i < count; i++) {
            int baseline = (int) (layout.getLineBaseline(i)-underlineOffset);
            int descent = layout.getLineDescent(i);
            rect.left = (int) layout.getLineLeft(i) + getPaddingLeft();
            rect.right = (int) layout.getLineRight(i) + getPaddingLeft();
            rect.top = (int) (baseline + paddingTop + descent - underlineHeight);
            rect.bottom = baseline + paddingTop + descent;
            canvas.drawRect(rect, paint);
        }

        super.onDraw(canvas);

    }

    public int getUnderlineHeight() {
        return (int) underlineHeight;
    }

    public void setUnderlineHeight(int height) {
        underlineHeight = height;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        gradient = new LinearGradient(0, 0, viewWidth, 0, Color.parseColor("#FF039BE5"), Color.parseColor("#22039BE5"), Shader.TileMode.CLAMP);
        paint.setShader(gradient);
    }

    private float convertDpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}