package jp.dividual.capture.func;

import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;


import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import com.adobe.fre.FREByteArray;
import com.adobe.fre.FREBitmapData;

public class RequestFrame implements FREFunction {

    private static final String TAG = "RequestFrameFunction";
    
    public static final String KEY ="requestFrame";
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {
        FREObject result = null;

        FREByteArray byteArray = null;
        ByteBuffer bytes = null;
        int res = 0;

        try {
            // read options
            byteArray = (FREByteArray)args[1];
            byteArray.acquire();

            bytes = byteArray.getBytes();
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            byteArray.release();
        } catch (Exception e0) {
            Log.i(TAG, "GET ARGS Error: " + e0.toString());

            try {
                result = FREObject.newObject( res );
            } catch(Exception e3) {
                Log.i(TAG, "Construct Result Object Error: " + e3.toString());
            }

            return result;
        }

    	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
    	CameraSurfaceView cameraSurface = ctx.getCameraSurface();

        if (cameraSurface.isNewFrame()) {
            res = 1;
            try {
                    FREBitmapData bmp = (FREBitmapData)args[2];
                    bmp.acquire();

                    bytes = bmp.getBits();
                    bytes.order(ByteOrder.LITTLE_ENDIAN);
                    cameraSurface.grabFrame(bytes);

                    bmp.release();
            } catch(Exception e) {
                Log.i(TAG, "Error: " + e.toString());
            }
        }

        try {
            result = FREObject.newObject( res );
        } catch(Exception e) {
            Log.i(TAG, "Construct Result Object Error: " + e.toString());
        }

        return result;
    }

}
