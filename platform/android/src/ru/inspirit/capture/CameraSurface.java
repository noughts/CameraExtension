package ru.inspirit.capture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.nio.ByteBuffer;
import java.lang.Integer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.adobe.fre.FREContext;

public class CameraSurface extends SurfaceView implements SurfaceHolder.Callback, Runnable 
{

	private static final String TAG = "CameraSurface";
	private static final String EVENT_PICTURE_TAKEN = "CAM_SHOT";
	private static final String EVENT_IMAGE_SAVED = "IMAGE_SAVED";
	private static final String EVENT_FOCUS_COMPLETE = "FOCUS_COMPLETE";
	private static final String EVENT_PREVIEW_READY = "PREVIEW_READY";
	
	public static final int ANDROID_FLASH_MODE_NOT_SUPPORTED = -1;
	public static final int ANDROID_FLASH_MODE_OFF = 0;
	public static final int ANDROID_FLASH_MODE_ON = 1;
	public static final int ANDROID_FLASH_MODE_AUTO = 2;
	public static final int ANDROID_FLASH_MODE_RED_EYE = 3;
	public static final int ANDROID_FLASH_MODE_TORCH = 4;
	
    public static FREContext captureContext = null;

    private int _frameDataSize;
    private int _frameDataSize2;
    private int _rawFrameDataSize;

    private byte[] _RGBA;
    private byte[] _RGBA_R;
    private byte[] _RGBA_P2;
    private byte[] _frameData;
    private byte[] _callbackBuffer;

    private Camera              _camera;
    private int 				_cameraId;
    private boolean  			_facing;
    private SurfaceHolder       _holder;
    private int                 _frameWidth;
    private int                 _frameHeight;
    private int                 _frameWidth2;
    private int                 _frameHeight2;
    private int                 _pictureWidth;
    private int                 _pictureHeight;
    private boolean             _threadRun;
    private boolean	            _previewReady;
    
    public boolean              isNewFrame;
    public boolean              isCapturing;
    public Integer              previewFormat;
    public Integer              pictureFormat;

    //
    static {
        System.loadLibrary("color_convert");
    }
    private native static void convert(byte[] input, byte[] output, int width, int height, int format);
    private native static void setupConvert(int width, int height);
    private native static void disposeConvert();
    //
    
    public static void nativeSetupConvert(int width, int height) {
    	setupConvert(width, height);
    }
    
    public static void nativeConvert(byte[] input, byte[] output, int width, int height, int format) {
    	convert(input, output, width, height, format);
    }
    
    public static void nativeDisposeConvert() {
    	disposeConvert();
    }
    
	private Camera.ShutterCallback mShutterHandler = new Camera.ShutterCallback() {
		@Override
		public void onShutter() {
			AudioManager mgr = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
            mgr.playSoundEffect(AudioManager.FLAG_PLAY_SOUND);
		}
	};
	
	private Camera.PictureCallback mPictureHandler = new Camera.PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
	        //jpeg
	        Log.i(TAG, "on jpeg arrives");
	
	        // notify AIR
	        CameraSurface.captureContext.dispatchStatusEventAsync( EVENT_PICTURE_TAKEN, "0" );
	
	        // resume camera
	        camera.startPreview();
		}
	};
	
    public CameraSurface(Context context) 
    {
        super(context);

        _holder = getHolder();
        _holder.addCallback(this);
        _holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        isNewFrame = false;
        isCapturing = false;

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    public int getFrameWidth() {
        return _frameWidth;
    }

    public int getFrameHeight() {
        return _frameHeight;
    }

    public void startPreview()
    {
        if(!isCapturing)
        {
            _camera.startPreview();
            isCapturing = true;
        }
    }
    public void stopPreview()
    {
        if(isCapturing)
        {
            _camera.stopPreview();
            isCapturing = false;
        }
    }
    public void focusAtPoint(double x, double y)
    {
        _camera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                        	CameraSurface.captureContext.dispatchStatusEventAsync(EVENT_FOCUS_COMPLETE, "0");
                        }
                });
    }
    public void startTakePicture()
    {
        _camera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                        takePicture();
                }
        });
    }
    public void takePicture() {
        Log.i(TAG, "takePicture");
        //System.gc();
        _camera.takePicture(mShutterHandler, null, mPictureHandler);
    }

    public String getFlashMode() {
    	String ret = null;
    	if (_camera != null) {
    		Camera.Parameters params = _camera.getParameters();
    		ret = params.getFlashMode();
    	}
    	return ret;
    }
    
    public void setFlashMode(String flashMode) {
    	if (_camera != null) {
    		Camera.Parameters params = _camera.getParameters();
    		params.setFlashMode(flashMode);
    		_camera.setParameters(params);
    	}
    }

    private boolean mSaving = false;
    public String captureAndSaveImage(final String directoryName, int orientation) {
    	if (_camera != null && !mSaving) {
    		mSaving = true;
			// Save to local file
	        final File pictureFile = getOutputMediaFile(directoryName);
	        if (pictureFile == null){
	            Log.d(TAG, "Error creating media file, check storage permissions");
	            return null;
	        }
	        setCameraOrientation(orientation, _cameraId, _camera);
    		_camera.takePicture(mShutterHandler, null, new Camera.PictureCallback() {
				@Override
				public void onPictureTaken(byte[] data, Camera camera) {
					mSaving = false;
			        try {
			            FileOutputStream fos = new FileOutputStream(pictureFile);
			            fos.write(data);
			            fos.close();
			        } catch (FileNotFoundException e) {
			            Log.d(TAG, "File not found: " + e.getMessage());
			        } catch (IOException e) {
			            Log.d(TAG, "Error accessing file: " + e.getMessage());
			        }
			        CameraSurface.captureContext.dispatchStatusEventAsync( EVENT_IMAGE_SAVED, "0" );
			        // resume camera
			        camera.startPreview();
				}
			});
    		return pictureFile.getAbsolutePath();
    	} else {
    		return null;
    	}
    }
    
    protected void onPreviewStarted(int previewWidth, int previewHeight) 
    {
        _frameDataSize = previewWidth * previewHeight * 4;
        _RGBA = new byte[_frameDataSize + 4096]; // safe
        _RGBA_R = new byte[_frameDataSize + 4096];
        _frameWidth2 = nextPowerOfTwo(_frameWidth);
        _frameHeight2 = nextPowerOfTwo(_frameHeight);
        _frameDataSize2 = _frameWidth2 * _frameHeight2 * 4;
        _RGBA_P2 = new byte[_frameDataSize2];

        setupConvert(previewWidth, previewHeight);

        isCapturing = true;
    }

    protected void onPreviewStopped() 
    {
        _RGBA = null;
        _RGBA_R = null;
        _RGBA_P2 = null;
        isCapturing = false;

        disposeConvert();
    }

    // DEBUG: Save the first frame
    //private boolean isFirstFrame = true;

    protected void processFrame(byte[] data) 
    {
        if(previewFormat == ImageFormat.NV21) {
            convert(data, _RGBA, _frameWidth, _frameHeight, 0);
            
            // DEBUG Convert YUV to JPEG -> Successful
            /*
            if (isFirstFrame) {
            	YuvImage img = new YuvImage(data, ImageFormat.NV21, _frameWidth, _frameHeight, null);
            	File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), TAG);
            	// Create the storage directory if it does not exist
            	if (! mediaStorageDir.exists()){
            		if (! mediaStorageDir.mkdirs()){
            			Log.d(TAG, "failed to create directory");
            			return;
            		}
            	}
            	// Create a media file name
            	String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            	File pictureFile = new File(mediaStorageDir.getPath() + File.separator + "DEBUG_"+ timeStamp + ".jpg");
            	try {
            		FileOutputStream fos = new FileOutputStream(pictureFile);
            		img.compressToJpeg(new Rect(0, 0, _frameWidth, _frameHeight), 80, fos);
            		fos.close();
            	} catch (FileNotFoundException e) {
    			    Log.d(TAG, "File not found: " + e.getMessage());
    			} catch (IOException e) {
    			    Log.d(TAG, "Error accessing file: " + e.getMessage());
    			}
            	
        		isFirstFrame = false;
            }
            */
            /*
        	// DEBUG: Save the first frame -> Successful
            if (isFirstFrame) {
            	// Convert RGBA to ARGB
            	int[] argbArray = new int[_frameWidth * _frameHeight];
            	for (int j = 0; j < _frameHeight; j++) {
            		for (int i = 0; i < _frameWidth; i++) {
            			int index = j * _frameWidth + i;
            			byte r = _RGBA[index * 4];
            			byte g = _RGBA[index * 4 + 1];
            			byte b = _RGBA[index * 4 + 2];
            			byte a = _RGBA[index * 4 + 3];
            			int argb = a << 24 | r << 16 | g << 8 | b;
            			argbArray[index] = argb;
            		}
            	}
            	Bitmap bmp = Bitmap.createBitmap(_frameWidth, _frameHeight, Bitmap.Config.ARGB_8888);
            	bmp.setPixels(argbArray, 0, _frameWidth, 0, 0, _frameWidth, _frameHeight);
            	File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), TAG);
              
            	// Create the storage directory if it does not exist
            	if (! mediaStorageDir.exists()){
            		if (! mediaStorageDir.mkdirs()){
            			Log.d(TAG, "failed to create directory");
            			return;
            		}
            	}
            	// Create a media file name

            	String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            	File pictureFile = new File(mediaStorageDir.getPath() + File.separator + "ARGB_"+ timeStamp + ".jpg");
            	try {
            		FileOutputStream fos = new FileOutputStream(pictureFile);
                	bmp.compress(CompressFormat.JPEG, 80, fos);
            		fos.close();
    				} catch (FileNotFoundException e) {
    					Log.d(TAG, "File not found: " + e.getMessage());
    			} catch (IOException e) {
    			    Log.d(TAG, "Error accessing file: " + e.getMessage());
    			}
            	isFirstFrame = false;
            }
            */
        } 
        else if(previewFormat == ImageFormat.YV12) // doesnt work
        {
            convert(data, _RGBA, _frameWidth, _frameHeight, 1);
        }

        isNewFrame = true;

        debugShowFPS();
    }

    public void grabFrame(ByteBuffer bytes) {
    	// Rotate if front side
    	if (_facing) {
    		for (int j = 0; j < _frameHeight; j++) {
    			for (int i = 0; i < _frameWidth; i++) {
    				int sj = _frameHeight - j;
    				int si = _frameWidth - i;
    				int idx = (j * _frameWidth + i) * 4;
    				int srcIdx = (sj * _frameWidth + si) * 4;
    				System.arraycopy(_RGBA, srcIdx, _RGBA_R, idx, 4);
    			}
    		}
    		bytes.put(_RGBA_R, 0, _frameDataSize);
    	} else {
    		bytes.put(_RGBA, 0, _frameDataSize);
    	}
    }

    public void grabP2Frame(ByteBuffer bytes, int w2, int h2)
    {
        int off_x = (w2 - _frameWidth) >> 1;
        int off_y = (h2 - _frameHeight) >> 1;
        int p2stride = w2 * 4;
        int stride = _frameWidth * 4;

        int b_off_x, b_off_y, a_off_x, a_off_y;

        if(off_x < 0)
        {
            b_off_x = 0;
            a_off_x = -off_x;
        } else {
            b_off_x = off_x;
            a_off_x = 0;
        }

        if(off_y < 0)
        {
            b_off_y = 0;
            a_off_y = -off_y;
        } else {
            b_off_y = off_y;
            a_off_y = 0;
        }

        int nw = _frameWidth - a_off_x*2;
        int nh = _frameHeight - a_off_y*2;
        int new_stride = nw*4;

        int offset = b_off_y * p2stride + b_off_x*4;
        int src_pos = a_off_y * stride + a_off_x*4;

        for(int i = 0; i < nh; ++i)
        {
            System.arraycopy(_RGBA, src_pos, _RGBA_P2, offset, new_stride);
            offset += p2stride;
            src_pos += stride;
        }

        bytes.put(_RGBA_P2, 0, p2stride*h2);
    }

    public void grabRawFrame(ByteBuffer bytes)
    {
        bytes.put(_frameData, 0, _rawFrameDataSize);
    }

    public int getJpegDataSize()
    {
        return 0;
    }
    public void grabJpegFrame(ByteBuffer bytes)
    {
        bytes.put(null, 0, 0);
    }

	@SuppressLint("NewApi")
	public void setPreview() throws IOException 
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
        {
            _camera.setPreviewTexture( new SurfaceTexture(10));
        } else {
            _camera.setPreviewDisplay(null);
        }
    }

    public void setupCameraSurface(int width, int height, int fps, int pictureQuality) 
    {
        Log.i(TAG, "setupCameraSurface: " + width + "/" + height);
        if (_camera != null) 
        {
            Camera.Parameters params = _camera.getParameters();
            List<Camera.Size> sizes = params.getSupportedPreviewSizes();

            _frameWidth = width;
            _frameHeight = height;

            // selecting optimal camera preview size
            {
                int minDiff = Integer.MAX_VALUE;
                
                for (int i = 0; i < sizes.size(); i++) {
                	Camera.Size size = sizes.get(i);
                	Log.d(TAG, "[PREVIEW] Supported Size #" + (i + 1) + ": " + size.width + "x" + size.height);
                	int diff = Math.abs(size.height - height) + Math.abs(size.width - width);
                    if (diff < minDiff) {
                        _frameWidth = size.width;
                        _frameHeight = size.height;
                        minDiff = diff;
                    }
                }
            }

            params.setPreviewSize(_frameWidth, _frameHeight);

            // setup Picture size
            sizes = params.getSupportedPictureSizes();
            int cnt = sizes.size();
            if (pictureQuality == 0) {
            	int minWidth = Integer.MAX_VALUE;
            	int minHeight = Integer.MAX_VALUE;
            	for (int i = 0; i < cnt; i++) {
            		Camera.Size size = sizes.get(i);
                	Log.d(TAG, "[PICTURE] Supported Size #" + (i + 1) + ": " + size.width + "x" + size.height);
            		if (minWidth * minHeight > size.width * size.height) {
            			minWidth = size.width;
            			minHeight = size.height;
            		}
            	}
                _pictureWidth = minWidth;
                _pictureHeight = minHeight;
            } else if (pictureQuality == 1) {
                _pictureWidth = sizes.get(cnt>>1).width;
                _pictureHeight = sizes.get(cnt>>1).height;
            } else if(pictureQuality == 2) {
            	// CAUTION: Picture size list is not always ascending
            	int maxWidth = 0;
            	int maxHeight = 0;
            	for (int i = 0; i < cnt; i++) {
            		Camera.Size size = sizes.get(i);
                	Log.d(TAG, "[PICTURE] Supported Size #" + (i + 1) + ": " + size.width + "x" + size.height);
            		if (maxWidth * maxHeight < size.width * size.height) {
            			maxWidth = size.width;
            			maxHeight = size.height;
            		}
            	}
                _pictureWidth = maxWidth;
                _pictureHeight = maxHeight;
            }

            params.setPictureSize(_pictureWidth, _pictureHeight);

            List<String> FocusModes = params.getSupportedFocusModes();
            if (FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                Log.i(TAG, "setFocusMode: FOCUS_MODE_CONTINUOUS_VIDEO");
            }
            else if(FocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
            {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                Log.i(TAG, "setFocusMode: FOCUS_MODE_AUTO");
            }

            // set fps
            List<int[]> fps_ranges = params.getSupportedPreviewFpsRange();
            int des_fps = fps * 1000;
            int min_fps = 0;
            int max_fps = 0;
            // selecting optimal camera fps range
            {
                int minDiff = Integer.MAX_VALUE;
                for (int[] fps_range : fps_ranges) {
                    int dnf = fps_range[0] - des_fps;
                    int dxf = fps_range[1] - des_fps;
                    if (dnf*dnf + dxf*dxf < minDiff) {
                        min_fps = fps_range[0];
                        max_fps = fps_range[1];
                        minDiff = dnf*dnf + dxf*dxf;
                    }
                }
            }
            params.setPreviewFpsRange(min_fps, max_fps);
            Log.i(TAG, "setPreviewFpsRange: " + min_fps + "/" + max_fps);

            // preview format
            /*
            List<Integer> preview_fmt = params.getSupportedPreviewFormats();
            if(preview_fmt.contains(ImageFormat.YV12))
            {
                params.setPreviewFormat(ImageFormat.YV12);
                Log.i(TAG, "setPreviewFormat: YV12");
            } 
            else if(preview_fmt.contains(ImageFormat.NV21))
            {
                params.setPreviewFormat(ImageFormat.NV21);
                Log.i(TAG, "setPreviewFormat: NV21");
            }
            */

            // picture format
            List<Integer> pict_fmt = params.getSupportedPictureFormats();
            {
                for (Integer fmt : pict_fmt) {
                    Log.i(TAG, "picture fmt: " + fmt);
                }
            }
            if(pict_fmt.contains(ImageFormat.JPEG))
            {
                params.setPictureFormat(ImageFormat.JPEG);
                Log.i(TAG, "setPictureFormat: JPEG");
            } 

            params.setPreviewFormat(ImageFormat.NV21);
            // too much memory needed
            // + not possible on half of devices
            //params.setPictureFormat(ImageFormat.NV21);

            _camera.setParameters(params);

            previewFormat = params.getPreviewFormat();
            pictureFormat = params.getPictureFormat();

            Log.i(TAG, "selected previewFormat: " + previewFormat);
            Log.i(TAG, "selected pictureFormat: " + pictureFormat);
            Log.i(TAG, "selected preview size: " + _frameWidth + "x" + _frameHeight);
            Log.i(TAG, "selected picture size: " + _pictureWidth + "x" + _pictureHeight);

            /* Now allocate the buffer */
            _rawFrameDataSize = _frameWidth * _frameHeight;
            _rawFrameDataSize = _rawFrameDataSize * ImageFormat.getBitsPerPixel(previewFormat) / 8;
            _callbackBuffer = new byte[_rawFrameDataSize];
            /* The buffer where the current frame will be copied */
            _frameData = new byte [_rawFrameDataSize];
            _camera.addCallbackBuffer(_callbackBuffer);

            try {
                setPreview();
            } catch (IOException e) {
                Log.e(TAG, "setPreviewDisplay/setPreviewTexture fails: " + e);
            }

            /* Notify that the preview is about to be started and deliver preview size */
            onPreviewStarted(_frameWidth, _frameHeight);

            /* Now we can start a preview */
            _camera.startPreview();
        }
    }

    public void createCameraSurface(int index) 
    {
        Log.i(TAG, "createCameraSurface: " + index);

        if (_camera != null) 
        {
            _threadRun = false;
            synchronized (this) {
                _camera.stopPreview();
                _camera.setPreviewCallback(null);
                _camera.release();
                _camera = null;
            }
            onPreviewStopped();
        }
        
        synchronized (this) 
        {
        	_previewReady = false;
            _camera = Camera.open(index);
            _cameraId = index;

            // rotate if back side
        	Camera.CameraInfo info = new Camera.CameraInfo();
        	Camera.getCameraInfo(index, info);
        	int orientation = info.orientation;
        	_facing = (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
            if (_facing) {
                _camera.setDisplayOrientation(360 - orientation);
            } else {
            	_camera.setDisplayOrientation(orientation);
            }
            
            _camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                public void onPreviewFrame(byte[] data, Camera camera) {
                	if (_previewReady == false) {
                		_previewReady = true;
                		CameraSurface.captureContext.dispatchStatusEventAsync( EVENT_PREVIEW_READY, "0" );
                		Log.i(TAG, "Preview Ready");
                	}
                    synchronized (CameraSurface.this) {
                        System.arraycopy(data, 0, _frameData, 0, data.length);
                        CameraSurface.this.notify(); 
                    }
                    camera.addCallbackBuffer(_callbackBuffer);
                }
            });
        }
                    
        (new Thread(this)).start();
    }

    public void destroyCameraSurface() 
    {
        Log.i(TAG, "destroyCameraSurface");
        _threadRun = false;
        if (_camera != null) {
            synchronized (this) {
                _camera.stopPreview();
                _camera.setPreviewCallback(null);
                _camera.release();
                _camera = null;
            }
        }
        onPreviewStopped();
    }

    public void run() 
    {
        _threadRun = true;
        Log.i(TAG, "Starting processing thread");
        while (_threadRun) 
        {
            synchronized (this) {
                try {
                    this.wait();
                    processFrame(_frameData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static int frameCounter = 0;
    private static long lastFpsTime = System.currentTimeMillis();
    private static float camFps = 0;
    private void debugShowFPS()
    {
        frameCounter++;
        int delay = (int)(System.currentTimeMillis() - lastFpsTime);
        if (delay > 1000) 
        {
            camFps = (((float)frameCounter)/delay)*1000;
            frameCounter = 0;
            lastFpsTime = System.currentTimeMillis();
            Log.i(TAG, "### Camera FPS ### " + camFps + " FPS");  
        }
    }

    private static int nextPowerOfTwo(int v)
    {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    private static void setCameraOrientation(int orientation, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int degrees = 0;
        switch (orientation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            degrees = 360 - degrees;  // compensate the mirror
        }
        Camera.Parameters params = camera.getParameters();
        params.setRotation(degrees);
        camera.setParameters(params);
    }
    
    /** Create a File for saving an image */
    private static File getOutputMediaFile(String dirName){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                  Environment.DIRECTORY_PICTURES), dirName);
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory: " + dirName);
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg");
        return mediaFile;
    }
    
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) 
    {
        Log.i(TAG, "surfaceChanged");
    }
    public void surfaceCreated(SurfaceHolder holder) 
    {
        Log.i(TAG, "surfaceCreated");
    }
    public void surfaceDestroyed(SurfaceHolder holder) 
    {
        Log.i(TAG, "surfaceDestroyed");
        destroyCameraSurface();
    }
}
