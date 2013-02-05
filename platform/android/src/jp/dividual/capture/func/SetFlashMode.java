package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.hardware.Camera;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class SetFlashMode implements FREFunction {

    public static final String KEY = "setFlashMode";

    private static final String TAG = "SetFlashModeFunction";
    
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView cameraSurface = ctx.getCameraSurface();
            if (cameraSurface != null) {
            	if (args != null && 1 == args.length) {
                    int code = args[0].getAsInt();
                    String mode = convertFlashModeFromInt(code);
                    if (mode != null) {
                    	cameraSurface.setFlashMode(mode);
                    }
            	}
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        
		return null;
	}

	private static String convertFlashModeFromInt(int code) {
		String mode = Camera.Parameters.FLASH_MODE_AUTO;
		switch (code) {
			case CameraSurfaceView.FLASH_MODE_OFF:
				mode = Camera.Parameters.FLASH_MODE_OFF;
				break;
			case CameraSurfaceView.FLASH_MODE_ON:
				mode = Camera.Parameters.FLASH_MODE_ON;
				break;
			case CameraSurfaceView.FLASH_MODE_AUTO:
				mode = Camera.Parameters.FLASH_MODE_AUTO;
				break;
		}
		return mode;
	}
}
