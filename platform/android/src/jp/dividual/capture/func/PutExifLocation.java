package jp.dividual.capture.func;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidApplication;
import jp.dividual.capture.CaptureAndroidContext;
import android.location.Location;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class PutExifLocation implements FREFunction {

    public static final String KEY = "focusAtPoint";

    private static final String TAG = "PutExifLocation";
    
	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		FREObject ret = null;
		Log.d(TAG, "putExifLocation called");
        try {
        	
                
        	File pictureFile = getOutputMediaFile("Blink");
        	String path = pictureFile.getAbsolutePath();
        	
                CaptureAndroidApplication app = (CaptureAndroidApplication)context.getActivity().getApplication();
                if(app.currentLocation != null){
                	Log.d(TAG, "putting exif..");
					putExifLocation(path, app.currentLocation);	
				}else{
					Log.d(TAG, "canot put exif! No location object.");
				}
                
                readoutExifLocation(path);
                
                ret = FREObject.newObject(path);
           
        } catch(Exception e) {
            Log.i(TAG, "Error: " + e.toString());
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
	
	/** Create a File for saving an image */
    private static File getOutputMediaFile(String dirName){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
 
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                  Environment.DIRECTORY_PICTURES), dirName);
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.
 
        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d(TAG, "failed to create directory: " + dirName);
                return null;
            }
        }
 
        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        timeStamp = "20130218_215838";
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_"+ timeStamp + ".jpg");
        return mediaFile;
    }

}