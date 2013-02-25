package jp.dividual.capture;

import android.app.Application;
import android.location.Location;
import android.util.Log;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;


public class CaptureAndroidApplication extends Application {
	private static final String TAG = "CaptureAndroidApplication";
	public static final String PRIVATE_PREF = "CaptureAndroidPref";
	
	public Location currentLocation = null;
	
	public static boolean isInForeground = false;
	
	
	@Override
	public void onCreate(){
		super.onCreate();
		
		//this.tracker = GoogleAnalyticsTracker.getInstance();
		//tracker.startNewSession("", 60, this);		
		
		//initializeTypefaces();
		
		Log.i(TAG, "Yeah!! Taking over app class!");
		

		
		
	}	
	
	private void takeOff(){

		Log.i(TAG, "before loadDefaultOptions");
		//AirshipConfigOptions options = AirshipConfigOptions.loadDefaultOptions(app);
		
		
		AirshipConfigOptions options = new AirshipConfigOptions();			
		Log.i(TAG, "after initiating AirshipConfigOptions");
		
		 options.inProduction = false;
         options.developmentAppKey = "a-h0LTMDQW6Ga2eigwryGA";
         options.developmentAppSecret = "z7a3cLd8Sw6nijrX4L0ovQ";
         options.transport = "gcm";
         options.iapEnabled = false;
         options.developmentLogLevel = 2;
         options.productionLogLevel = 6;
         
        // Optionally, customize your config at runtime:
        //
        // options.inProduction = false;
        // options.developmentAppKey = "Your Development App Key";
        // options.developmentAppSecret "Your Development App Secret";
       
		 Log.i(TAG, "after loadDefaultOptions");

        UAirship.takeOff(this, options);
        Log.i(TAG, "UAirship took off");
	}

}
