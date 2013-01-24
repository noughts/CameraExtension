package ru.inspirit.capture;

import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class CaptureAndSaveImageFunction implements FREFunction {

    private static final String TAG = "CaptureAndSaveImageFunction";
    
    public static final String KEY = "captureAndSaveImage";
    
    
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		FREObject ret = null;
        try {
            CameraSurface capt = CaptureAndroidContext.cameraSurfaceHandler;
            if(capt != null && 1 < args.length) {
                String dirName = args[1].getAsString();
                int orientation = args[2].getAsInt();
                String path = capt.captureAndSaveImage(dirName, orientation);
                ret = FREObject.newObject(path);
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        
        return ret;
	}

}
