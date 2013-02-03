package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class EndCamera implements FREFunction {

	public static final String KEY ="endCamera";

	private static final String TAG = "endCameraFunction";
    
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {
        Log.i(TAG, "disposing extension");

    	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
    	CameraSurfaceView cameraSurface = ctx.getCameraSurface();
        if (cameraSurface != null) {
        	cameraSurface.endCamera();
	        ((CaptureAndroidContext)context).setCameraSurface(null);
        }
        return null;
    }

}
