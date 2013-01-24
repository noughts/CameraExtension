package ru.inspirit.capture
{
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
    
    public final class CaptureDevice extends EventDispatcher
    {
        internal static var _context:ExtensionContext;
        internal static var _info_buff:ByteArray;

        public static var MAX_TEXTURE_SIZE:int = 2048;
        
        public static const GET_FRAME_BITMAP:int = 2;// 1 << 1;
        public static const GET_FRAME_RAW_BYTES:int = 4;// 1 << 2;
        public static const GET_POWER_OF_2_FRAME_BGRA_BYTES:int = 8;// 1 << 3;

        public static const ANDROID_STILL_IMAGE_QUALITY_LOW:int = 0;
        public static const ANDROID_STILL_IMAGE_QUALITY_MEDIUM:int = 1;
        public static const ANDROID_STILL_IMAGE_QUALITY_BEST:int = 2;

		public static const DEVICE_ORIENTATION_0:int = 0;
		public static const DEVICE_ORIENTATION_90:int = 1;
		public static const DEVICE_ORIENTATION_180:int = 2;
		public static const DEVICE_ORIENTATION_270:int = 3;

		// Flash mode
		public static const IOS_FLASH_MODE_OFF:int = 0;
		public static const IOS_FLASH_MODE_ON:int = 1;
		public static const IOS_FLASH_MODE_AUTO:int = 2;
		
		public static const ANDROID_FLASH_MODE_NOT_SUPPORTED:int = -1;
		public static const ANDROID_FLASH_MODE_OFF:int = 0;
		public static const ANDROID_FLASH_MODE_ON:int = 1;
		public static const ANDROID_FLASH_MODE_AUTO:int = 2;
		public static const ANDROID_FLASH_MODE_RED_EYE:int = 3;
		public static const ANDROID_FLASH_MODE_TORCH:int = 4;
		
        // events
        public static const EVENT_IMAGE_SAVED:String = 'IMAGE_SAVED';
        public static const EVENT_CAPTURE_DEVICE_LOST:String = 'CAPTURE_DEVICE_LOST';
		public static const EVENT_FOCUS_COMPLETE:String = 'FOCUS_COMPLETE';
		public static const EVENT_PREVIEW_READY:String = 'PREVIEW_READY';
		
		public static const EVENT_STATUS_BAR_TAPPED:String = 'STATUS_BAR_TAPPED';
		
        protected var _id:int = -1;
        protected var _deviceName:String;
        protected var _width:int;
        protected var _height:int;
        protected var _width2:int;
        protected var _height2:int;
        protected var _isCapturing:Boolean = false;
        
        public var bmp:BitmapData;
        public var rawBytes:ByteArray;
        public var bgraP2Bytes:ByteArray;

        public var rect:Rectangle;
        
        /**
         * Try to start provided Device.
         * if everything runs OK u will get new instance
         * otherwise u have to catch an Error event
         * 
         * please note that result video frame width\height may differ from requested
         * 
         * @param    deviceName             wanted device name received by CaptureDevice.getDevices method
         * @param    width                  desired video frame width
         * @param    height                 desired video frame width
         * @param    fps                    desired frame rate
         * @param    stillImageQuality      desired still image quality (ANDROID only)
         */
        public function CaptureDevice(deviceName:String, width:int, height:int, fps:int = 0, stillImageQuality:int = ANDROID_STILL_IMAGE_QUALITY_MEDIUM)
        {
            var res:Boolean = _init(deviceName, width, height, fps, stillImageQuality);
            if (!res)
            {
                throw new Error('CaptureDevice: ' + deviceName + ' failed to start');
            }
			_context.addEventListener(StatusEvent.STATUS, onMiscStatus);
        }
        
        /**
         * Begin capturing video
         */
        public function start():void
        {
            _context.call('toggleCapturing', _id, 1);
            _isCapturing = true;
        }
        
        /**
         * Stop capturing video
         */
        public function stop():void
        {
            _context.call('toggleCapturing', _id, 0);
            _isCapturing = false;
        }

        public function focusAtPoint(x:Number = 0.5, y:Number = 0.5):void
        {
            _context.call('focusAtPoint', _id, x, y);
        }

		// Additional APIs //
		
		private var _requestedOrientation:int = 0;
		
		// Image will be saved to the spcified path. Byte array won't be passed to Flash.
		public function captureAndSaveImage(dirName:String, orientation:int = DEVICE_ORIENTATION_0):String 
		{
			var filePath:String = _context.call('captureAndSaveImage', _id, dirName, orientation) as String;
			return filePath;
		}
		
		public function exposureAtPoint(x:Number = 0.5, y:Number = 0.5):void
		{
			_context.call('exposureAtPoint', _id, x, y);		
		}
		
		public function setFlashMode(flashMode:int):void
		{
			_context.call('setFlashMode', _id, flashMode);
		}
		
		public function getFlashMode():int
		{
			var flashMode:int = _context.call('getFlashMode', _id) as int;
			return flashMode;
		}
		
		public function isAdjustingFocus():Boolean
		{
			return _context.call('isAdjustingFocus', _id) as Boolean;
		}
		
		public function isAdjustingExposure():Boolean
		{
			return _context.call('isAdjustingExposure', _id) as Boolean;
		}
		
		//
		
        public function captureStillImage():void
        {
            _context.addEventListener(StatusEvent.STATUS, onStatus);
            _context.call('camShot', _id);
        }

        public function grabStillImage(ba:ByteArray):void
        {
            _context.call('grabCamShot', ba);
        }

        public function setupForDataType(dataType:int, powerOfTwoRect:Rectangle = null):void
        {
            _disposeData();

            // internally fixed to render correctly to display :)
            if(dataType & GET_FRAME_BITMAP)
            {
                bmp = new BitmapData(_width, _height, false, 0x0);
            }

            // the output is platform dependent
            // on macOS and iOS u will have BGRA pixel values
            // on windows Y flipped BGR pixel values
            // on Android most likely ImageFormat.NV21 aka YCrCb format used for images
            if(dataType & GET_FRAME_RAW_BYTES)
            {
                rawBytes = new ByteArray();
                rawBytes.endian = Endian.LITTLE_ENDIAN;
                rawBytes.length = _width * _height * 4; // should be enough for any
            }

            // internally fixed to be always BGRA pixels
            // and image is center aligned in power of 2 frame size
            // perfectly suited for Stage3D texture
            if(dataType & GET_POWER_OF_2_FRAME_BGRA_BYTES)
            {
                bgraP2Bytes = new ByteArray();
                bgraP2Bytes.endian = Endian.LITTLE_ENDIAN;
                var w2:int = nextPowerOfTwo(_width);
                var h2:int = nextPowerOfTwo(_height);
                w2 = Math.min(MAX_TEXTURE_SIZE, w2);
                h2 = Math.min(MAX_TEXTURE_SIZE, h2);
                if (null != powerOfTwoRect)
                {
                    w2 = Math.min(w2, powerOfTwoRect.width);
                    h2 = Math.min(h2, powerOfTwoRect.height);
                }
                _width2 = w2;
                _height2 = h2;
                bgraP2Bytes.length = w2 * h2 * 4;
            }
        }
        
        /**
         * Update Device data if new frame is available
         * u should provide what actions to perform
         * 
         * Returns whether there is a new video frame available since the last call
         * 
         * @param    options     specify what data to gather
         * @return     true if new frame was received otherwise false
         */
        public function requestFrame(options:int = GET_FRAME_BITMAP):Boolean
        {
            _info_buff.position = 0;
            _info_buff.writeInt(options);
            _info_buff.writeInt(_width2);
            _info_buff.writeInt(_height2);
            
            var isNewFrame:int = _context.call('getCaptureFrame', _id, _info_buff, bmp, rawBytes, bgraP2Bytes) as int;
            
            // we lost device - happens when u unplug the cam during capturing
            //
            // on Mac system i wasnt able to dispose it without hanging the whole app
            // smth is wrong there and i dont see how to handle it
            // so it is better to remove hanged device from update list but dont try to dispose it
            //
            // on Windows machines disposing hanged device works OK.
            // so as soon as u get this message u can dispose it and remove from update list
            if (isNewFrame == -1)
            {
                _isCapturing = false;
                dispatchEvent( new Event(EVENT_CAPTURE_DEVICE_LOST, false, true) );
            }
            
            return isNewFrame == 1;
        }
        
        /**
         * Dispose current Device instance internally
         * and clear instance objects
         */
        public function dispose():void
        {
            _context.call('releaseCapture', _id);
            _isCapturing = false;
            _disposeData();
        }
        
        /**
         * Initialize native side and prepare internal buffer.
         */
        public static function initialize():void
        {
            if (!_context)
            {
                _context = ExtensionContext.createExtensionContext("ru.inspirit.capture", null);
                if(_context)
                {
                    _info_buff = new ByteArray();
                    _info_buff.endian = Endian.LITTLE_ENDIAN;
                    _info_buff.length = 64 * 1024;
                }
            }
        }

        public static function supportsSaveToCameraRoll():Boolean
        {
            if(!_context) throw new Error('Native Extension not initialized');
            var res:int = _context.call('supportsSaveToCameraRoll') as int;
            return res == 1;
        }

        public static function saveToCameraRoll(fileName:String, data:ByteArray, size:int, orientation:int = DEVICE_ORIENTATION_0):Boolean
        {
            if(!_context) throw new Error('Native Extension not initialized');
            var res:int = _context.call('saveToCameraRoll', fileName, data, size, orientation) as int;
            return res == 1;
        }
        
        /**
         * Check if extension was initialized and ready to use
         */
        public static function get available():Boolean
        {
            if(!_context) return false;
            return true;
        }

        /**
         * Dispose native extension
         * after calling u should start with initialize method
         */
        public static function unInitialize():void
        {
            _context.call('disposeANE');
            _context.dispose();
            _context = null;
            _info_buff.length = 0;
            _info_buff = null;
        }
        
        /**
         * Returns a vector of all Devices connected to the system. 
         * If forceRefresh then the system will be polled for connected devices. 
         */
        public static function getDevices(forceRefresh:Boolean = false):Vector.<CaptureDeviceInfo>
        {
            _info_buff.position = 0;
            _context.call('listDevices', forceRefresh ? 1 : 0, _info_buff);
            
            var n:int = _info_buff.readInt();
            var i:int;
            
            var devs:Vector.<CaptureDeviceInfo> = new Vector.<CaptureDeviceInfo>(n);
            
            for (i = 0; i < n; ++i)
            {
                var name_size:int = _info_buff.readInt();
                var name_str:String = _info_buff.readUTFBytes(name_size);
                var available:Boolean = Boolean(_info_buff.readInt());
                var connected:Boolean = Boolean(_info_buff.readInt());
                var dev:CaptureDeviceInfo = new CaptureDeviceInfo();
                dev.name = name_str;
                dev.available = available;
                dev.connected = connected;
                
                devs[i] = dev;
            }
            
            return devs;
        }
        
        internal function _init(deviceName:String, width:int, height:int, fps:int, stillImageQuality:int):Boolean
        {
            _info_buff.position = 0;
            _context.call('getCapture', deviceName, width, height, fps, _info_buff, stillImageQuality);
            
            _id = _info_buff.readInt();
            
            if (_id != -1)
            {
                _width = _info_buff.readInt();
                _height = _info_buff.readInt();
                
                _deviceName = deviceName;
                _isCapturing = true; // always autostarts
                
                rect = new Rectangle(0, 0, _width, _height);
            }
            
            return _id != -1;
        }

        internal function _disposeData():void
        {
            if (bmp)
            {
                bmp.dispose();
                bmp = null;
            }
            if(rawBytes)
            {
                rawBytes.length = 0;
                rawBytes = null;
            }
            if(bgraP2Bytes)
            {
                bgraP2Bytes.length = 0;
                bgraP2Bytes = null;
            }
        }
        
        public function get id():int
        {
            return _id;
        }
        
        public function get width():int
        {
            return _width;
        }
        
        public function get height():int
        {
            return _height;
        }

        public function get name():String
        {
            return _deviceName;
        }

        public function get isCapturing():Boolean
        {
            return _isCapturing;
        }

        override public function toString():String
        {
            return 'CaptureDevice { '+ _deviceName +' } { ' + _width + 'x' + _height + ' }';
        }

        public static function nextPowerOfTwo(v:uint):uint
        {
            v--;
            v |= v >> 1;
            v |= v >> 2;
            v |= v >> 4;
            v |= v >> 8;
            v |= v >> 16;
            v++;
            return v;
        }

		private function saveImage():void {
			// the result is JPEG file
			var ba:ByteArray = new ByteArray();
			ba.endian = Endian.LITTLE_ENDIAN;
			grabStillImage(ba);
			ba.position = 0;
			
			// now lets save it to camera roll
			if(CaptureDevice.supportsSaveToCameraRoll()){
				var now:Date = new Date();
				var filename:String = "IMG_" + now.fullYear +
					now.month +
					now.day + "_" +
					now.hours + 
					now.minutes +
					now.seconds + ".jpg";
				saveToCameraRoll(filename, ba, ba.length, _requestedOrientation);
				_requestedOrientation = DEVICE_ORIENTATION_0;
			}
		}
		
        internal function onStatus(e:StatusEvent):void {
            if (e.code == "CAM_SHOT") {
				saveImage();
			}
			_context.removeEventListener(StatusEvent.STATUS, onStatus);
        }
		
		internal function onMiscStatus(e:StatusEvent):void {
			if (e.code == "IMAGE_SAVED") {
				dispatchEvent(new Event(EVENT_IMAGE_SAVED));
			} else if (e.code == "STATUS_BAR") {
				dispatchEvent(new Event(EVENT_STATUS_BAR_TAPPED));
			} else if (e.code == "FOCUS_COMPLETE") {
				dispatchEvent(new Event(EVENT_FOCUS_COMPLETE));
			} else if (e.code == "PREVIEW_READY") {
				dispatchEvent(new Event(EVENT_PREVIEW_READY));
			}
		}

    }

}