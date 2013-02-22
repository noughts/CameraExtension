package jp.dividual.capture.func;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import jp.dividual.capture.CaptureAndroidApplication;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class GetLocationFunction implements FREFunction {

	public static final String KEY = "getLocation"; // ToDo:getLocationにかえる。

	private static final String TAG = "GetLocationFunction";

	/* Location */
	// Location currentLocation = null;
	private LocationManager locationManager;
	private MyLocationListener locationListener;
	// ProgressDialog locationSensingProgressDialog;
	boolean isWaitingLocationUpdate;
	private Handler mHandler = new Handler();
	// 常時のログ取得タイミング
	static final int MIN_TIME = 0; // milli second
	static final int MIN_DISTANCE = 0; // meter

	static final int LOCATION_SENSING_MAX_WAIT = 20000; // 13sec

	CaptureAndroidApplication app;

	private Runnable stopLocationUpdateTask = new Runnable() {
		public void run() {
			if (isWaitingLocationUpdate == true) {
				locationManager.removeUpdates(locationListener);
				isWaitingLocationUpdate = false;

			}
		}

	};

	@Override
	public FREObject call(FREContext context, FREObject[] args) {
		Log.i(TAG, "getLocation called");

		FREObject ret = null;

		Application app = context.getActivity().getApplication();
		Activity activity = context.getActivity();
		Log.i(TAG, "activity is " + activity.toString());
		Log.i(TAG, "activity class name is " + activity.getClass().getName());

		if (app == null) {
			Log.i(TAG, "app is null");
		} else {
			Log.i(TAG, "app is " + app.toString());
			Log.i(TAG, "app class name is " + app.getClass().getName());
		}
		this.app = (CaptureAndroidApplication) app;

		locationListener = new MyLocationListener();
		locationManager = (LocationManager) activity
				.getSystemService(Context.LOCATION_SERVICE);
		this.getLocationOneTime();

		return ret;
	}

	public void getLocationOneTime() {
		if (isWaitingLocationUpdate == false) {
			/*
			 * locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER
			 * , MIN_TIME, MIN_DISTANCE, locationListener);
			 */

			List<String> availableProviderList = locationManager
					.getProviders(true);
			String locationProviderName = null;
			for (String providerName : availableProviderList) {
				if (providerName.equalsIgnoreCase("network")) {
					locationProviderName = LocationManager.NETWORK_PROVIDER;
					break;
				} else if (providerName.equalsIgnoreCase("gps")) {
					locationProviderName = LocationManager.GPS_PROVIDER;
					break;
				}
				Log.i(TAG, providerName);
			}

			if (locationProviderName == null) {
				// ToDo: show alert
			} else {
				locationManager.requestLocationUpdates(locationProviderName,
						MIN_TIME, MIN_DISTANCE, locationListener);
			}

			isWaitingLocationUpdate = true;
			mHandler.postDelayed(stopLocationUpdateTask,
					LOCATION_SENSING_MAX_WAIT); // 10sec
		}

	}

	private class MyLocationListener implements LocationListener {
		private MyLocationListener() {
		}

		public void onLocationChanged(Location loc) {

			locationManager.removeUpdates(locationListener);
			isWaitingLocationUpdate = false;
			// locationObtained(loc.getLatitude(), loc.getLongitude());

			int lat = (int) (loc.getLatitude() * 1E6);
			int lng = (int) (loc.getLongitude() * 1E6);
			Log.i(TAG, "onLocationChanged lat = " + lat + ", lng=" + lng);

			app.currentLocation = loc;
			
			/*
			File pictureFile = getOutputMediaFile("Blink");
			String path = pictureFile.getAbsolutePath();
			
			if (app.currentLocation != null) {
				Log.d(TAG, "putting exif..");
				putExifLocation(path, app.currentLocation);
			} else {
				Log.d(TAG, "canot put exif! No location object.");
			}

			readoutExifLocation(path);
			*/

		}

		public void onProviderDisabled(String arg0) {
			Log.i(TAG, "onProviderDisabled by " + arg0);
		}

		public void onProviderEnabled(String arg0) {
			Log.i(TAG, "onProviderEnabled");
		}

		public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
			Log.i(TAG, "onStatusChanged");
		}
	}

	public void putExifLocation(String path, Location location) {
		// final String DUMMY_GPS_LATITUDE = "22/1,21/1,299295/32768";
		// final String DUMMY_GPS_LATITUDE_REF = "N";
		// final String DUMMY_GPS_LONGITUDE = "114/1,3/1,207045/4096";
		// final String DUMMY_GPS_LONGITUDE_REF = "E";

		try {
			ExifInterface exif = new ExifInterface(path);
			// String latitudeStr = "90/1,12/1,30/1";
			double lat = location.getLatitude();
			double alat = Math.abs(lat);
			String dms = Location.convert(alat, Location.FORMAT_SECONDS);
			String[] splits = dms.split(":");
			String[] secnds = (splits[2]).split("\\.");
			String seconds;
			if (secnds.length == 0) {
				seconds = splits[2];
			} else {
				seconds = secnds[0];
			}

			String latitudeStr = splits[0] + "/1," + splits[1] + "/1,"
					+ seconds + "/1";
			exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latitudeStr);
			// exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,
			// DUMMY_GPS_LATITUDE);

			exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, lat > 0 ? "N"
					: "S");
			// exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF,
			// DUMMY_GPS_LATITUDE_REF);

			double lon = location.getLongitude();
			double alon = Math.abs(lon);

			dms = Location.convert(alon, Location.FORMAT_SECONDS);
			splits = dms.split(":");
			secnds = (splits[2]).split("\\.");

			if (secnds.length == 0) {
				seconds = splits[2];
			} else {
				seconds = secnds[0];
			}
			String longitudeStr = splits[0] + "/1," + splits[1] + "/1,"
					+ seconds + "/1";

			exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, longitudeStr);
			// exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,
			// DUMMY_GPS_LONGITUDE);

			exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,
					lon > 0 ? "E" : "W");
			// exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF,
			// DUMMY_GPS_LONGITUDE_REF);

			exif.saveAttributes();

			Log.d(TAG, "put exif location done");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void readoutExifLocation(String path) {
		try {
			ExifInterface exif = new ExifInterface(path);
			String latString = exif
					.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
			String lngString = exif
					.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);

			Log.i(TAG, "Read out Exif location lat = " + latString
					+ " , lng = " + lngString);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Create a File for saving an image */
	private static File getOutputMediaFile(String dirName) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				dirName);
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d(TAG, "failed to create directory: " + dirName);
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
		timeStamp = "20130218_215838";
		File mediaFile = new File(mediaStorageDir.getPath() + File.separator
				+ "IMG_" + timeStamp + ".jpg");
		return mediaFile;
	}

}
