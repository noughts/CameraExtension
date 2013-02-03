package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class CaptureAndSaveImage implements FREFunction {

    public static final String KEY = "captureAndSaveImage";

    private static final String TAG = "CaptureAndSaveImageFunction";
    
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		FREObject ret = null;
        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView cameraSurface = ctx.getCameraSurface();
            if(cameraSurface != null && 1 < args.length) {
                String dirName = args[1].getAsString();
                int orientation = args[2].getAsInt();
                String path = cameraSurface.captureAndSaveImage(dirName, orientation);
                ret = FREObject.newObject(path);
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }
        return ret;
	}

}
