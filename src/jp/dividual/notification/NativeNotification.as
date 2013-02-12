package jp.dividual.notification {
	import flash.display.*;
	import flash.events.*;
	import flash.external.ExtensionContext;

	public final class NativeNotification extends EventDispatcher{
		internal static var _context:ExtensionContext;

		public function NativeNotification(){
			if (!_context) {
				_context = ExtensionContext.createExtensionContext("jp.dividual.capture", null);
			}
		}
		
		public function registerPush():void{
			_context.call( 'registerPush' );
		}

	}

}