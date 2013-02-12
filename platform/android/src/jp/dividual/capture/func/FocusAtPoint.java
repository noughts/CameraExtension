package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FocusAtPoint implements FREFunction {
	
    public static final String KEY = "focusAtPoint";

    private static final String TAG = "FocusAtPointFunction";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {

        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView cameraSurface = ctx.getCameraSurface();

            if (cameraSurface != null) {
                double x = args[0].getAsDouble();
                double y = args[1].getAsDouble();
                cameraSurface.focusAtPoint(x, y);
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        return null;
    }

}
