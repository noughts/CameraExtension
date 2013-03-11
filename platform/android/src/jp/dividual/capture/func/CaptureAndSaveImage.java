package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidApplication;
import jp.dividual.capture.CaptureAndroidContext;
import android.location.Location;
import android.media.ExifInterface;
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
		Log.d(TAG, "captureAndSaveImage called");
        try {
        	CaptureAndroidContext ctx = (CaptureAndroidContext)context;
        	CameraSurfaceView cameraSurface = ctx.getCameraSurface();
            if(cameraSurface != null && 1 < args.length) {
                String dirName = args[0].getAsString();
                int orientation = args[1].getAsInt();
                
                String path = cameraSurface.captureAndSaveImage(dirName, orientation);
                
                
                Log.d(TAG, "file path is" + path);
                CaptureAndroidApplication app = (CaptureAndroidApplication)context.getActivity().getApplication();
                if(app.currentLocation != null){
                	Log.d(TAG, "putting exif..");
					putExifLocation(path, app.currentLocation);	
				}else{
					Log.d(TAG, "canot put exif! No location object.");
				}
                
                readoutExifLocation(path);
                
                ret = FREObject.newObject(path);
            }else{
            	Log.d(TAG, "cameraSurface is null or args.length < 2");
            }
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
            e.printStackTrace();
        }
        return ret;
	}
	
	public void putExifLocation(String path, Location location){
		 //final String DUMMY_GPS_LATITUDE = "22/1,21/1,299295/32768";
		 //final String DUMMY_GPS_LATITUDE_REF = "N";
		 //final String DUMMY_GPS_LONGITUDE = "114/1,3/1,207045/4096";
		 //final String DUMMY_GPS_LONGITUDE_REF = "E";
		 
		try{
			ExifInterface exif = new ExifInterface(path);
	        //String latitudeStr = "90/1,12/1,30/1";
	        double lat = location.getLatitude();
	        double alat = Math.abs(lat);
	        String dms = Location.convert(alat, Location.FORMAT_SECONDS);
	        String[] splits = dms.split(":");
	        String[] secnds = (splits[2]).split("\\.");
	        String seconds;
	        if(secnds.length==0)
	        {
	            seconds = splits[2];
	        }
	        else
	        {
	            seconds = secnds[0];
	        }

	        String latitudeStr = splits[0] + "/1," + splits[1] + "/1," + seconds + "/1";
	        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitudeStr);
	        //exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, DUMMY_GPS_LATITUDE);

	        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat>0?"N":"S");
	        //exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, DUMMY_GPS_LATITUDE_REF);

	        double lon = location.getLongitude();
	        double alon = Math.abs(lon);


	        dms = Location.convert(alon, Location.FORMAT_SECONDS);
	        splits = dms.split(":");
	        secnds = (splits[2]).split("\\.");

	        if(secnds.length==0)
	        {
	            seconds = splits[2];
	        }
	        else
	        {
	            seconds = secnds[0];
	        }
	        String longitudeStr = splits[0] + "/1," + splits[1] + "/1," + seconds + "/1";


	        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitudeStr);
	        //exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, DUMMY_GPS_LONGITUDE);
	        
	        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lon>0?"E":"W");
	        //exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, DUMMY_GPS_LONGITUDE_REF);

	        exif.saveAttributes();
	        
	        Log.d(TAG, "put exif location done");
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void readoutExifLocation(String path){
		try{
			ExifInterface exif = new ExifInterface(path);
			String latString = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
			String lngString = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
	        
	        Log.i(TAG, "Read out Exif location lat = " + latString + " , lng = " + lngString);
		}catch(Exception e){
			e.printStackTrace();
		}
    }

}
