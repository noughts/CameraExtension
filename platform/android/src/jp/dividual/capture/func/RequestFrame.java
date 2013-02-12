package jp.dividual.capture.func;

import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;


import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import com.adobe.fre.FREBitmapData;

public class RequestFrame implements FREFunction {

    private static final String TAG = "RequestFrameFunction";
    
    public static final String KEY ="requestFrame";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {
        FREObject result = null;

        ByteBuffer bytes = null;
        int res = 0;

    	CameraSurfaceView cameraSurface = ((CaptureAndroidContext)context).getCameraSurface();

        if (cameraSurface != null && cameraSurface.isNewFrame()) {
            res = 1;
            try {
                    FREBitmapData bmp = (FREBitmapData)args[0];
                    bmp.acquire();

                    bytes = bmp.getBits();
                    bytes.order(ByteOrder.LITTLE_ENDIAN);
                    cameraSurface.grabFrame(bytes);

                    bmp.release();
            } catch (Exception e) {
                Log.i(TAG, "Error: " + e.toString());
            }
        }

        try {
            result = FREObject.newObject(res);
        } catch (Exception e) {
            Log.i(TAG, "Construct Result Object Error: " + e.toString());
        }

        return result;
    }

}
