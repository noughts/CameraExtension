package jp.dividual.capture;

import java.util.HashMap;
import java.util.Map;

import jp.dividual.capture.func.CaptureAndSaveImage;
import jp.dividual.capture.func.EndCamera;
import jp.dividual.capture.func.ExposureAtPoint;
import jp.dividual.capture.func.FlipCameraFunction;
import jp.dividual.capture.func.FocusAtPoint;
import jp.dividual.capture.func.GetFlashMode;
import jp.dividual.capture.func.GetLocationFunction;
import jp.dividual.capture.func.ListDevices;
import jp.dividual.capture.func.PutExifLocation;
import jp.dividual.capture.func.RequestFrame;
import jp.dividual.capture.func.SetFlashMode;
import jp.dividual.capture.func.StartCamera;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;

public class CaptureAndroidContext extends FREContext {
	private static final String TAG = "CaptureAndroidContext";
	private CameraSurfaceView mSurface = null;
	
	/*
	public CaptureAndroidContext(String extensionName ) { 
        this.takeOff();
    } 
    */
	

	public void setCameraSurface(CameraSurfaceView surface) {
		mSurface = surface;
	}
	
	public CameraSurfaceView getCameraSurface() {
		return mSurface;
	}
	
	public void takeOff(){
		Application app = this.getActivity().getApplication();
		Activity activity = this.getActivity();
		Log.i(TAG, "activity is " + activity.toString());	
		Log.i(TAG, "activity class name is " + activity.getClass().getName());	
					
		Context appContext = this.getActivity().getApplicationContext();			
		
		if(app == null){
			Log.i(TAG, "app is null");	
		}else{
			Log.i(TAG, "app is " + app.toString());	
			Log.i(TAG, "app class name is " + app.getClass().getName());	
		}
		
		
		
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
         
         Log.i(TAG, "after loadDefaultOptions");
         
        // Optionally, customize your config at runtime:
        //
        // options.inProduction = false;
        // options.developmentAppKey = "Your Development App Key";
        // options.developmentAppSecret "Your Development App Secret";
       
		 Log.i(TAG, "after loadDefaultOptions");

        UAirship.takeOff(app, options);
        Log.i(TAG, "UAirship took off");
	}
	
	@Override
	public void dispose() {
		if (mSurface != null) {
			mSurface.endCamera();
        }
	}

	@Override
	public Map<String, FREFunction> getFunctions() {
		Map<String, FREFunction> map = new HashMap<String, FREFunction>();
		map.put(StartCamera.KEY, new StartCamera());
		map.put(EndCamera.KEY, new EndCamera());
		map.put(RequestFrame.KEY, new RequestFrame());
		map.put(ListDevices.KEY, new ListDevices());
		
		map.put(FlipCameraFunction.KEY, new FlipCameraFunction());
		
		
		map.put(FocusAtPoint.KEY, new FocusAtPoint());
		
		
		map.put(ExposureAtPoint.KEY, new ExposureAtPoint());
		map.put(GetFlashMode.KEY, new GetFlashMode());
		map.put(SetFlashMode.KEY, new SetFlashMode());
		map.put(CaptureAndSaveImage.KEY, new CaptureAndSaveImage());
		
		//map.put(RegisterPushFunction.KEY, new RegisterPushFunction());
		map.put(GetLocationFunction.KEY, new GetLocationFunction());
		//map.put(PutExifLocation.KEY, new PutExifLocation());
				
		return map;
	}

}
