package ru.inspirit.capture{
	import flash.display.BitmapData;
	import flash.events.Event;
	import flash.events.EventDispatcher;
	import flash.events.StatusEvent;
	import flash.external.ExtensionContext;
	import flash.geom.Rectangle;
	import flash.utils.ByteArray;
	import flash.utils.Endian;
	import flash.system.Capabilities;

	/**
	* Class for video capturing from cameras
	* @author Eugene Zatepyakin
	*/
	
	public final class CaptureDevice extends EventDispatcher{
		internal static var _context:ExtensionContext;


		// Flash mode
		public static const FLASH_MODE_OFF:int = 0;
		public static const FLASH_MODE_ON:int = 1;
		public static const FLASH_MODE_AUTO:int = 2;
		
		// events
		public static const EVENT_IMAGE_SAVED:String = 'IMAGE_SAVED';
		public static const EVENT_CAPTURE_DEVICE_LOST:String = 'CAPTURE_DEVICE_LOST';
		public static const EVENT_FOCUS_COMPLETE:String = 'FOCUS_COMPLETE';
		public static const EVENT_PREVIEW_READY:String = 'PREVIEW_READY';
		
		public var bmp:BitmapData;

	
		/**
		* Initialize native side and prepare internal buffer.
		*/
		public static function initialize():void{
			if (!_context){
				_context = ExtensionContext.createExtensionContext("ru.inspirit.capture", null);
			}
		}

		// [静的] [読み取り専用] 使用可能なすべてのカメラの名前が含まれるストリング配列です。
		public static function get names():Array{
			_context.call( 'getNames' );
			return null;
		}

		// [静的] ビデオをキャプチャする CaptureDevice オブジェクトへの参照を返します。
		public static function getDevice( name:String, width:uint, height:uint ):CaptureDevice{
			_context.call( 'getDevice', name, width, height );
			return null;
		}

		
		/**
		 * Begin capturing video
		 */
		public function startCapturing():void{
			_context.call( 'startCapturing' );
			_context.addEventListener(StatusEvent.STATUS, onMiscStatus);
		}
		
		/**
		 * Stop capturing video
		 */
		public function stopCapturing():void{
			_context.call('stopCapturing' );
			_context.removeEventListener(StatusEvent.STATUS, onMiscStatus);
		}


		// フォーカスと露出を調整します
		public function focusAndExposureAtPoint(x:Number = 0.5, y:Number = 0.5):void{
			_context.call('focusAndExposureAtPoint', x, y);
		}

		
		// フラッシュの状態を設定
		public function setFlashMode( flashMode:uint ):void{
			_context.call( 'setFlashMode', flashMode );
		}
		
		public function getFlashMode():uint{
			var flashMode:uint = _context.call('getFlashMode') as uint;
			return flashMode;
		}
		
		// フレーム画像を要求する
		// 更新されていたら true を返し、bmp プロパティを書き換える
		public function requestFrame():Boolean{
			var isNewFrame:int = _context.call( 'requestFrame', bmp ) as int;
			return isNewFrame == 1;
		}
		

		// フォーカスと露出を合わせて撮影、フルサイズの画像を端末のカメラロールに保存し、withSound が true ならシャッター音を鳴らす
		// シャッター音は消せない可能性あり。要相談
		public function shutter( withSoud:Boolean=true ):void{
			_context.call( 'shutter', withSoud );
		}
		
		// カメラを順番に切り替える
		public function toggleDevice():void{
			_context.call( 'toggleDevice' );
		}



		public static function get available():Boolean{
			if(!_context) return false;
			return true;
		}

		
		
		internal function onMiscStatus(e:StatusEvent):void {
			if (e.code == "FOCUS_COMPLETE") {
				dispatchEvent(new Event(EVENT_FOCUS_COMPLETE));
			} else if (e.code == "PREVIEW_READY") {
				dispatchEvent(new Event(EVENT_PREVIEW_READY));
			}
		}

	}

}