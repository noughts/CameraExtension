package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class ToggleCapturingFunction implements FREFunction {
    
    public static final String KEY = "toggleCapturing";

    private static final String TAG = "ToggleCapturingFunction";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {

        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView cameraSurface = ctx.getCameraSurface();
            if (cameraSurface != null) {
            	// TODO call flip method
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }

        return null;
    }
}
