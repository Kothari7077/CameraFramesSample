package com.example.cameraframessample;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

/** A {@link TextureView} that can be adjusted to a specified aspect ratio. */
public class AutoFitTextureView extends TextureView {
    static final String TAG = "AutoFitTextureView_CameraFrame";

    private int ratioWidth = 0;
    private int ratioHeight = 0;

    public AutoFitTextureView(final Context context) {
        this(context, null);
        Log.v(TAG, "Inside AutoFitTextureView constructor1" );
    }
    public AutoFitTextureView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
        Log.v(TAG, "Inside AutoFitTextureView constructor2" );
    }
    public AutoFitTextureView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        Log.v(TAG, "Inside AutoFitTextureView constructor3" );
    }

    public void setAspectRatio(final int width, final int height) {
        Log.v(TAG, "Inside setAspectRatio width: "+width +" height: " +height );
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Log.v(TAG, "Inside onMeasure widthMeasureSpec : " + widthMeasureSpec+ " heightMeasureSpec: "+heightMeasureSpec );
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        if (0 == ratioWidth || 0 == ratioHeight) {
            Log.v(TAG, "Inside onMeasure" );
            setMeasuredDimension(width, height);
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                Log.v(TAG, "Inside onMeasure" );
                setMeasuredDimension(width, width * ratioHeight / ratioWidth);
                Log.v(TAG, "Inside onMeasure" );
            } else {
                Log.v(TAG, "Inside onMeasure" );
                setMeasuredDimension(height * ratioWidth / ratioHeight, height);
                Log.v(TAG, "Inside onMeasure" );
            }
        }
    }
}
