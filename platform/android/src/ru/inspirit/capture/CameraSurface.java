package ru.inspirit.capture;

public class CameraSurface {

	static {
        System.loadLibrary("color_convert");
    }

	private native static void convert(byte[] input, byte[] output, int width, int height, int format);
    private native static void setupConvert(int width, int height);
    private native static void disposeConvert();
    
    public static void nativeSetupConvert(int width, int height) {
    	setupConvert(width, height);
    }
    
    public static void nativeConvert(byte[] input, byte[] output, int width, int height, int format) {
    	convert(input, output, width, height, format);
    }
    
    public static void nativeDisposeConvert() {
    	disposeConvert();
    }
    
}
