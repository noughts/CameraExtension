package ru.inspirit.capture;

import android.hardware.Camera;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class GetFlashModeFunction implements FREFunction {

    private static final String TAG = "GetFlashModeFunction";
    public static final String KEY = "getFlashMode";
    
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		FREObject ret = null;
		
        try {
            CameraSurface cap = CaptureAndroidContext.cameraSurfaceHandler;
            if (cap != null) {
                String mode = cap.getFlashMode();
                int code = convertFlashModeToInt(mode);
                ret = FREObject.newObject(code);
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        
		return ret;
	}
	
	private static int convertFlashModeToInt(String mode) {
		int code = -1;
		if (mode.equals(Camera.Parameters.FLASH_MODE_OFF)) {
			code = CameraSurface.ANDROID_FLASH_MODE_OFF;
		} else if (mode.equals(Camera.Parameters.FLASH_MODE_ON)) {
			code = CameraSurface.ANDROID_FLASH_MODE_ON;
		} else if (mode.equals(Camera.Parameters.FLASH_MODE_AUTO)) {
			code = CameraSurface.ANDROID_FLASH_MODE_AUTO;
		} else if (mode.equals(Camera.Parameters.FLASH_MODE_RED_EYE)) {
			code = CameraSurface.ANDROID_FLASH_MODE_RED_EYE;
		} else if (mode.equals(Camera.Parameters.FLASH_MODE_TORCH)) {
			code = CameraSurface.ANDROID_FLASH_MODE_TORCH;
		}
		return code;
	}
}
