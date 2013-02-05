package jp.dividual.capture.func;

import jp.dividual.capture.CameraSurfaceView;
import jp.dividual.capture.CaptureAndroidContext;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.adobe.fre.FREContext;
import com.adobe.fre.FREFunction;
import com.adobe.fre.FREObject;

public class EndCamera implements FREFunction {

	public static final String KEY ="endCamera";

	private static final String TAG = "endCameraFunction";
    
    
    @Override
    public FREObject call(FREContext context, FREObject[] args) {

        // Remove SurfaceView from view tree
        View focus = context.getActivity().getCurrentFocus();
        FrameLayout parent = (FrameLayout)focus.getParent();
        CameraSurfaceView surfaceView = (CameraSurfaceView)parent.findViewWithTag(CameraSurfaceView.VIEW_TAG);
        if (surfaceView != null) {
        	surfaceView.endCamera();
        	parent.removeView(surfaceView);
        }

        ((CaptureAndroidContext)context).setCameraSurface(null);
        
        return null;
    }

}
