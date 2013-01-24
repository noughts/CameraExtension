package ru.inspirit.capture;

import android.hardware.Camera;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class SetFlashModeFunction implements FREFunction {

    private static final String TAG = "SetFlashModeFunction";
    public static final String KEY = "setFlashMode";
    
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
        try {
            CameraSurface cap = CaptureAndroidContext.cameraSurfaceHandler;
            if (cap != null) {
            	if (args != null && 1 < args.length) {
                    int code = args[1].getAsInt();
                    String mode = convertFlashModeFromInt(code);
                    if (mode != null) {
                    	cap.setFlashMode(mode);
                    }
            	}
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        
		return null;
	}

	private static String convertFlashModeFromInt(int code) {
		String mode = null;
		switch (code) {
			case CameraSurface.ANDROID_FLASH_MODE_OFF:
				mode = Camera.Parameters.FLASH_MODE_OFF;
				break;
			case CameraSurface.ANDROID_FLASH_MODE_ON:
				mode = Camera.Parameters.FLASH_MODE_ON;
				break;
			case CameraSurface.ANDROID_FLASH_MODE_AUTO:
				mode = Camera.Parameters.FLASH_MODE_AUTO;
				break;
			case CameraSurface.ANDROID_FLASH_MODE_RED_EYE:
				mode = Camera.Parameters.FLASH_MODE_RED_EYE;
				break;
			case CameraSurface.ANDROID_FLASH_MODE_TORCH:
				mode = Camera.Parameters.FLASH_MODE_TORCH;
				break;
		}
		return mode;
	}
}
