package com.example.cameraframessample;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/** Utility class for manipulating images. */
public class ImageUtils {
    static final String TAG = "ImageUtils_CameraFrame";

    static final int kMaxChannelValue = 262143;

    /**Saves a Bitmap object to disk for analysis.
     *@param bitmap The bitmap to save.
     *@param filename The location to save the bitmap to.
     */
    public static void saveBitmap(final Bitmap bitmap, final String filename) {
        Log.v(TAG, "Inside saveBitmap" );
        final String root = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "tensorflow";
        final File myDir = new File(root);
        Log.v(TAG, "Inside saveBitmap" );
        Log.v(TAG, "Inside saveBitmap" );
        final String fname = filename;
        final File file = new File(myDir, fname);
        if (file.exists()) {
            file.delete();
        }
        try {
            Log.v(TAG, "Inside saveBitmap" );
            final FileOutputStream out = new FileOutputStream(file);
            Log.v(TAG, "Inside saveBitmap" );
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
            Log.v(TAG, "Inside saveBitmap" );
            out.flush();
            out.close();
            Log.v(TAG, "Inside saveBitmap" );
        } catch (final Exception e) {

        }
    }

    private static int YUV2RGB(int y, int u, int v) {
        y = Math.max((y - 16), 0);
        u -= 128;
        v -= 128;
        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);
        r = r > kMaxChannelValue ? kMaxChannelValue : (Math.max(r, 0));
        g = g > kMaxChannelValue ? kMaxChannelValue : (Math.max(g, 0));
        b = b > kMaxChannelValue ? kMaxChannelValue : (Math.max(b, 0));
        return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
    }

    public static void convertYUV420ToARGB8888(
            byte[] yData,
            byte[] uData,
            byte[] vData,
            int width,
            int height,
            int yRowStride,
            int uvRowStride,
            int uvPixelStride,
            int[] out) {
        Log.v(TAG, "Inside convertYUV420ToARGB8888" );
        int yp = 0;
        for (int j = 0; j < height; j++) {
            int pY = yRowStride * j;
            int pUV = uvRowStride * (j >> 1);

            for (int i = 0; i < width; i++) {
                int uv_offset = pUV + (i >> 1) * uvPixelStride;

                out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
            }
        }
        Log.v(TAG, "Inside convertYUV420ToARGB8888 exiting..." );
    }
}
