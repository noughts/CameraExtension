package jp.dividual.capture {

	import flash.events.*;

	public class CaptureDeviceEvent extends Event {

		public static const EVENT_IMAGE_SAVED:String = 'IMAGE_SAVED';
		public static const EVENT_CAPTURE_DEVICE_LOST:String = 'CAPTURE_DEVICE_LOST';
		public static const EVENT_FOCUS_COMPLETE:String = 'FOCUS_COMPLETE';
		public static const EVENT_PREVIEW_READY:String = 'PREVIEW_READY';

		public var data:*;

		public function CaptureDeviceEvent( type:String ){
			super( type )
		}
	}
}