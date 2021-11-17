package com.example.cameraframessample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * Camera Connection Fragment that captures images from camera.
 *
 * <p>Instantiated by newInstance.</p>
 */
@SuppressLint("ValidFragment")
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
@SuppressWarnings("FragmentNotInstantiable")
public class CameraConnectionFragment extends Fragment {
    static final String TAG = "CameraConnectionFragment_CameraFrame";

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private static final int MINIMUM_PREVIEW_SIZE = 320;

    /** Conversion from screen rotation to JPEG orientation. */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
    private final Semaphore cameraOpenCloseLock = new Semaphore(1);
    /** A {@link OnImageAvailableListener} to receive frames as they are available. */
    private final OnImageAvailableListener imageListener;
    /** The input size in pixels desired by TensorFlow (width and height of a square bitmap). */
    private final Size inputSize;
    /** The layout identifier to inflate for this Fragment. */
    private final int layout;
    //
    private ImageReader previewReader;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;

    //
    private String cameraId;
    private AutoFitTextureView textureView;
    private CameraCaptureSession captureSession;
    private CameraDevice cameraDevice;
    private Integer sensorOrientation;
    private Size previewSize;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    //
    private final ConnectionCallback cameraConnectionCallback;

    public static CameraConnectionFragment newInstance(
            final ConnectionCallback callback,
            final OnImageAvailableListener imageListener,
            final int layout,
            final Size inputSize) {
        Log.v(TAG, "Inside CameraConnectionFragment newInstance" );
        return new CameraConnectionFragment(callback, imageListener, layout, inputSize);
    }
    @SuppressLint("ValidFragment")
    private CameraConnectionFragment(
            final ConnectionCallback connectionCallback,
            final OnImageAvailableListener imageListener,
            final int layout,
            final Size inputSize) {
        Log.v(TAG, "Inside CameraConnectionFragment constructor" );
        this.cameraConnectionCallback = connectionCallback;
        this.imageListener = imageListener;
        this.layout = layout;
        this.inputSize = inputSize;
        Log.v(TAG, "Inside CameraConnectionFragment constructor exiting..." );
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureProgressed(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final CaptureResult partialResult) {
                    Log.v(TAG, "Inside CameraCaptureSession.CaptureCallback,onCaptureProgressed" );
                }
                @Override
                public void onCaptureCompleted(
                        final CameraCaptureSession session,
                        final CaptureRequest request,
                        final TotalCaptureResult result) {
                    Log.v(TAG, "Inside CameraCaptureSession.CaptureCallback,onCaptureCompleted" );
                }
            };


    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    Log.v(TAG, "Inside surfaceTextureListener, onSurfaceTextureAvailable width:"+ width+" height: "+height );
                    Log.v(TAG, "Inside surfaceTextureListener, onSurfaceTextureAvailable OpenCamera function called" );
                    openCamera(width, height);
                    Log.v(TAG, "Inside surfaceTextureListener, onSurfaceTextureAvailable exiting..." );
                }
                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                    Log.v(TAG, "Inside surfaceTextureListener, onSurfaceTextureSizeChanged width:"+ width+" height: "+height );
                    Log.v(TAG, "Inside surfaceTextureListener, onSurfaceTextureSizeChanged configureTransform function called" );
                    configureTransform(width, height);
                    Log.v(TAG, "Inside surfaceTextureListener, onSurfaceTextureSizeChanged exiting..." );
                }
                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    Log.v(TAG, "Inside surfaceTextureListener,onSurfaceTextureDestroyed" );
                    return true;
                }
                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {
                @Override
                public void onOpened(final CameraDevice cd) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    Log.v(TAG, "Inside CameraDevice.StateCallback,onOpened" );

                    Log.v(TAG, "Inside CameraDevice.StateCallback,onOpened cameraOpenCloseLock.release" );
                    cameraOpenCloseLock.release();
                    cameraDevice = cd;

                    Log.v(TAG, "Inside CameraDevice.StateCallback,onOpened createCameraPreviewSession" );
                    createCameraPreviewSession();
                    Log.v(TAG, "Inside CameraDevice.StateCallback,onOpened exiting.." );
                }

                @Override
                public void onDisconnected(final CameraDevice cd) {
                    Log.v(TAG, "Inside CameraDevice.StateCallback,onDisconnected" );
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(final CameraDevice cd, final int error) {
                    Log.v(TAG, "Inside CameraDevice.StateCallback, onError" );
                    cameraOpenCloseLock.release();
                    cd.close();
                    cameraDevice = null;
                    final Activity activity = getActivity();
                    if (null != activity) {
                        activity.finish();
                    }
                }
            };


    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width The minimum desired width
     * @param height The minimum desired height
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
        Log.v(TAG, "Inside chooseOptimalSize" );
        final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
        final Size desiredSize = new Size(width, height);

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        if (exactSizeFound) {
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            // LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            // LOGGER.e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        Log.v(TAG, "Inside onCreateView" );
        return inflater.inflate(layout, container, false);
    }

    @Override
    public void onViewCreated(final View view, final Bundle savedInstanceState) {
        Log.v(TAG, "Inside onViewCreated" );
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        Log.v(TAG, "Inside onActivityCreated" );
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            Log.v(TAG, "Inside onResume, if part then calling openCamera" );
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            Log.v(TAG, "Inside onResume calling setSurfaceTextureListener" );
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public void setCamera(String cameraId) {
        this.cameraId = cameraId;
    }

    /** Sets up member variables related to camera. */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setUpCameraOutputs() {
        Log.v(TAG, "Inside SetUpCameraOutputs" );
        final Activity activity = getActivity();
        Log.v(TAG, "Inside SetUpCameraOutputs CameraManager" );
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.v(TAG, "Inside SetUpCameraOutputs,try CameraCharacteristics" );
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Log.v(TAG, "Inside SetUpCameraOutputs StreamConfigurationMap" );
            final StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Log.v(TAG, "Inside SetUpCameraOutputs sensorOrientation" );
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            Log.v(TAG, "Inside SetUpCameraOutputs previewSize" );
            previewSize =
                    chooseOptimalSize(
                            map.getOutputSizes(SurfaceTexture.class),
                            inputSize.getWidth(),
                            inputSize.getHeight());

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            Log.v(TAG, "Inside SetUpCameraOutputs orientation" );
            final int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Log.v(TAG, "Inside SetUpCameraOutputs orientation==landscape" );
                textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                Log.v(TAG, "Inside SetUpCameraOutputs orientation!=landscape" );
                textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        } catch (final CameraAccessException e) {
            //  LOGGER.e(e, "Exception!");
        } catch (final NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance("getString(R.string.tfe_ic_camera_error)")
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            throw new IllegalStateException("getString(R.string.tfe_ic_camera_error)");
        }
        Log.v(TAG, "Inside SetUpCameraOutputs cameraConnectionCallback.onPreviewSizeChosen" );
        cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @SuppressLint("MissingPermission")
    private void openCamera(final int width, final int height) {
        Log.v(TAG, "Inside openCamera" );
        Log.v(TAG, "Inside openCamera width: "+ width + ", height: "+height );
        Log.v(TAG, "Inside openCamera setUpCameraOutputs function called" );
        setUpCameraOutputs();
        Log.v(TAG, "Inside openCamera configureTransform function called" );
        configureTransform(width, height);
        Log.v(TAG, "Inside openCamera getActivity called" );
        final Activity activity = getActivity();
        Log.v(TAG, "setting cameraManager" );
        final CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            Log.v(TAG, "Inside openCamera manager.openCamera" );
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (final CameraAccessException e) {
            // LOGGER.e(e, "Exception!");
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /** Closes the current {@link CameraDevice}. */
    private void closeCamera() {
        try {
            Log.v(TAG, "Inside closeCamera" );
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                Log.v(TAG, "Inside closeCamera null!=captureSession" );
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                Log.v(TAG, "Inside closeCamera null!=cameraDevice" );
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                Log.v(TAG, "Inside closeCamera null!=previewReader" );
                previewReader.close();
                previewReader = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            Log.v(TAG, "Inside closeCamera cameraOpenCloseLock.release" );
            cameraOpenCloseLock.release();
        }
    }

    /** Starts a background thread and its {@link Handler}. */
    private void startBackgroundThread() {
        Log.v(TAG, "Inside StartBackgroundThread" );
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    /** Stops the background thread and its {@link Handler}. */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void stopBackgroundThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Log.v(TAG, "Inside stopBackgroundThread" );
            backgroundThread.quitSafely();

            try {
                Log.v(TAG, "Inside stopBackgroundThread try block" );
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (final InterruptedException e) {
                //    LOGGER.e(e, "Exception!");
            }
        }
    }

    /** Creates a new {@link CameraCaptureSession} for camera preview. */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createCameraPreviewSession() {
        try {
            Log.v(TAG, "Inside createCameraPreviewSession" );
            Log.v(TAG, "Inside createCameraPreviewSession SurfaceTexture is set" );
            final SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            Log.v(TAG, "Inside createCameraPreviewSession texture.setDefaultBufferSize" );
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Log.v(TAG, "Inside createCameraPreviewSession width: "+previewSize.getWidth()+ " height: "+previewSize.getHeight() );
            // This is the output Surface we need to start preview.

            Log.v(TAG, "Inside createCameraPreviewSession surface set" );
            final Surface surface = new Surface(texture);

            Log.v(TAG, "Inside createCameraPreviewSession previewRequestBuilder" );
            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            Log.v(TAG, "Inside createCameraPreviewSession added target surface" );
            // LOGGER.i("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight());

            // Create the reader for the preview frames.
            Log.v(TAG, "Inside createCameraPreviewSession previewReader" );
            previewReader =
                    ImageReader.newInstance(
                            previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            Log.v(TAG, "Inside createCameraPreviewSession previewReader.setOnImageAvailableListener" );
            previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
            Log.v(TAG, "Inside createCameraPreviewSession  previewRequestBuilder.addTarget" );
            previewRequestBuilder.addTarget(previewReader.getSurface());
            Log.v(TAG, "Inside createCameraPreviewSession cameraDevice.createCaptureSession" );

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            Log.v(TAG, "Inside createCameraPreviewSession,cameraDevice.createCaptureSession onConfigured" );
                            if (null == cameraDevice) {
                                return;
                            }
                            Log.v(TAG, "Inside createCameraPreviewSession,cameraDevice.createCaptureSession onConfigured captureSession" );
                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                Log.v(TAG, "Inside createCameraPreviewSession,cameraDevice.createCaptureSession onConfigured previewRequestBuilder.set" );
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                Log.v(TAG, "Inside createCameraPreviewSession,cameraDevice.createCaptureSession onConfigured previewRequestBuilder.set" );
                                previewRequestBuilder.set(
                                        CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                Log.v(TAG, "Inside createCameraPreviewSession,cameraDevice.createCaptureSession onConfigured previewRequest" );

                                previewRequest = previewRequestBuilder.build();
                                Log.v(TAG, "Inside createCameraPreviewSession,cameraDevice.createCaptureSession onConfigured captureSession" );
                                captureSession.setRepeatingRequest(
                                        previewRequest, captureCallback, backgroundHandler);
                            } catch (final CameraAccessException e) {
                                //       LOGGER.e(e, "Exception!");
                            }
                        }

                        @Override
                        public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    },
                    null);
        } catch (final CameraAccessException e) {
            //        LOGGER.e(e, "Exception!");
        }
    }

    /**Configures the necessary {@link Matrix} transformation to `mTextureView`. This method should be
     * called after the camera preview size is determined in setUpCameraOutputs and also the size of
     * `mTextureView` is fixed.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(final int viewWidth, final int viewHeight) {
        Log.v(TAG, "Inside configureTransform" );
        final Activity activity = getActivity();
        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
        Log.v(TAG, "Inside configureTransform rotation" );
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        Log.v(TAG, "Inside configureTransform other parameters" );
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            Log.v(TAG, "Inside configureTransform surface rotation 90 || 270" );
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale =
                    Math.max(
                            (float) viewHeight / previewSize.getHeight(),
                            (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            Log.v(TAG, "Inside configureTransform surface rotation 180" );
            matrix.postRotate(180, centerX, centerY);
        }
        Log.v(TAG, "Inside configureTransform textureView.setTransform" );
        textureView.setTransform(matrix);
    }

    /**
     * Callback for Activities to use to initialize their data once the selected preview size is
     * known.
     */
    public interface ConnectionCallback {

        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    /** Compares two {@code Size}s based on their areas. */
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(final Size lhs, final Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            Log.v(TAG, "Inside CompareSizesbyArea lhs: " +lhs+" rhs:" +rhs);
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    /** Shows an error message dialog. */
    public static class ErrorDialog extends DialogFragment {
        private static final String ARG_MESSAGE = "message";
        public static ErrorDialog newInstance(final String message) {
            Log.v(TAG, "Inside ErrorDialog" );
            final ErrorDialog dialog = new ErrorDialog();
            final Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            Log.v(TAG, "Inside onCreateDialog" );
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(
                            android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface dialogInterface, final int i) {
                                    Log.v(TAG, "Inside onCreateDialog" );
                                    activity.finish();
                                }
                            })
                    .create();
        }
    }
}
