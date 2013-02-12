package jp.dividual.capture.func;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;

import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class FlipCameraFunction implements FREFunction {
    
    public static final String KEY = "flipCamera";

    private static final String TAG = "FlipCameraFunction";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {

    	FREByteArray info = (FREByteArray)args[0];
    	
        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView surfaceView = ctx.getCameraSurface();
            if (surfaceView != null) {
            	surfaceView.flipCamera();
            	
    	        info.acquire();
    	        ByteBuffer bytes = info.getBytes();
    	        bytes.order(ByteOrder.LITTLE_ENDIAN);
    	
    	        bytes.putInt(surfaceView.getFrameWidth());
    	        bytes.putInt(surfaceView.getFrameHeight());
    	        
    	        info.release();
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }

        return null;
    }
}
