package jp.dividual.capture.func;

import android.util.Log;
import android.hardware.Camera;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.Charset;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import com.adobe.fre.FREByteArray;

public class ListDevices implements FREFunction {
    
    public static final String KEY = "listDevices";

    private static final String TAG = "ListCaptureDevicesFunction";
    
    private static Charset charset = Charset.forName("UTF-8");
    private static CharsetEncoder encoder = charset.newEncoder();
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {
        FREByteArray info;
        ByteBuffer bytes;

        try {
            info = (FREByteArray)args[1];
            info.acquire();
            bytes = info.getBytes();
            bytes.order(ByteOrder.LITTLE_ENDIAN);

            ByteBuffer str0 = str_to_bb("Back Camera");

            int num = Camera.getNumberOfCameras();

            bytes.putInt(num);

            bytes.putInt(11);
            bytes.put(str0);
            bytes.putInt(1);
            bytes.putInt(1);

            if (num > 1) {
                ByteBuffer str1 = str_to_bb("Front Camera");

                bytes.putInt(12);
                bytes.put(str1);
                bytes.putInt(1);
                bytes.putInt(1);
            }

            info.release();
        } catch (Exception e) {
            Log.i(TAG, "Error: " + e.toString());
        }

        return null;
    }

    private static ByteBuffer str_to_bb(String msg) {
        try {
            return encoder.encode(CharBuffer.wrap(msg));
        } catch (Exception e) {
        	e.printStackTrace();
        }
        return null;
    }

}
