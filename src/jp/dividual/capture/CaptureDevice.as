package jp.dividual.capture {
	import flash.display.*;
	import flash.events.*;
	import flash.external.ExtensionContext;
	import flash.utils.*;
	import flash.system.*;
	import flash.geom.*;

	public final class CaptureDevice extends EventDispatcher{
		internal static var _context:ExtensionContext;
		internal static var _infoBuffer:ByteArray;
		
		// Flash mode
		public static const FLASH_MODE_OFF:int = 0;
		public static const FLASH_MODE_ON:int = 1;
		public static const FLASH_MODE_AUTO:int = 2;
		
		public static const ANDROID_STILL_IMAGE_QUALITY_LOW:int = 0;
		public static const ANDROID_STILL_IMAGE_QUALITY_MEDIUM:int = 1;
		public static const ANDROID_STILL_IMAGE_QUALITY_BEST:int = 2;
		
		// Orientation of picture to be saved
		public static const ROTATION_0:int = 0;
		public static const ROTATION_90:int = 1;
		public static const ROTATION_180:int = 2;
		public static const ROTATION_270:int = 3;
				
		public var bmp:BitmapData;

		private var _index:int;
		public function get index():int{ return _index }
		private var _width:int;
		private var _height:int;
		private var _fps:int;
		
		public function CaptureDevice(cameraIndex:int, width:int, height:int, fps:int=15) {
			_initExtensionContext();

			_index = cameraIndex;
			_width = width;
			_height = height;
			_fps = fps;
		}


		static private function _initExtensionContext():void{
			if (!_context) {
				_context = ExtensionContext.createExtensionContext("jp.dividual.capture", null);
				if (!_infoBuffer) {
					_infoBuffer = new ByteArray();
					_infoBuffer.endian = Endian.LITTLE_ENDIAN;
					_infoBuffer.length = 64 * 1024;
				}
			}
		}



		
		// [静的] [読み取り専用] 使用可能なすべてのカメラの名前が含まれるストリング配列です。
		private static var _names:Array;
		public static function get names():Array{
			if (!_context) {
				_context = ExtensionContext.createExtensionContext("jp.dividual.capture", null);
			}

			if( _names ){
				return _names;
			}

			if (!_infoBuffer) {
				_infoBuffer = new ByteArray();
				_infoBuffer.endian = Endian.LITTLE_ENDIAN;
				_infoBuffer.length = 64 * 1024;
			} else {
				_infoBuffer.position = 0;
			}
			
			_context.call('listDevices', _infoBuffer);
			
			var n:int = _infoBuffer.readInt();
			var ret:Array = new Array(n);
			for (var i:int = 0; i < n; i++) {
				var nameLength:int = _infoBuffer.readInt();
				var name:String = _infoBuffer.readUTFBytes(nameLength);
				var available:Boolean = Boolean(_infoBuffer.readInt());
				var connected:Boolean = Boolean(_infoBuffer.readInt());
				ret[i] = name;
			}
			_names = ret;
			return ret;
		}

		/**
		 * Begin capturing video
		 */
		public function startCapturing():void {
			var infoBuffer:ByteArray = new ByteArray();
			infoBuffer.endian = Endian.LITTLE_ENDIAN;
			infoBuffer.length = 64 * 1024;

			_context.call('startCamera', _index, _width, _height, _fps, infoBuffer, ANDROID_STILL_IMAGE_QUALITY_BEST);
			var width:int = infoBuffer.readInt();
			var height:int = infoBuffer.readInt();
			_width = width;
			_height = height;
			bmp = new BitmapData(_width, _height, false, 0x0);
			_context.addEventListener(StatusEvent.STATUS, onMiscStatus);
		}
		
		/**
		 * Stop capturing video
		 */
		public function stopCapturing():void{
			_context.call('endCamera', _index);
			_context.removeEventListener(StatusEvent.STATUS, onMiscStatus);
		}


		// フォーカスと露出を調整します
		public function focusAndExposureAtPoint(x:Number = 0.5, y:Number = 0.5):void {
			_context.call('focusAtPoint', x, y);
		}


		// 現在のカメラで露出補正がサポートされているか？
		public function get isExposureCompensationSupported():Boolean{
			if( Capabilities.manufacturer.search('Android') > -1 ){
				return true;
			}
			return false;
			//return _context.call( 'isExposureCompensationSupported' ) as Boolean;
		}
		
		// 現在のカメラのEV値を設定
		public function setExposureCompensation( val:int ):void{
			if( isExposureCompensationSupported ){
				_context.call( 'setExposureCompensation', val );
			}
		}

		// EV値を取得
		public function getExposureCompensation():int{
			if( isExposureCompensationSupported ){
				return _context.call('getExposureCompensation') as int;
			}
			return 0;
		}


		// 現在のカメラでフラッシュがサポートされているか
		public function get isFlashSupported():Boolean{
			return _context.call('isFlashSupported') as Boolean;
		}
		
		// フラッシュの状態を設定
		public function setFlashMode( flashMode:uint ):void{
			_context.call( 'setFlashMode', flashMode);
		}
		
		public function getFlashMode():uint{
			var flashMode:uint = _context.call('getFlashMode') as uint;
			return flashMode;
		}
		
		// フレーム画像を要求する
		// 更新されていたら true を返し、bmp プロパティを書き換える
		public function requestFrame():Boolean {
			if (_context == null) {
				return false;
			}
			var isNewFrame:int = _context.call('requestFrame', bmp, _index, _width, _height) as int;

			// Android でフロントカメラだったら上下反転
				trace( _index, Capabilities.manufacturer.search('Android') )
			if( _index==1 && Capabilities.manufacturer.search('Android') > -1 ){
				var flipped:BitmapData = new BitmapData( bmp.width, bmp.height );
				var mat:Matrix = new Matrix( 1, 0, 0, -1, 0, bmp.height);
				flipped.draw( bmp, mat, null, null, null, true );
				bmp = flipped.clone();
				//flipped.dispose();
				trace( "flipped" )
			}

			return (isNewFrame == 1);
		}
		

		// フォーカスと露出を合わせて撮影、フルサイズの画像を端末のカメラロールに保存し、withSound が true ならシャッター音を鳴らす
		// シャッター音は消せない可能性あり。要相談
		public function shutter(directoryName:String, pictureOrientation:int, withSound:Boolean=true, lat:Number=99999, lng:Number=99999 ):void {
			if( lat==99999 && lng==99999 ){
				_context.call( 'captureAndSaveImage', directoryName, pictureOrientation, _index );
			} else {
				_context.call( 'captureAndSaveImage', directoryName, pictureOrientation, _index, lat, lng );
			}
		}


		// android で撮影した画像に位置情報を入れる
		public function putExifLocation( filePath:String, lat:Number, lng:Number ):void{
			_context.call('putExifLocation', filePath, lat, lng );
		}

		
		// カメラを順番に切り替える
		public function toggleDevice():void{
			var infoBuffer:ByteArray = new ByteArray();
			infoBuffer.endian = Endian.LITTLE_ENDIAN;
			infoBuffer.length = 64 * 1024;
			
			//_context.call('startCamera', _index, _width, _height, _fps, infoBuffer, ANDROID_STILL_IMAGE_QUALITY_BEST);
			_context.call('flipCamera', infoBuffer);
			_index = (_index + 1) % 2;
			
			var width:int = infoBuffer.readInt();
			var height:int = infoBuffer.readInt();
			_width = width;
			_height = height;
			bmp = new BitmapData(_width, _height, false, 0x0);
		}



		public static function get available():Boolean{
			if(!_context) return false;
			return true;
		}

		
		
		internal function onMiscStatus(e:StatusEvent):void {
			trace( "onMiscStatus", e.code, e.level )
			if (e.code == CaptureDeviceEvent.EVENT_FOCUS_COMPLETE) {
				dispatchEvent(new CaptureDeviceEvent(CaptureDeviceEvent.EVENT_FOCUS_COMPLETE));
			} else if (e.code == CaptureDeviceEvent.EVENT_PREVIEW_READY) {
				dispatchEvent(new CaptureDeviceEvent(CaptureDeviceEvent.EVENT_PREVIEW_READY));
			} else if (e.code == CaptureDeviceEvent.EVENT_IMAGE_SAVED) {
				var ne:CaptureDeviceEvent = new CaptureDeviceEvent( CaptureDeviceEvent.EVENT_IMAGE_SAVED )
				ne.data = e.level;
				dispatchEvent( ne );
			}
		}

	}

}