package jp.dividual.capture;

import android.app.Application;
import android.location.Location;
import android.util.Log;


public class CaptureAndroidApplication extends Application {
	private static final String TAG = "CaptureAndroidApplication";
	public static final String PRIVATE_PREF = "CaptureAndroidPref";
	
	public Location currentLocation = null;
	
			
	@Override
	public void onCreate(){
		super.onCreate();
		
		Log.i(TAG, "Taking over app class!");
				
		
	}	
	
}
