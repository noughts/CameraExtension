package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class GetExposureCompensation implements FREFunction {

    public static final String KEY = "getExposureCompensation";

    private static final String TAG = "GetExposureCompensation";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {
    	FREObject ret = null;
    	 try {
    		 
         	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
         	CameraSurfaceView surfaceView = ctx.getCameraSurface();
             if (surfaceView != null) {
             	int currentExposureCompensation = surfaceView.getExposure(); 
            	 ret = FREObject.newObject(currentExposureCompensation);
             }
         } catch(Exception e) {
             Log.i(TAG, "Error: " + e.toString());
         }
    	    	
		return ret;
	}
}
