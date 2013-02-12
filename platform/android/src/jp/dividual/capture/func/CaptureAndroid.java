package jp.dividual.capture.func;

import jp.dividual.capture.CaptureAndroidContext;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREExtension;

public class CaptureAndroid implements FREExtension {

	@Override
	public FREContext createContext(String arg0) {
		return new CaptureAndroidContext();
	}

	@Override
	public void dispose() {

	}

	@Override
	public void initialize() {

	}

}
