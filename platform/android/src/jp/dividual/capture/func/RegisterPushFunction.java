package jp.dividual.capture.func;


import jp.dividual.capture.IntentReceiver;
import jp.dividual.capture.Resources;
import android.app.Application;
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
		FREObject ret = null;
		try {
			
			Application app = context.getActivity().getApplication();
			
			AirshipConfigOptions options = AirshipConfigOptions.loadDefaultOptions(app);

	        // Optionally, customize your config at runtime:
	        //
	        // options.inProduction = false;
	        // options.developmentAppKey = "Your Development App Key";
	        // options.developmentAppSecret "Your Development App Secret";

	        UAirship.takeOff(app, options);
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
		} catch (Exception e) {
			Log.i(TAG, "Error: " + e.toString());
		}
		return ret;
	}

}
