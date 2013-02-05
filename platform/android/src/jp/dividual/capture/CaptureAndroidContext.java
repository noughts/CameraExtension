package jp.dividual.capture;

import java.util.HashMap;
import java.util.Map;

import ru.inspirit.capture.CameraSurface;

import jp.dividual.capture.func.CaptureAndSaveImage;
import jp.dividual.capture.func.EndCamera;
import jp.dividual.capture.func.ExposureAtPoint;
import jp.dividual.capture.func.FocusAtPoint;
import jp.dividual.capture.func.GetFlashMode;
import jp.dividual.capture.func.ListDevices;
import jp.dividual.capture.func.RequestFrame;
import jp.dividual.capture.func.SetFlashMode;
import jp.dividual.capture.func.StartCamera;
import jp.dividual.capture.func.FlipCameraFunction;


import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;

public class CaptureAndroidContext extends FREContext {
	
	private CameraSurfaceView mSurface = null;

	public void setCameraSurface(CameraSurfaceView surface) {
		mSurface = surface;
	}
	
	public CameraSurfaceView getCameraSurface() {
		return mSurface;
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
		
		return map;
	}

}
