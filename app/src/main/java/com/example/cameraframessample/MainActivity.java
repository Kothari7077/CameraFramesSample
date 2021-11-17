package com.example.cameraframessample;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener {
    static final String TAG = "MainActivity_CameraFrame";

    int previewHeight = 0;
    int previewWidth = 0;
    int sensorOrientation;

    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Bitmap rgbFrameBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.v(TAG, "OnCreate" );
        //TODO ask for camera permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.v(TAG, "Inside onCreate: ask permissions" );
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA}, 121);
        } else {
            //TODO show live camera footage
            Log.v(TAG, "Inside OnCreate: setFragment called" );
            setFragment();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //TODO show live camera footage
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //TODO show live camera footage
            Log.v(TAG, "Inside onRequestPermissionsResult" );
            setFragment();
            Log.v(TAG, "Inside onRequestPermissionsResult finished calling setFragment" );
        } else {
            finish();
        }
    }

    //TODO fragment which show live footage from camera
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    protected void setFragment() {
        Log.v(TAG, "Inside setFragment" );
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String cameraId = null;
        try {
            Log.v(TAG, "Inside setFragment: get cameraId" );
            cameraId = manager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.v(TAG, "Inside setFragment,fragment declared" );
        CameraConnectionFragment fragment;
        Log.v(TAG, "Inside setFragment camera2Fragment declared" );
        CameraConnectionFragment camera2Fragment =
                CameraConnectionFragment.newInstance(
                        new CameraConnectionFragment.ConnectionCallback() {
                            @Override
                            public void onPreviewSizeChosen(final Size size, final int rotation) {
                                Log.v(TAG, "Inside setFragment,onPreviewSizeChosen" );
                                previewHeight = size.getHeight();
                                previewWidth = size.getWidth();
                                Log.d(TAG+ "tryOrientation","rotation: "+rotation+"   orientation: "+getScreenOrientation()+"  "+previewWidth+"   "+previewHeight);
                                sensorOrientation = rotation - getScreenOrientation();
                            }
                        },
                        this,
                        R.layout.camera_fragment,
                        new Size(640, 480));

        Log.v(TAG, "Inside setFragment fragment=camera2Fragment" );
        camera2Fragment.setCamera(cameraId);
        fragment = camera2Fragment;
        Log.v(TAG, "Inside setFragment executing getFragmentManager()");
        getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
        Log.v(TAG, "Inside setFragment exiting..." );
    }
    protected int getScreenOrientation() {
        Log.v(TAG, "Inside getScreenOrientation" );
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    //TODO getting frames of live camera footage and passing them to model
    @Override
    public void onImageAvailable(ImageReader reader) {
        Log.v(TAG, "Inside onImageAvailable" );
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            Log.v(TAG, "Inside onImageAvailable "+" previewWidth: "+previewWidth+ " previewHeight: "+previewHeight );
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
            Log.v(TAG, "Inside onImageAvailable rgbBytes" );
        }
        try {
            Log.v(TAG, "Inside onImageAvailable, try block" );
            final Image image = reader.acquireLatestImage();

            if (image == null) {
                Log.v(TAG, "Inside onImageAvailable image==null" );
                return;
            }

            if (isProcessingFrame) {
                Log.v(TAG, "Inside onImageAvailable isProcessing is true" );
                image.close();
                return;
            }
            isProcessingFrame = true;
            final Image.Plane[] planes = image.getPlanes();

            Log.v(TAG, "Inside onImageAvailable" + " fillBytes function is called" );
            fillBytes(planes, yuvBytes);
            Log.v(TAG, "Inside onImageAvailable,fillBytes function exiting..." );

            yRowStride = planes[0].getRowStride();
            Log.v(TAG, "Inside onImageAvailable" +" yRowStride: "+ yRowStride);
            final int uvRowStride = planes[1].getRowStride();
            Log.v(TAG, "Inside onImageAvailable uvRowStride: " +uvRowStride);
            final int uvPixelStride = planes[1].getPixelStride();
            Log.v(TAG, "Inside onImageAvailable uvPixelStride: " + uvPixelStride);

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            Log.v(TAG, "Inside onImageAvailable,imageConverter run ->ImageUtils.convertYUV420ToARGB8888 called" );
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                            Log.v(TAG, "Inside onImageAvailable,imageConverter run exiting..." );
                        }
                    };
            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            Log.v(TAG, "Inside onImageAvailable, PostInterferenceCallback" );
                            image.close();
                            isProcessingFrame = false;
                            Log.v(TAG, "Inside onImageAvailable, PostInterferenceCallBack exiting.." );
                        }
                    };

            Log.v(TAG, "Inside onImageAvailable processImage function called" );
            processImage();
            Log.v(TAG, "Inside onImageAvailable end of try block" );
        } catch (final Exception e) {
            Log.d("tryError",e.getMessage());
            return;
        }

    }

    private void processImage() {
        Log.v(TAG, "Inside processImage imageConverter.run" );
        imageConverter.run();

        Log.v(TAG, "Inside processImage rgbFrameBitmap initialized" );
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);

        Log.v(TAG, "Inside processImage rgbFrameBitmap.setPixels" );
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        ImageView imageView = findViewById(R.id.container2);
        Drawable d = new BitmapDrawable(rgbFrameBitmap);
        imageView.setImageDrawable(d);
        //Now to pass these frame as input buffer to the other device.
        //conversion of bitmap to Byte array
        Log.v(TAG, "Inside processImage" );
        byte[] input_byte_array =convertBitmapToByteArray(rgbFrameBitmap);


        Log.v(TAG, "Inside processImage" );
        postInferenceCallback.run();
        Log.v(TAG, "Inside processImage" );
    }

    public static byte[] convertBitmapToByteArray(Bitmap bitmap){
        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
        bitmap.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        return byteBuffer.array();
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        Log.v(TAG, "Inside fillBytes" );
        Log.v(TAG, "Inside fillBytes planes.length: " + planes.length);
        for (int i = 0; i < planes.length; ++i) {
            Log.v(TAG, "Inside fillBytes i="+i );
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
        Log.v(TAG, "Inside FillBytes, exiting function.." );
    }
}