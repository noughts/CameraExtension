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
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;

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
	
	public void inspect(){
		
		Application app = this.getActivity().getApplication();
		Activity activity = this.getActivity();
		Log.i(TAG, "activity is " + activity.toString());	
		Log.i(TAG, "activity class name is " + activity.getClass().getName());	
				
		if(app == null){
			Log.i(TAG, "app is null");	
		}else{
			Log.i(TAG, "app is " + app.toString());	
			Log.i(TAG, "app class name is " + app.getClass().getName());	
		}
					
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
		map.put(PutExifLocation.KEY, new PutExifLocation());
		return map;
	}

}
