package jp.dividual.capture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import ru.inspirit.capture.CameraSurface;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.adobe.fre.FREContext;

public class CameraSurfaceView extends SurfaceView implements
		SurfaceHolder.Callback {

	private static final String TAG = "CameraSurfaceView";

	public static final String VIEW_TAG = "SurfaceViewTag";

	/**
	 * Fired when a picture has been saved to the local storage
	 */
	public static final String EVENT_IMAGE_SAVED = "IMAGE_SAVED";

	/**
	 * Fired when auto-focus/exposure is complete
	 */
	public static final String EVENT_FOCUS_COMPLETE = "FOCUS_COMPLETE";

	/**
	 * Fired when camera preview starts
	 */
	public static final String EVENT_PREVIEW_READY = "PREVIEW_READY";

	public static final int FLASH_MODE_NOT_SUPPORTED = -1;
	public static final int FLASH_MODE_OFF = 0;
	public static final int FLASH_MODE_ON = 1;
	public static final int FLASH_MODE_AUTO = 2;

	private int mCameraId;
	private int mFrameWidth;
	private int mFrameHeight;
	private int mFPS;
	private int mPictureQuality;

	private byte[] mRGBAData;
	private byte[] mRGBARotateData;
	private int mRGBADataSize;
	private byte[] mYUVData;

	private Camera mCamera;
	private SurfaceHolder mHolder;
	private FREContext mContext;

	private boolean mFacing;
	private boolean mFirstFrame;
	private boolean mImageSaving;

	private boolean mNewFrame;
	private boolean isCapturing;

	private Camera.ShutterCallback mShutterHandler = new Camera.ShutterCallback() {
		@Override
		public void onShutter() {
			AudioManager mgr = (AudioManager) getContext().getSystemService(
					Context.AUDIO_SERVICE);
			mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
		}
	};

	private Camera.PreviewCallback mPreviewHandler = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			// Log.d(TAG, "Preview (" + data.length + " bytes)");

			if (mFirstFrame == true) {
				mFirstFrame = false;
				mContext.dispatchStatusEventAsync(EVENT_PREVIEW_READY, "0");
				Log.i(TAG, "Preview Ready");
			}
			processFrame(data);
			camera.addCallbackBuffer(mYUVData);
		}
	};

	public CameraSurfaceView(FREContext context) {
		super(context.getActivity());

		mContext = context;
		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		mNewFrame = false;
		isCapturing = false;

		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	public void startCamera(int cameraIndex, int width, int height, int fps,
			int pictureQuality) {

		if (mCamera != null) {
			endCamera();
		}

		try {
			mFirstFrame = true;
			mCameraId = cameraIndex;
			mFPS = fps;
			mPictureQuality = pictureQuality;

			mCamera = Camera.open(cameraIndex);
			setupPreviewSize(width, height);
			setupPictureSize(pictureQuality);
			setupFPS(fps);
			// setExposure(4);

			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(cameraIndex, info);
			mFacing = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);

			int bpp = ImageFormat.getBitsPerPixel(ImageFormat.NV21);
			int yuvSize = mFrameWidth * mFrameHeight * bpp / 8;
			if (mYUVData == null || mYUVData.length != yuvSize) {
				mYUVData = new byte[mFrameWidth * mFrameHeight * bpp / 8];
			}

			mRGBADataSize = mFrameWidth * mFrameHeight * 4;
			mRGBAData = new byte[mRGBADataSize];
			mRGBARotateData = new byte[mRGBADataSize];
			CameraSurface.nativeSetupConvert(mFrameWidth, mFrameHeight);

			mCamera.setPreviewDisplay(mHolder);
			mCamera.addCallbackBuffer(mYUVData);
			mCamera.setPreviewCallbackWithBuffer(mPreviewHandler);
			startPreview();
		} catch (IOException e) {
			Log.w(TAG, "Error on starting camera preview: " + e.getMessage());
		}
	}

	public void endCamera() {
		if (mCamera != null) {
			/*
			 * try { mCamera.setPreviewDisplay(null); } catch (IOException e) {
			 * Log.w(TAG, "Error on finishing camera preview: " +
			 * e.getMessage()); } finally { stopPreview();
			 * mCamera.setPreviewCallback(null); mCamera.release(); mCamera =
			 * null; }
			 */
			stopPreview();
			mCamera.setPreviewCallback(null);
			mCamera.release();
			mCamera = null;
		}

		mYUVData = null;
		mRGBAData = null;
		mRGBARotateData = null;

		// This call will cause an error on next startCamera()
		// CameraSurface.nativeDisposeConvert();
	}

	public void flipCamera() {
		int n = Camera.getNumberOfCameras();
		if (1 < n) {
			int id = (mCameraId + 1) % n;
			endCamera();
			startCamera(id, mFrameWidth, mFrameHeight, mFPS, mPictureQuality);
		}
	}

	public int getFrameWidth() {
		return mFrameWidth;
	}

	public int getFrameHeight() {
		return mFrameHeight;
	}

	public void startPreview() {
		if (!isCapturing) {
			mCamera.startPreview();
			isCapturing = true;
		}
	}

	public void stopPreview() {
		if (isCapturing) {
			mCamera.stopPreview();
			isCapturing = false;
		}
	}

	public void focusAtPoint(double x, double y) {
		mCamera.autoFocus(new Camera.AutoFocusCallback() {
			@Override
			public void onAutoFocus(boolean success, Camera camera) {
				mContext.dispatchStatusEventAsync(EVENT_FOCUS_COMPLETE, "0");
			}
		});
	}

	public boolean isNewFrame() {
		return mNewFrame;
	}

	public void grabFrame(ByteBuffer bytes) {
		// Rotate if front side
		// if (mFacing) {
		// for (int j = 0; j < mFrameHeight; j++) {
		// for (int i = 0; i < mFrameWidth; i++) {
		// int sj = mFrameHeight - j;
		// int si = mFrameWidth - i;
		// int idx = (j * mFrameWidth + i) * 4;
		// int srcIdx = (sj * mFrameWidth + si) * 4;
		// System.arraycopy(mRGBAData, srcIdx, mRGBARotateData, idx, 4);
		// }
		// }
		// bytes.put(mRGBARotateData, 0, mRGBADataSize);
		// }
		bytes.put(mRGBAData, 0, mRGBADataSize);
		mNewFrame = false;
	}

	public boolean isFlashSupported() {
		boolean ret = false;
		if (mCamera != null) {
			List<String> supportedFlashModes = mCamera.getParameters()
					.getSupportedFlashModes();
			if (supportedFlashModes == null) {
				ret = false;
			} else {
				ret = true;
			}
		}
		return ret;
	}

	public String getFlashMode() {
		String ret = null;
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			ret = params.getFlashMode();
		}
		return ret;
	}

	public void setFlashMode(String flashMode) {
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			if(flashMode == Camera.Parameters.FLASH_MODE_ON 
					|| flashMode == Camera.Parameters.FLASH_MODE_AUTO){
				List<String> supportedFlashModes = params.getSupportedFlashModes();
				boolean modeSupported = false;
				for(String name : supportedFlashModes){
					if(name.equals(flashMode)){
						params.setFlashMode(flashMode);
						modeSupported = true;
						break;
					}
				}
				if(modeSupported == false){
					params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);	
				}
				
			}else{
				params.setFlashMode(flashMode);	
			}
			
			
			mCamera.setParameters(params);
		}
	}

	public String captureAndSaveImage(final String directoryName,
			int orientation) {
		Log.d(TAG, "captureAndSaveImage");
		if (mCamera != null && !mImageSaving) {
			mImageSaving = true;
			// Save to local file
			final File pictureFile = getOutputMediaFile(directoryName);
			if (pictureFile == null) {
				Log.d(TAG,
						"Error creating media file, check storage permissions");
				return null;
			}
			setCameraOrientation(orientation);
			// Take picture after auto-focus
			mCamera.autoFocus(new Camera.AutoFocusCallback() {
				@Override
				public void onAutoFocus(boolean success, Camera camera) {
					mCamera.takePicture(mShutterHandler, null,
							new Camera.PictureCallback() {
								@Override
								public void onPictureTaken(byte[] data,
										Camera camera) {
									mImageSaving = false;
									try {
										FileOutputStream fos = new FileOutputStream(
												pictureFile);
										fos.write(data);
										fos.close();
									} catch (FileNotFoundException e) {
										Log.d(TAG,
												"File not found: "
														+ e.getMessage());
									} catch (IOException e) {
										Log.d(TAG,
												"Error accessing file: "
														+ e.getMessage());
									}
									mContext.dispatchStatusEventAsync(
											EVENT_IMAGE_SAVED,
											pictureFile.getAbsolutePath());
									// resume camera
									mCamera.startPreview();
								}
							});
				}
			});
			return pictureFile.getAbsolutePath();
		} else {
			return null;
		}
	}

	protected void processFrame(byte[] data) {
		CameraSurface.nativeConvert(data, mRGBAData, mFrameWidth, mFrameHeight,
				0);
		mNewFrame = true;
		debugShowFPS();
	}

	private static int frameCounter = 0;
	private static long lastFpsTime = System.currentTimeMillis();
	private static float camFps = 0;

	private void debugShowFPS() {
		frameCounter++;
		int delay = (int) (System.currentTimeMillis() - lastFpsTime);
		if (delay > 1000) {
			camFps = (((float) frameCounter) / delay) * 1000;
			frameCounter = 0;
			lastFpsTime = System.currentTimeMillis();
			Log.i(TAG, "### Camera FPS ### " + camFps + " FPS");
		}
	}

	private void setCameraOrientation(int orientation) {
		if (mCamera != null) {
			Camera.CameraInfo info = new Camera.CameraInfo();
			Camera.getCameraInfo(mCameraId, info);
			int degrees = 0;
			switch (orientation) {
			case Surface.ROTATION_0:
				degrees = 90;
				break;
			case Surface.ROTATION_90:
				degrees = 180;
				break;
			case Surface.ROTATION_180:
				degrees = 270;
				break;
			case Surface.ROTATION_270:
				degrees = 0;
				break;
			}
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				degrees = 360 - degrees; // compensate the mirror
				if (degrees == 360) {
					Log.d(TAG, "Camera facing front, and degree is 360");
					degrees = 0;
				}
			} else {
				if (degrees == 360) {
					Log.d(TAG, "Camera facing rear, but degrees is 360");
					degrees = 0;
				}
			}
			Camera.Parameters params = mCamera.getParameters();
			params.setRotation(degrees);
			mCamera.setParameters(params);

			Log.d(TAG, "orientation set to " + degrees);
		}
	}

	/** Create a File for saving an image */
	private static File getOutputMediaFile(String dirName) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				dirName);
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d(TAG, "failed to create directory: " + dirName);
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
		File mediaFile = new File(mediaStorageDir.getPath() + File.separator
				+ "IMG_" + timeStamp + ".jpg");
		return mediaFile;
	}

	private void setupPreviewSize(int width, int height) {
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			List<Camera.Size> sizes = params.getSupportedPreviewSizes();
			int previewWidth = width;
			int previewHeight = height;

			int minDiff = Integer.MAX_VALUE;

			for (int i = 0; i < sizes.size(); i++) {
				Camera.Size size = sizes.get(i);
				Log.d(TAG, "[PREVIEW] Supported Size #" + (i + 1) + ": "
						+ size.width + "x" + size.height);
				int diff = Math.abs(size.height - height)
						+ Math.abs(size.width - width);
				if (diff < minDiff) {
					previewWidth = size.width;
					previewHeight = size.height;
					minDiff = diff;
				}
			}
			params.setPreviewSize(previewWidth, previewHeight);
			mCamera.setParameters(params);
			mFrameWidth = previewWidth;
			mFrameHeight = previewHeight;
			Log.i(TAG, "Selected preview size: " + previewWidth + "x"
					+ previewHeight);
		}
	}

	private void setupPictureSize(int pictureQuality) {
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			List<Camera.Size> sizes = params.getSupportedPreviewSizes();
			sizes = params.getSupportedPictureSizes();
			int cnt = sizes.size();

			int pictureWidth = 0;
			int pictureHeight = 0;

			if (pictureQuality == 0) {
				int minWidth = Integer.MAX_VALUE;
				int minHeight = Integer.MAX_VALUE;
				for (int i = 0; i < cnt; i++) {
					Camera.Size size = sizes.get(i);
					Log.d(TAG, "[PICTURE] Supported Size #" + (i + 1) + ": "
							+ size.width + "x" + size.height);
					if (minWidth * minHeight > size.width * size.height) {
						minWidth = size.width;
						minHeight = size.height;
					}
				}
				pictureWidth = minWidth;
				pictureHeight = minHeight;
			} else if (pictureQuality == 1) {
				pictureWidth = sizes.get(cnt >> 1).width;
				pictureHeight = sizes.get(cnt >> 1).height;
			} else if (pictureQuality == 2) {
				// CAUTION: Picture size list is not always ascending
				int maxWidth = 0;
				int maxHeight = 0;
				for (int i = 0; i < cnt; i++) {
					Camera.Size size = sizes.get(i);
					Log.d(TAG, "[PICTURE] Supported Size #" + (i + 1) + ": "
							+ size.width + "x" + size.height);
					if (maxWidth * maxHeight < size.width * size.height) {
						maxWidth = size.width;
						maxHeight = size.height;
					}
				}
				pictureWidth = maxWidth;
				pictureHeight = maxHeight;
			}

			params.setPictureSize(pictureWidth, pictureHeight);
			mCamera.setParameters(params);

			Log.i(TAG, "Selected picture size: " + pictureWidth + "x"
					+ pictureHeight);
		}
	}

	private void setupFPS(int fps) {
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			List<int[]> fps_ranges = params.getSupportedPreviewFpsRange();
			int des_fps = fps * 1000;
			int minFPS = 0;
			int maxFPS = 0;

			// selecting optimal camera fps range
			int minDiff = Integer.MAX_VALUE;
			for (int[] fps_range : fps_ranges) {
				int dnf = fps_range[0] - des_fps;
				int dxf = fps_range[1] - des_fps;
				if (dnf * dnf + dxf * dxf < minDiff) {
					minFPS = fps_range[0];
					maxFPS = fps_range[1];
					minDiff = dnf * dnf + dxf * dxf;
				}
			}
			params.setPreviewFpsRange(minFPS, maxFPS);
			Log.i(TAG, "setPreviewFpsRange: " + minFPS + "/" + maxFPS);
		}
	}

	public int getExposure() {
		int ret = 0;
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();
			ret = params.getExposureCompensation();
			Log.d(TAG, "CurrentExposureCompensation = " + ret);
		}
		return ret;
	}

	public void setExposure(int exposureCompensation) {
		if (mCamera != null) {
			Camera.Parameters params = mCamera.getParameters();

			int min = params.getMinExposureCompensation();
			int max = params.getMaxExposureCompensation();
			int current = params.getExposureCompensation();
			Log.d(TAG, "MinExposureCompensation = " + min);
			Log.d(TAG, "MaxExposureCompensation = " + max);
			Log.d(TAG, "CurrentExposureCompensation = " + current);
			Log.d(TAG,
					"ExposureCompensationStep = "
							+ params.getExposureCompensationStep());

			if (exposureCompensation > max) {
				current = max;
			} else if (exposureCompensation < min) {
				current = min;
			} else {
				current = exposureCompensation;
			}

			params.setExposureCompensation(current);
			mCamera.setParameters(params);

		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i(TAG, "surfaceCreated");
		mHolder = holder;
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		Log.i(TAG, "surfaceChanged");
		try {
			mCamera.setPreviewDisplay(mHolder);
			mCamera.addCallbackBuffer(mYUVData);
			mCamera.setPreviewCallbackWithBuffer(mPreviewHandler);
			startPreview();
		} catch (Exception e) {

		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i(TAG, "surfaceDestroyed");
	}
}