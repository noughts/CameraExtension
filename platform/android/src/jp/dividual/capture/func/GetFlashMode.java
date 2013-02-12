package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.hardware.Camera;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class GetFlashMode implements FREFunction {

    public static final String KEY = "getFlashMode";

    private static final String TAG = "GetFlashModeFunction";

	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		FREObject ret = null;
        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView cameraSurface = ctx.getCameraSurface();
            if (cameraSurface != null) {
                String mode = cameraSurface.getFlashMode();
                int code = convertFlashModeToInt(mode);
                ret = FREObject.newObject(code);
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        
		return ret;
	}
	
	private static int convertFlashModeToInt(String mode) {
		int code = CameraSurfaceView.FLASH_MODE_NOT_SUPPORTED;
		if (mode.equals(Camera.Parameters.FLASH_MODE_OFF)) {
			code = CameraSurfaceView.FLASH_MODE_OFF;
		} else if (mode.equals(Camera.Parameters.FLASH_MODE_ON)) {
			code = CameraSurfaceView.FLASH_MODE_ON;
		} else if (mode.equals(Camera.Parameters.FLASH_MODE_AUTO)) {
			code = CameraSurfaceView.FLASH_MODE_AUTO;
		}
		return code;
	}
}
