package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class SetExposureCompensation implements FREFunction {

    public static final String KEY = "setExposureCompensation";

    private static final String TAG = "SetExposureCompensation";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {
    	 try {
    		 int exposureCompensation = args[0].getAsInt();
         	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
         	CameraSurfaceView surfaceView = ctx.getCameraSurface();
             if (surfaceView != null) {
             	surfaceView.setExposure(exposureCompensation);             	     	       
             }
         } catch(Exception e) {
             Log.i(TAG, "Error: " + e.toString());
         }
    	    	
		return null;
	}
}
