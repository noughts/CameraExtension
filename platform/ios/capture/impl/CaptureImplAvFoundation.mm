
#import "CaptureImplAvFoundation.h"
#include <dlfcn.h>
#include <string.h>
#import <UIKit/UIDevice.h>
#import <UIKit/UIKit.h>
#import <AssetsLibrary/AssetsLibrary.h>
#import <ImageIO/ImageIO.h>

std::string    convertNsString( NSString *str )
{
    return std::string( [str UTF8String] );
}

CaptureImplAvFoundationDevice::CaptureImplAvFoundationDevice( AVCaptureDevice * device )
    : Capture::Device()
{
    mUniqueId = convertNsString( [device uniqueID] );
    mName = convertNsString( [device localizedName] );
    mNativeDevice = [device retain];
}

CaptureImplAvFoundationDevice::~CaptureImplAvFoundationDevice()
{
    [mNativeDevice release];
}

bool CaptureImplAvFoundationDevice::checkAvailable() const
{
    return mNativeDevice.connected;
}

bool CaptureImplAvFoundationDevice::isConnected() const
{
    return mNativeDevice.connected;
}


void frameDeallocator( void *refcon )
{
    CVPixelBufferRef pixelBuffer = reinterpret_cast<CVPixelBufferRef>( refcon );
    CVPixelBufferUnlockBaseAddress( pixelBuffer, 0 );
    CVBufferRelease( pixelBuffer );
}


static std::vector<Capture::DeviceRef> sDevices;
static BOOL sDevicesEnumerated = false;

@implementation CaptureImplAvFoundation

+ (AVCaptureConnection *)connectionWithMediaType:(NSString *)mediaType fromConnections:(NSArray *)connections
{
	for ( AVCaptureConnection *connection in connections ) {
		for ( AVCaptureInputPort *port in [connection inputPorts] ) {
			if ( [[port mediaType] isEqual:mediaType] ) {
				return connection;
			}
		}
	}
	return nil;
}

+ (const std::vector<Capture::DeviceRef>&)getDevices:(BOOL)forceRefresh
{
    if( sDevicesEnumerated && ( ! forceRefresh ) ) {
        return sDevices;
    }

    sDevices.clear();
    
    Class clsAVCaptureDevice = NSClassFromString(@"AVCaptureDevice");
    if( clsAVCaptureDevice != nil ) {
        NSArray * devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
        for( int i = 0; i < [devices count]; i++ ) {
            AVCaptureDevice * device = [devices objectAtIndex:i];
            sDevices.push_back( Capture::DeviceRef( new CaptureImplAvFoundationDevice( device ) ) );
        }
    }
    sDevicesEnumerated = true;
    return sDevices;
}

+ (bool)saveToCameraRoll:(const char*)name data:(const uint8_t*)data size:(int)size orientation:(int)orientation
{
    NSData *dataForJPGFile = nil;
    NSString *fileName = nil;
    
    dataForJPGFile = [NSData dataWithBytes:data length:size];
    //fileName = [NSString stringWithUTF8String:name];
    
    // we can save to program documents also
    /*
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES);
    NSString *documentsDirectory = [paths objectAtIndex:0];
    
    NSError *error2 = nil;
    if (![dataForJPGFile writeToFile:[documentsDirectory stringByAppendingPathComponent:fileName] options:NSAtomicWrite error:&error2])
    {
        NSLog(@"ERROR: the image failed to be written to Documents dir");
        return false;
    }
    */
    
    // Save to assets library
    ALAssetsLibrary *library = [[ALAssetsLibrary alloc] init];
    
    // Add orientation metadata
    int meta = 0;
    if (orientation == 0) {
        meta = 1; 
    } else if (orientation == 1) {
        meta = 6; // 90CW
    } else if (orientation == 2) {
        meta = 3; // 180
    } else if (orientation == 3) {
        meta = 8; // 270CW
    }

    NSDictionary *metadata = [NSDictionary dictionaryWithObjectsAndKeys:
                              [NSNumber numberWithInt:meta], (NSString *)kCGImagePropertyOrientation,
                              nil];
    [library writeImageDataToSavedPhotosAlbum:dataForJPGFile metadata:metadata completionBlock:^(NSURL *assetURL, NSError *error)
     {
         if (error) 
         {
             NSLog(@"ERROR: the image failed to be written to Photos Album");
         }
         else {
             NSLog(@"PHOTO SAVED - assetURL: %@", assetURL);
         }
     }];
    
    return true;
}

- (id)initWithDevice:(const Capture::DeviceRef)device width:(int)width height:(int)height frameRate:(int)frameRate
{
    if( ( self = [super init] ) ) {

        mDevice = device;
        if( mDevice ) {
            mDeviceUniqueId = [NSString stringWithUTF8String:device->getUniqueId().c_str()];
            [mDeviceUniqueId retain];
        }
        
        mIsCapturing = false;
        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
        mHasNewFrame = false;
        mExposedFrameBytesPerRow = 0;
        mExposedFrameWidth = 0;
        mExposedFrameHeight = 0;
        
        // Additional code to enable tapping on the status bar
        // NSArray* windows = [UIApplication sharedApplication].windows;
        // UIView *rootView = [windows objectAtIndex:0];
        // UIScrollView *scrollView = [[UIScrollView alloc] initWithFrame:CGRectMake(0, 0, rootView.frame.size.width, 0)];
        // [rootView addSubview:scrollView];
        // scrollView.delegate = self;
        // scrollView.contentSize = CGSizeMake(0.0f, 2.0f);
        // scrollView.contentOffset = CGPointMake(0.0f, 1.0f);
    }
    return self;
}

- (void)dealloc
{
    if( mIsCapturing ) {
        [self stopCapture];
    }
    
    [mDeviceUniqueId release];
    
    [super dealloc];
}

- (BOOL)scrollViewShouldScrollToTop:(UIScrollView *)scrollView {
	NSLog(@"status bar tapped.");
    statusBarTappedCallback();
	return NO;
}

- (bool)prepareStartCapture 
{
    // AVFramework is weak-linked to maintain support for iOS 3.2, 
    // so if these symbols don't exist, don't start the capture
    Class clsAVCaptureSession = NSClassFromString(@"AVCaptureSession");
    Class clsAVCaptureDevice = NSClassFromString(@"AVCaptureDevice");
    Class clsAVCaptureDeviceInput = NSClassFromString(@"AVCaptureDeviceInput");
    Class clsAVCaptureVideoDataOutput = NSClassFromString(@"AVCaptureVideoDataOutput");
    Class clsAVCaptureConnection = NSClassFromString(@"AVCaptureConnection");
    if( clsAVCaptureSession == nil || 
        clsAVCaptureDevice == nil || 
        clsAVCaptureDeviceInput == nil || 
        clsAVCaptureVideoDataOutput == nil ||
        clsAVCaptureConnection == nil ) 
    {
        return false;
    }

    NSError * error = nil;

    mSession = [[clsAVCaptureSession alloc] init];

    // Configure the session
    // 
    
    [mSession beginConfiguration];    
    

    // Find a suitable AVCaptureDevice
    AVCaptureDevice * mCapDevice = nil;
    if( ! mDeviceUniqueId ) {
        mCapDevice = [clsAVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    } else {
        mCapDevice = [clsAVCaptureDevice deviceWithUniqueID:mDeviceUniqueId];
    }
    
    if( ! mCapDevice ) {
        throw CaptureExcInitFail();
    }

    // Create a device input with the device and add it to the session.
    mCapDeviceInput = [clsAVCaptureDeviceInput deviceInputWithDevice:mCapDevice error:&error];
    if( ! mCapDeviceInput ) {
        throw CaptureExcInitFail();
    }
    [mSession addInput:mCapDeviceInput];

    // Create a VideoDataOutput and add it to the session
    mCapOutput = [[[clsAVCaptureVideoDataOutput alloc] init] autorelease];
    [mSession addOutput:mCapOutput];
    
    // Configure your output.
    dispatch_queue_t queue = dispatch_queue_create("myQueue", NULL);
    mPreviewReady = false;
    [mCapOutput setSampleBufferDelegate:self queue:queue];
    dispatch_release(queue);
    
    
    [mCapOutput setVideoSettings:[NSDictionary dictionaryWithObject:
                                  [NSNumber numberWithInt:kCVPixelFormatType_32BGRA] forKey:(id)kCVPixelBufferPixelFormatTypeKey]];

    mCapOutput.alwaysDiscardsLateVideoFrames = YES;
    
    if(mFrameRate > 0)
    {
        float version = [[[UIDevice currentDevice] systemVersion] floatValue];
        if (version < 5.0f) 
        {
            mCapOutput.minFrameDuration = CMTimeMake(1, mFrameRate);
        } else {
            AVCaptureConnection *conn = [mCapOutput connectionWithMediaType:AVMediaTypeVideo];
            conn.videoMinFrameDuration = CMTimeMake(1, mFrameRate);
            conn.videoMaxFrameDuration = CMTimeMake(1, mFrameRate);
        }
    }
    
    if(mWidth == 0)
    {
        mSession.sessionPreset = AVCaptureSessionPresetPhoto;
    }
    else if(mWidth < 480)
    {
        mSession.sessionPreset = AVCaptureSessionPresetLow;
    }
    else if (mWidth < 1024) {
        if(mWidth == 640 && [mSession canSetSessionPreset: AVCaptureSessionPreset640x480]){
            mSession.sessionPreset = AVCaptureSessionPreset640x480;
        } else {
            // 852*640
            mSession.sessionPreset = AVCaptureSessionPresetPhoto;
        }
    }
    else {
        if(mWidth == 1280 && [mSession canSetSessionPreset: AVCaptureSessionPreset1280x720]){
            mSession.sessionPreset = AVCaptureSessionPreset1280x720;
        } else {
            mSession.sessionPreset = AVCaptureSessionPresetHigh;
        }
    }
    
    // Setup the still image file output
    photoOutput = [[[AVCaptureStillImageOutput alloc] init] autorelease];
    // raw BGRA for still image
    // not cool, too much memory used
    /*
    NSDictionary *outputSettings = [NSDictionary dictionaryWithObject:[NSNumber numberWithInt:kCVPixelFormatType_32BGRA] forKey:(id)kCVPixelBufferPixelFormatTypeKey];
    */
    NSDictionary *outputSettings = [[NSDictionary alloc] initWithObjectsAndKeys:
                                    AVVideoCodecJPEG, AVVideoCodecKey,
                                    nil];
    
    [photoOutput setOutputSettings:outputSettings];
    [outputSettings release];
    
    if ([mSession canAddOutput:photoOutput]) {
        [mSession addOutput:photoOutput];
    }
    
    [mSession commitConfiguration];
    
    return true;
}

- (void)focusAtPoint:(float)x y:(float)y
{
    CGPoint point = CGPointMake((CGFloat)x,  (CGFloat)y);
    AVCaptureDevice *device = [mCapDeviceInput device];
    
    if ([device isFocusPointOfInterestSupported] && [device isFocusModeSupported:AVCaptureFocusModeAutoFocus]) 
    {
        [device addObserver:self forKeyPath:@"adjustingFocus" options:NSKeyValueObservingOptionNew context:nil];
        NSError *error;
        if ([device lockForConfiguration:&error]) 
        {
            [device setFocusPointOfInterest:point];
            [device setFocusMode:AVCaptureFocusModeAutoFocus];
            [device unlockForConfiguration];
            
            NSLog(@"focusing device camera at {%f, %f}", x, y);
        } else {
            // smth goes wrong
            NSLog(@"failed to autoFocus");
        }        
    } else {
        NSLog(@"autoFocus not supported");
    }
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
    if ([keyPath isEqualToString:@"adjustingFocus"]) {
        BOOL adjustingFocus = [[change objectForKey:NSKeyValueChangeNewKey] isEqualToNumber:[NSNumber numberWithInt:1]];
        if (!adjustingFocus) {
            focusCompleteCallback();
            AVCaptureDevice *device = [mCapDeviceInput device];
            [device removeObserver:self forKeyPath:@"adjustingFocus"];
        }
    }
}
// Additional APIs //

- (void)exposureAtPoint:(float)x y:(float)y
{
    CGPoint point = CGPointMake(x, y);
    AVCaptureDevice *device = [mCapDeviceInput device];
    // iPhone 5 does not support AVCaptureExposureModeAutoExpose
    if ([device isExposurePointOfInterestSupported] && [device isExposureModeSupported:AVCaptureExposureModeContinuousAutoExposure])
    {
        NSError *error;
        if ([device lockForConfiguration:&error])
        {
            [device setExposurePointOfInterest:point];
            [device setExposureMode:AVCaptureExposureModeContinuousAutoExposure];
            [device unlockForConfiguration];
            NSLog(@"auto exposure in progress");
        } else {
            NSLog(@"auto exposure failed");
        }
    } else {
        NSLog(@"auto exposure not supported");
    }
}

- (void)setFlashMode:(int32_t)mode
{
    AVCaptureDevice *device = [mCapDeviceInput device];
    AVCaptureFlashMode flashMode = (AVCaptureFlashMode)mode;
    if ([device isFlashModeSupported:flashMode]) {
        NSError *error;
        if ([device lockForConfiguration:&error])
        {
            device.flashMode = flashMode;
            [device unlockForConfiguration];
            NSLog(@"flash mode changed to %d", flashMode);
        } else {
            NSLog(@"flash mode change failed");
        }
    } else {
        NSLog(@"flash mode not supported");
    }
}

- (int32_t)getFlashMode
{
    AVCaptureDevice *device = [mCapDeviceInput device];
    AVCaptureFlashMode mode = AVCaptureFlashModeOff;
    if (device.hasFlash) {
        mode = device.flashMode;
    }
    NSLog(@"current flash mode: %d", mode);
    return (int32_t)mode;
}

- (bool)isAdjustingFocus
{
    AVCaptureDevice *device = [mCapDeviceInput device];
    bool ret = device.isAdjustingFocus ? true : false;
    return ret;
}

- (bool)isAdjustingExposure
{
    AVCaptureDevice *device = [mCapDeviceInput device];
    bool ret = device.isAdjustingExposure ? true : false;
    return ret;
}

- (void)cameraSizeForCameraInput:(AVCaptureDeviceInput*)input
{
    NSArray *ports = [input ports];
    AVCaptureInputPort *usePort = nil;
    for ( AVCaptureInputPort *port in ports )
    {
        if ( usePort == nil || [port.mediaType isEqualToString:AVMediaTypeVideo] )
        {
            usePort = port;
        }
    }
    
    //if ( usePort == nil ) return CGSizeZero;
    if ( usePort == nil ) return;
    
    CMFormatDescriptionRef format = [usePort formatDescription];
    CMVideoDimensions dim = CMVideoFormatDescriptionGetDimensions(format);
    
    mWidth = dim.width;
    mHeight = dim.height;
    //CGSize cameraSize = CGSizeMake(dim.width, dim.height);
    
    //return cameraSize;
}

- (void)startCapture
{
    if( mIsCapturing )
        return; 

    @synchronized( self ) {
        if( [self prepareStartCapture] ) {
            mWorkingPixelBuffer = 0;
            mHasNewFrame = false;
            mLastTickTime = CFAbsoluteTimeGetCurrent();
        
            mIsCapturing = true;
            
            [mSession startRunning];
            
            [self cameraSizeForCameraInput:mCapDeviceInput];
            
            // setup focus
            NSError *error;
            AVCaptureDevice *device = [mCapDeviceInput device];
            
            if ([device isFocusModeSupported:AVCaptureFocusModeContinuousAutoFocus]) 
            {
                if ([device lockForConfiguration:&error]) 
                {
                    [device setFocusMode:AVCaptureFocusModeContinuousAutoFocus];
                    [device unlockForConfiguration];
                }
                NSLog(@"setting continuous focus");
            }
            else if([device isFocusModeSupported:AVCaptureFocusModeAutoFocus]) 
            {
                if ([device lockForConfiguration:&error]) 
                {
                    [device setFocusMode:AVCaptureFocusModeAutoFocus];
                    [device unlockForConfiguration];
                }
                NSLog(@"setting auto focus");
            }
            //
        }
    }
}

- (void)stopCapture
{
    if( ! mIsCapturing )
        return;

    @synchronized( self ) {
        
        if([mSession isRunning])[mSession stopRunning];
        
        [mSession release];
        [mCapDeviceInput release];
        
        mCapDeviceInput = nil;
        mSession = nil;
        
        [mCapOutput release];
        [photoOutput release];
        
        mCapOutput = nil;
        photoOutput = nil;

        mIsCapturing = false;
        mHasNewFrame = false;
        
        mCurrentFrame.reset();
        
        if( mWorkingPixelBuffer ) {
            CVBufferRelease( mWorkingPixelBuffer );
            mWorkingPixelBuffer = 0;
        }        
    }
}

- (bool)isCapturing
{
    return mIsCapturing;
}

- (void)captureAndSaveImage:(int)orientation latitude:(double)lat longitude:(double)lng {
    AVCaptureConnection *connection = [CaptureImplAvFoundation connectionWithMediaType:AVMediaTypeVideo fromConnections:[photoOutput connections]];
    [photoOutput captureStillImageAsynchronouslyFromConnection:connection completionHandler:^(CMSampleBufferRef imageDataSampleBuffer, NSError *error) {
        captureImageReadyCallback();
        NSData *data = [AVCaptureStillImageOutput jpegStillImageNSDataRepresentation:imageDataSampleBuffer];
        UIImage *image = [UIImage imageWithData:data];
        ALAssetsLibrary *library = [[ALAssetsLibrary alloc] init];
        
        // Add orientation metadata
        int meta = 0;
        if (orientation == 0) {
            meta = 1;
        } else if (orientation == 1) {
            meta = 6; // 90CW
        } else if (orientation == 2) {
            meta = 3; // 180
        } else if (orientation == 3) {
            meta = 8; // 270CW
        }
        
        NSString *latRef = (lat < 0.0) ? @"S" : @"N";
        NSString *lngRef = (lng < 0.0) ? @"W" : @"E";

        NSDictionary *gpsDic = [NSDictionary dictionaryWithObjectsAndKeys:
                                latRef, (NSString *)kCGImagePropertyGPSLatitudeRef,
                                [NSNumber numberWithFloat:fabs(lat)], (NSString *)kCGImagePropertyGPSLatitude,
                                lngRef, (NSString *)kCGImagePropertyGPSLongitudeRef,
                                [NSNumber numberWithFloat:fabs(lng)], (NSString *)kCGImagePropertyGPSLongitude,
                                nil];
        NSDictionary *metadata = [NSDictionary dictionaryWithObjectsAndKeys:
                                  [NSNumber numberWithInt:meta], (NSString *)kCGImagePropertyOrientation,
                                  gpsDic, (NSString *)kCGImagePropertyGPSDictionary,
                                  nil];
        [library writeImageToSavedPhotosAlbum:image.CGImage metadata:metadata
                              completionBlock:^(NSURL *assetURL, NSError *error) {
                                  imageSavedCallback();
            
        }];
    }];
}

- (bool)isFlashSupported {
    AVCaptureDevice *device = [mCapDeviceInput device];
    if (device && device.hasFlash) {
        return true;
    } else {
        return false;
    }
}

- (void)captureStillImage:(Capture *)f method:(imageCallback)call 
{
    AVCaptureConnection *stillImageConnection = [CaptureImplAvFoundation connectionWithMediaType:AVMediaTypeVideo fromConnections:[ photoOutput connections]];
    //if ([stillImageConnection isVideoOrientationSupported])
      //  [stillImageConnection setVideoOrientation:AVCaptureVideoOrientationPortrait];
    
    [photoOutput captureStillImageAsynchronouslyFromConnection:stillImageConnection
                                                         completionHandler:^(CMSampleBufferRef sampleBuffer, NSError *error) {
															 
															 if (sampleBuffer != NULL) 
                                                             {
                                                                 // raw BGRA access
                                                                 /*
                                                                 CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
                                                                 
                                                                 CVPixelBufferLockBaseAddress(imageBuffer, 0); 
                                                                 uint8_t *baseAddress = (uint8_t *)CVPixelBufferGetBaseAddress(imageBuffer); 
                                                                 size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer); 
                                                                 size_t width = CVPixelBufferGetWidth(imageBuffer); 
                                                                 size_t height = CVPixelBufferGetHeight(imageBuffer);
                                                                 size_t stride = CVPixelBufferGetBytesPerRow(imageBuffer);
                                                                 CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
                                                                 */
                                                                 //
                                                                 //(f->*call)(baseAddress, width, height, stride);
                                                                 
                                                                 NSData *imageData = [AVCaptureStillImageOutput jpegStillImageNSDataRepresentation:sampleBuffer];
                                                                 
                                                                 const uint8_t *data = (const uint8_t *)[imageData bytes];
                                                                 int32_t size = [imageData length];
                                                                 
                                                                 (f->*call)(data, size);
															 }
															 
															 
                                                         }];
}

// Delegate routine that is called when a sample buffer was written
- (void)captureOutput:(AVCaptureOutput *)captureOutput didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{ 
    @synchronized( self ) {
        if( mIsCapturing ) {
            // Dispatch event on the first call
            if (mPreviewReady == false) {
                mPreviewReady = true;
                previewReadyCallback();
            }
            // if the last pixel buffer went unclaimed, we'll need to release it
            if( mWorkingPixelBuffer ) {
                CVBufferRelease( mWorkingPixelBuffer );
                mWorkingPixelBuffer = NULL;
            }
            
            CVImageBufferRef videoFrame = CMSampleBufferGetImageBuffer(sampleBuffer);
            // Lock the base address of the pixel buffer
            //CVPixelBufferLockBaseAddress( videoFrame, 0 );
            
            CVBufferRetain( videoFrame );
        
            mWorkingPixelBuffer = (CVPixelBufferRef)videoFrame;
            mHasNewFrame = true;
            mLastTickTime = CFAbsoluteTimeGetCurrent();
        }
    }    
}

- (CaptureSurface8u)getCurrentFrame
{
    if( ( ! mIsCapturing ) || ( ! mWorkingPixelBuffer ) ) {
        return mCurrentFrame;
    }
    
    @synchronized (self) {
        CVPixelBufferLockBaseAddress( mWorkingPixelBuffer, 0 );
        
        uint8_t *data = (uint8_t *)CVPixelBufferGetBaseAddress( mWorkingPixelBuffer );
        mExposedFrameBytesPerRow = CVPixelBufferGetBytesPerRow( mWorkingPixelBuffer );
        mExposedFrameWidth = CVPixelBufferGetWidth( mWorkingPixelBuffer );
        mExposedFrameHeight = CVPixelBufferGetHeight( mWorkingPixelBuffer );

        mCurrentFrame = CaptureSurface8u( data, mExposedFrameWidth, mExposedFrameHeight, mExposedFrameBytesPerRow );
        mCurrentFrame.setDeallocator( frameDeallocator, mWorkingPixelBuffer );
        
        // mark the working pixel buffer as empty since we have wrapped it in the current frame
        mWorkingPixelBuffer = 0;
    }
    
    return mCurrentFrame;
}

- (bool)checkNewFrame
{
    bool result;
    @synchronized (self) {
        result = mHasNewFrame;
        mHasNewFrame = FALSE;
    }
    return result;
}

- (bool)checkResponse
{
    if (!mIsCapturing) {
        return false;
    }
    
    float deltaTimeInSeconds = CFAbsoluteTimeGetCurrent() - mLastTickTime;
    
    if(deltaTimeInSeconds > 4)
    {
        return false;
    }
    
    return true;
}

- (const Capture::DeviceRef)getDevice {
    return mDevice;
}

- (int32_t)getWidth
{
    return mWidth;
}

- (int32_t)getHeight
{
    return mHeight;
}

- (int32_t)getCurrentFrameBytesPerRow
{
    return mExposedFrameBytesPerRow;
}

- (int32_t)getCurrentFrameWidth
{
    return mExposedFrameWidth;
}

- (int32_t)getCurrentFrameHeight
{
    return mExposedFrameHeight;
}

@end