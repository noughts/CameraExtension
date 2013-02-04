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

public class StartCamera implements FREFunction {

	public static final String KEY ="startCamera";

	private static final String TAG = "StartCameraFunction";
	
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		CameraSurfaceView surfaceView = null;
        FREByteArray info = (FREByteArray)args[4];
		
		try {
	        int index = args[0].getAsInt();
	        int width = args[1].getAsInt();
	        int height = args[2].getAsInt();
	        int fps = args[3].getAsInt();
	        int pictureQuality = args[5].getAsInt();
	
	        surfaceView = new CameraSurfaceView(context);
	        CaptureAndroidContext.setCameraSurface(surfaceView);
	        context.getActivity().setContentView(surfaceView);
	        
	        Log.d(TAG, "Starting... (" + width + ", " + height + ", " + fps + ", " + pictureQuality + ")");
	        surfaceView.startCamera(index, width, height, fps, pictureQuality);
	        Log.d(TAG, "Done.");
	        
	        info.acquire();
	        ByteBuffer bytes = info.getBytes();
	        bytes.order(ByteOrder.LITTLE_ENDIAN);
	
	        bytes.putInt(surfaceView.getFrameWidth());
	        bytes.putInt(surfaceView.getFrameHeight());
	        
	        info.release();
	    } catch(Exception e) {
	        Log.i(TAG, "Opening Device Error: " + e.toString());
	
	        if (surfaceView != null) {
	            surfaceView.endCamera();
		        CaptureAndroidContext.setCameraSurface(null);
	        }
	
	        try {
	            info.acquire();
	            ByteBuffer bytes = info.getBytes();
	            bytes.order(ByteOrder.LITTLE_ENDIAN);
	            bytes.putInt(-1);
	            info.release();
	        } catch(Exception e2) {
	            Log.i(TAG, "Write Result Error: " + e2.toString());
	        }
	    }
		return null;
	}

}
