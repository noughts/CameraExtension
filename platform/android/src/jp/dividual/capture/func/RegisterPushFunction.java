package jp.dividual.capture.func;


import jp.dividual.capture.IntentReceiver;
import jp.dividual.capture.Resources;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.CustomPushNotificationBuilder;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushPreferences;

public class RegisterPushFunction implements FREFunction {

	public static final String KEY = "registerPush";

	private static final String TAG = "RegisterPushFunction";

	

	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		Log.i(TAG, "registerPush called");
		
		FREObject ret = null;
		try {
			
		
	        
	        Logger.logLevel = Log.VERBOSE;

	        //use CustomPushNotificationBuilder to specify a custom layout
	        CustomPushNotificationBuilder nb = new CustomPushNotificationBuilder();

	        nb.statusBarIconDrawableId = Resources.getResourseIdByName(context.getActivity().getPackageName(), "drawable", "icon_small");
	        
	        nb.layout = Resources.getResourseIdByName(context.getActivity().getPackageName(), "layout", "notification");
	        nb.layoutIconDrawableId = Resources.getResourseIdByName(context.getActivity().getPackageName(), "drawable", "icon"); 
	        nb.layoutIconId = Resources.getResourseIdByName(context.getActivity().getPackageName(), "id", "icon"); 
	        nb.layoutSubjectId =  Resources.getResourseIdByName(context.getActivity().getPackageName(), "id", "subject"); 
	        nb.layoutMessageId = Resources.getResourseIdByName(context.getActivity().getPackageName(), "id", "message"); 
	       
	        // customize the sound played when a push is received
	        //nb.soundUri = Uri.parse("android.resource://"+this.getPackageName()+"/" +R.raw.cat);

	        PushManager.shared().setNotificationBuilder(nb);
	        PushManager.shared().setIntentReceiver(IntentReceiver.class);
			
			

			// args[0]:sound, args[1]:vibration
			boolean soundEnabled = args[0].getAsBool();
			boolean vibEnabled = args[1].getAsBool();

			PushPreferences pushPrefs = PushManager.shared().getPreferences();
			pushPrefs.setSoundEnabled(soundEnabled);
			pushPrefs.setVibrateEnabled(vibEnabled);
			
			
			Log.i(TAG, "Push Registered");
		} catch (Exception e) {
			Log.i(TAG, "registerPush Error: " + e.toString());
		}
		return ret;
	}

}
