
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined (__ANDROID__)
#include <android/log.h>
#define  LOG_TAG    "CAPTURE"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGW(...)  __android_log_print(ANDROID_LOG_WARN,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#endif

#include "FlashRuntimeExtensions.h"
#include "CCapture.h"
#include "resize.h"

#if defined(_WIN32) || defined(__WIN32__) || defined(WIN32)
    #define ANE_MSW
    #define ANE_EXPORT __declspec( dllexport )
#elif defined(macintosh) || defined(__APPLE__) || defined(__APPLE_CC__)
    #define ANE_EXPORT __attribute__((visibility("default")))
    #include "TargetConditionals.h"
    #if TARGET_OS_IPHONE
        #define ANE_IOS
    #else
        #define ANE_MAC
    #endif
#else
    #define ANE_EXPORT 
#endif

#ifdef __cplusplus
extern "C" {
#endif

    ANE_EXPORT void captureInitializer(void** extData, FREContextInitializer* ctxInitializer, FREContextFinalizer* ctxFinalizer);
    ANE_EXPORT void captureFinalizer(void* extData);

#ifdef __cplusplus
} // end extern "C"
#endif

// 
// ADOBE AIR NATIVE EXTENSION WRAPPER
//


#define MAX_ACTIVE_CAMS (20)
#define FRAME_BITMAP (1 << 1)
#define FRAME_RAW (1 << 2)
#define FRAME_P2_BGRA (1 << 3)

static size_t active_cams_count = 0;

static CCapture* active_cams[MAX_ACTIVE_CAMS];

static FREContext _ctx = NULL;
static uint8_t *_cam_shot_data = 0;
static int32_t _cam_shot_size = 0;

// resize buffers
static int32_t resize_buf_size = 0;
static uint8_t *resize_buf = 0;

static size_t ba_write_int(void *ptr, int32_t val)
{
    int32_t *mptr = (int32_t*)ptr;
    *mptr = val;
    return sizeof(int32_t);
}
static size_t ba_read_int(void *ptr, int32_t *val)
{
    int32_t *mptr = (int32_t*)ptr;
    *val = (*mptr);
    return sizeof(int32_t);
}

static uint32_t nextPowerOfTwo(uint32_t v)
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

static __inline__ void* alignPtr(void* ptr, int n)
{
    return (void*)(((size_t)ptr + n-1) & -n);
}

static void* cvAlloc( size_t size)
{
    uint8_t* udata = (uint8_t*)malloc(size + sizeof(void*) + 32);
    if(!udata) return 0;
    uint8_t** adata = (uint8_t**)alignPtr((uint8_t**)udata + 1, 32);
    adata[-1] = udata;
    return adata;
}

static void cvFree( void* ptr )
{
    if(ptr)
    {
        uint8_t* udata = ((uint8_t**)ptr)[-1];
        free(udata);
    }
}

static uint8_t * allocResizeBuf(int32_t w, int32_t h, int32_t ch)
{
    const int32_t sz = w * h * ch;
    if(resize_buf_size < sz)
    {
        if(resize_buf) cvFree(resize_buf);
        resize_buf = (uint8_t*)cvAlloc(sz);
        resize_buf_size = sz;
    }

    return resize_buf;
}

static void dispose_ane()
{
    int32_t i;
    CCapture* cap;
    for(i = 0; i < MAX_ACTIVE_CAMS; i++)
    {
        cap = active_cams[i];

        if(cap)
        {
            releaseCapture(cap);

            active_cams[i] = 0;
        }
    }
    active_cams_count = 0;

    if(resize_buf_size)
    {
        cvFree(resize_buf);
        resize_buf_size = 0;
    }
    
    if(_cam_shot_data) free(_cam_shot_data);
    _ctx = NULL;
}

FREObject listDevices(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{

    int32_t i, numDevices = 0;
    CaptureDeviceInfo devices[MAX_ACTIVE_CAMS * 2];
    
    numDevices = getCaptureDevices(devices, 1);
    
    FREObject objectBA = argv[0];
    FREByteArray baData;
    
    FREAcquireByteArray(objectBA, &baData);
    
    uint8_t *ba = baData.bytes;
    
    ba += ba_write_int(ba, numDevices);
    
    for (i = 0; i < numDevices; i++)
    {
        const CaptureDeviceInfo *dev = &devices[i];
        
        ba += ba_write_int(ba, dev->name_size);
        memcpy( ba, (uint8_t*)dev->name, dev->name_size );
        ba += dev->name_size;
        
        ba += ba_write_int(ba, dev->available);
        ba += ba_write_int(ba, dev->connected);
    }
    
    FREReleaseByteArray(objectBA);
    return NULL;
}

int32_t active_cam_width = 0;
int32_t active_cam_height = 0;
int32_t active_cam_fps = 0;
int32_t active_cam_id = -1;
bool active_cam_front = false;

FREObject getCapture(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t camera_index = 0;
    int32_t w, h;
    int32_t frameRate = 0;
    uint32_t name_size = 0;
    const uint8_t* name_val = NULL;

    FREGetObjectAsInt32(argv[0], &camera_index);
    FREGetObjectAsInt32(argv[1], &w);
    FREGetObjectAsInt32(argv[2], &h);
    FREGetObjectAsInt32(argv[3], &frameRate);

    FREObject objectBA = argv[4];
    FREByteArray baData;
    FREAcquireByteArray(objectBA, &baData);
    uint8_t *ba = baData.bytes;

    int32_t emptySlot = -1;
    size_t i;
    // search empty slot
    for(i = 0; i < MAX_ACTIVE_CAMS; i++) {
        if(!active_cams[i]) {
            emptySlot = i;
            break;
        }
    }

    if (emptySlot == -1) {
        ba += ba_write_int(ba, -1);
        FREReleaseByteArray(objectBA);
        return NULL;
    }
    
    CCapture* cap = NULL;
    if (camera_index == 1) {
        char camera_name[] = "Front Camera";
        cap = createCameraCapture(w, h, camera_name, frameRate );
        active_cam_front = true;
    } else {
        char camera_name[] = "Back Camera";
        cap = createCameraCapture(w, h, camera_name, frameRate );
        active_cam_front = false;
    }
    
    if(!cap)
    {
        ba += ba_write_int(ba, -1);
        FREReleaseByteArray(objectBA);
        return NULL;
    }

    // start if not running
    if( captureIsCapturing(cap) == 0 )
    {
        captureStart(cap);
    }

    active_cams[emptySlot] = cap;
    active_cam_id = emptySlot;
    active_cams_count++;

    captureGetSize( cap, &w, &h );
    active_cam_width = w;
    active_cam_height = h;
    active_cam_fps = frameRate;
    
    // write result
    //ba += ba_write_int(ba, emptySlot);
    ba += ba_write_int(ba, w);
    ba += ba_write_int(ba, h);

    FREReleaseByteArray(objectBA);

    FREObject ret;
    FRENewObjectFromInt32(emptySlot, &ret);
    
    return ret;
}

FREObject delCapture(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t _id;
    FREGetObjectAsInt32(argv[0], &_id);


    if(_id < 0 || _id >= MAX_ACTIVE_CAMS)
    {
        return NULL;
    }

    CCapture* cap;
    cap = active_cams[_id];

    if(cap)
    {
        releaseCapture(cap);

        active_cams[_id] = 0;
        active_cams_count--;
    }

    return NULL;
}

FREObject toggleCapturing(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t _id, _opt;
    FREGetObjectAsInt32(argv[0], &_id);
    FREGetObjectAsInt32(argv[1], &_opt);

    CCapture* cap;
    cap = active_cams[_id];

    if(cap)
    {
        if(_opt == 0)
        {
            captureStop(cap);
        }
        else if(_opt == 1)
        {
            // TODO can fail when starting after stop so we need to handle result
            captureStart(cap);
        }
    }

    return NULL;
}

FREObject stopCapturing(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[]) {
    
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if(cap) {
        //captureStop(cap);
        //releaseCapture(cap);
        
        //active_cams[active_cam_id] = 0;
        //active_cams_count--;
    }
    
    return NULL;
}

FREObject getCaptureFrame(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t w, h, w2, h2, widthInBytes, i, j, id;
    FREGetObjectAsInt32(argv[1], &id);
    FREGetObjectAsInt32(argv[2], &w2);
    FREGetObjectAsInt32(argv[3], &h2);
    int32_t opt = FRAME_BITMAP;
    
    FREObject res_obj;
    FRENewObjectFromInt32(0, &res_obj);

    FREObject objectBA;
    FREByteArray baData;
    FREBitmapData2 bitmapData;
    uint8_t *ba;
    const uint8_t* frame_0;
    const uint8_t* frame;

    uint32_t fstride;
    
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    uint32_t flipped = 0;
    uint32_t isWin = 1;
    #if defined(ANE_MAC)
        isWin = 0;
    #endif

    if (cap && captureCheckNewFrame(cap)) {
        frame_0 = (const uint8_t*)captureGetFrame(cap, &w, &h, &widthInBytes);

        // here is some conversion we need cause in Flash BitmapData bytes
        // represented differently :(
        if((opt & FRAME_BITMAP))
        {
            objectBA = argv[0];
            FREAcquireBitmapData2(objectBA, &bitmapData);

            uint32_t* input = bitmapData.bits32;
            fstride = bitmapData.lineStride32 * sizeof(uint32_t); // for some reasons not always == width
            flipped = bitmapData.isInvertedY;

            frame = frame_0;
            ba = (uint8_t*)input;
            
            #if defined (__ANDROID__) || defined (ANE_IOS)

                for(i=0; i<h; i++){
                    memcpy(ba+i*fstride, frame+i*widthInBytes, fstride);
                }

            #else

            // most likely wont happen / but who knows? ;)
            if(flipped && !isWin)
            {
                const uint8_t* a_ptr = (const uint8_t*)frame;
                const uint8_t* a_ptr2 = (const uint8_t*)(frame + (h - 1) * widthInBytes);
                uint8_t* b_ptr = ba + (h - 1) * fstride;
                uint8_t* b_ptr2 = ba;
                for(i = 0; i < h / 2; i++)
                {
                    uint8_t *ba_ln = b_ptr;
                    uint8_t *ba_ln2 = b_ptr2;
                    const uint8_t *a_ln = a_ptr;
                    const uint8_t *a_ln2 = a_ptr2;
                    #if defined(ANE_MSW)
                    for(j = 0; j < w; j++, ba_ln += 4, ba_ln2 += 4, a_ln += 3, a_ln2 += 3)
                    {
                            *ba_ln = *(a_ln);
                            ba_ln[1] = *(a_ln+1);
                            ba_ln[2] = *(a_ln+2);
                            //
                            *ba_ln2 = *(a_ln2);
                            ba_ln2[1] = *(a_ln2+1);
                            ba_ln2[2] = *(a_ln2+2);
                    }
                    #elif defined(ANE_MAC)
                    memcpy(ba_ln, a_ln, widthInBytes);
                    memcpy(ba_ln2, a_ln2, widthInBytes);
                    #endif
                    
                    a_ptr += widthInBytes;
                    a_ptr2 -= widthInBytes;
                    b_ptr -= fstride;
                    b_ptr2 += fstride;
                }
                if(h&1)
                {
                    #if defined(ANE_MSW)
                        for(j = 0; j < w; j++, b_ptr += 4, a_ptr+=3)
                        {
                            *b_ptr = *(a_ptr);
                            b_ptr[1] = *(a_ptr+1);
                            b_ptr[2] = *(a_ptr+2);
                        }
                    #elif defined(ANE_MAC)
                        memcpy(b_ptr, a_ptr, widthInBytes);
                    #endif
                }
            }
            else
            {
                for(i = 0; i < h; i++)
                {
                    uint8_t *ba_ln = ba;
                    const uint8_t *fr_ln = frame;
                    #if defined(ANE_MSW)
                        for(j = 0; j < w; j++, ba_ln += 4, fr_ln += 3)
                        {                        
                            *ba_ln = *(fr_ln);
                            ba_ln[1] = *(fr_ln+1);
                            ba_ln[2] = *(fr_ln+2);
                        }
                    #elif defined(ANE_MAC) 
                        memcpy(ba_ln, fr_ln, widthInBytes);
                    #endif
                    ba += fstride;
                    frame += widthInBytes;
                }
            }

            #endif


            FREReleaseBitmapData(objectBA);
        } 

        if((opt & FRAME_RAW))
        {
            objectBA = argv[3];
            FREAcquireByteArray(objectBA, &baData);
            ba = baData.bytes;

            memcpy(ba, frame_0, widthInBytes * h);

            FREReleaseByteArray(objectBA);
        }

        // power of 2 output for stage3d
        if((opt & FRAME_P2_BGRA))
        {
            objectBA = argv[4];
            FREAcquireByteArray(objectBA, &baData);
            ba = baData.bytes;

            frame = frame_0;

            // resize
            /*
            if(w > w2 || h > h2)
            {
                float scx = (float)w2 / (float)w;
                float scy = (float)h2 / (float)h;
                if(scy < scx) scx = scy;
                int32_t wr = w * scx;
                int32_t hr = h * scx;
                uint8_t *frame_rs = allocResizeBuf(wr, hr, 4);
                //resample_area_8u(frame, w, h, frame_rs, wr, hr, 4);

                // update values
                w = wr;
                h = hr;
                frame = frame_rs;
                widthInBytes = wr * sizeof(uint32_t);
            }
            */

            int32_t p2w = (w2);
            int32_t p2h = (h2);
            int32_t off_x = (p2w - w) / 2; // -64
            int32_t off_y = (p2h - h) / 2;
            size_t p2stride = p2w * sizeof(uint32_t);

            int32_t b_off_x, b_off_y, a_off_x, a_off_y;

            if(off_x < 0)
            {
                b_off_x = 0;
                a_off_x = -off_x;
            } else {
                b_off_x = off_x;
                a_off_x = 0;
            }

            if(off_y < 0)
            {
                b_off_y = 0;
                a_off_y = -off_y;
            } else {
                b_off_y = off_y;
                a_off_y = 0;
            }

            uint8_t* b_ptr0 = ba + b_off_y * p2stride + b_off_x*4;

            int32_t nw = w - a_off_x*2;
            int32_t nh = h - a_off_y*2;

            #if defined (ANE_MSW) // we need flip Y

                const uint8_t* a_ptr0 = (const uint8_t*)(frame + a_off_y * widthInBytes + a_off_x*3);
                const uint8_t* a_ptr = a_ptr0;
                const uint8_t* a_ptr2 = a_ptr0 + (nh - 1) * widthInBytes;
                uint8_t* b_ptr = b_ptr0 + (h - 1) * p2stride;
                uint8_t* b_ptr2 = b_ptr0;
                const uint8_t alpha = 0xFF;
                for(i = 0; i < nh / 2; i++)
                {
                    uint8_t *ba_ln = b_ptr;
                    uint8_t *ba_ln2 = b_ptr2;
                    const uint8_t *a_ln = a_ptr;
                    const uint8_t *a_ln2 = a_ptr2;
                    for(j = 0; j < nw; j++, ba_ln += 4, ba_ln2 += 4, a_ln += 3, a_ln2 += 3)
                    {

                        *ba_ln = *(a_ln+0);
                        ba_ln[1] = *(a_ln+1);
                        ba_ln[2] = *(a_ln+2);
                        ba_ln[3] = alpha;
                        //
                        *ba_ln2 = *(a_ln2+0);
                        ba_ln2[1] = *(a_ln2+1);
                        ba_ln2[2] = *(a_ln2+2);
                        ba_ln2[3] = alpha;
                    }
                    
                    a_ptr += widthInBytes;
                    a_ptr2 -= widthInBytes;
                    b_ptr -= p2stride;
                    b_ptr2 += p2stride;
                }
                if(nh&1)
                {
                    for(j = 0; j < nw; j++, b_ptr += 4, a_ptr+=3)
                    {
                        *b_ptr = *(a_ptr+0);
                        b_ptr[1] = *(a_ptr+1);
                        b_ptr[2] = *(a_ptr+2);
                        b_ptr[3] = alpha;
                    }
                }

            #elif defined(ANE_MAC) || defined (ANE_IOS) || defined (__ANDROID__) 

                const uint8_t* a_ptr0 = (const uint8_t*)(frame + a_off_y * widthInBytes + a_off_x*4);

                for(i = 0; i < nh; i++)
                {
                    memcpy(b_ptr0, a_ptr0, nw*4);
                    a_ptr0 += widthInBytes;
                    b_ptr0 += p2stride;
                }

            #endif
            
            FREReleaseByteArray(objectBA);
        }

        FRENewObjectFromInt32(1, &res_obj);
    } 
    else if(cap)
    {
        // lets see if device is available it may be freezed
        if( captureCheckResponse(cap) == 0 )
        {
            FRENewObjectFromInt32(-1, &res_obj);
        }
    }

    return res_obj;
}

FREObject supportsSaveToCameraRoll(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    FREObject res_obj;

    int32_t res = captureSupportsSaveToCameraRoll();
    FRENewObjectFromInt32(res, &res_obj);
    
    return res_obj;
}

FREObject saveToCameraRoll(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    uint32_t name_size = 0;
    const uint8_t* name_val = NULL;
    
    FREGetObjectAsUTF8(argv[0], &name_size, &name_val);
    
    FREObject objectBA = argv[1];
    FREByteArray baData;
    FREAcquireByteArray(objectBA, &baData);
    uint8_t *ba = baData.bytes;
    
    int32_t _size;
    FREGetObjectAsInt32(argv[2], &_size);
    int32_t _orientation;
    FREGetObjectAsInt32(argv[3], &_orientation);
    
    int32_t res = captureSaveToCameraRoll( (const char *)name_val, (const uint8_t*)ba, _size, _orientation);
    
    FREReleaseByteArray(objectBA);
    
    FREObject res_obj;
    FRENewObjectFromInt32(res, &res_obj);
    
    return res_obj;
}

FREObject focusAndExposureAtPoint(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    double _x, _y;
    
    FREGetObjectAsDouble(argv[0], &_x);
    FREGetObjectAsDouble(argv[1], &_y);
    
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if(cap)
    {
        captureFocusAtPoint(cap, (float)_x, (float)_y);
        captureExposureAtPoint(cap, (float)_x, (float)_y);
    }
    return NULL;
}

void focusCompleteCallback()
{
    FREDispatchStatusEventAsync(_ctx, (const uint8_t *)"FOCUS_COMPLETE", (const uint8_t *)"0");
}

// Additional APIs //

FREObject flipCamera(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    FREObject objectBA = argv[0];
    FREByteArray baData;
    FREAcquireByteArray(objectBA, &baData);
    uint8_t *ba = baData.bytes;
    
    int32_t emptySlot = -1;
    size_t i;
    // search empty slot
    for(i = 0; i < MAX_ACTIVE_CAMS; i++) {
        if(!active_cams[i]) {
            emptySlot = i;
            break;
        }
    }
    
    if (emptySlot == -1) {
        ba += ba_write_int(ba, -1);
        FREReleaseByteArray(objectBA);
        return NULL;
    }
    
    CCapture* cap = NULL;
    if (active_cam_front) {
        char camera_name[] = "Back Camera";
        cap = createCameraCapture(active_cam_width, active_cam_height, camera_name, active_cam_fps);
        active_cam_front = false;
    } else {
        char camera_name[] = "Front Camera";
        cap = createCameraCapture(active_cam_width, active_cam_height, camera_name, active_cam_fps);
        active_cam_front = true;
    }
    
    if(!cap) {
        ba += ba_write_int(ba, -1);
        FREReleaseByteArray(objectBA);
        return NULL;
    }
    
    // start if not running
    if( captureIsCapturing(cap) == 0 ) {
        captureStart(cap);
    }
    
    active_cams[emptySlot] = cap;
    active_cam_id = emptySlot;
    active_cams_count++;
    
    captureGetSize(cap, &active_cam_width, &active_cam_height);
    
    // write result
    //ba += ba_write_int(ba, emptySlot);
    ba += ba_write_int(ba, active_cam_width);
    ba += ba_write_int(ba, active_cam_height);
    
    FREReleaseByteArray(objectBA);
    return NULL;
}

FREObject setFlashMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t mode;
    FREGetObjectAsInt32(argv[0], &mode);
    
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if (cap)
    {
        captureSetFlashMode(cap, mode);
    }
    return NULL;
}

FREObject getFlashMode(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if (cap)
    {
        int32_t mode = (int32_t)captureGetFlashMode(cap);
        FREObject ret;
        FRENewObjectFromInt32(mode, &ret);
        return ret;
    }
    return NULL;
}

FREObject isAdjustingFocus(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if (cap)
    {
        bool focusing = captureIsAdjustingFocus(cap);
        FREObject ret;
        FRENewObjectFromBool(focusing, &ret);
        return ret;
    }
    return NULL;
}

FREObject isAdjustingExposure(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if (cap)
    {
        bool exposing = captureIsAdjustingExposure(cap);
        FREObject ret;
        FRENewObjectFromBool(exposing, &ret);
        return ret;
    }
    return NULL;
}

void statusBarTappedCallback()
{
    FREDispatchStatusEventAsync(_ctx, (const uint8_t*)"STATUS_BAR", (const uint8_t*)"0");
}

void stillImageCallback(const uint8_t *data, int32_t size)
{
    if(_cam_shot_data) free(_cam_shot_data);
    _cam_shot_data = (uint8_t*)malloc(size);
    
    memcpy(_cam_shot_data, data, size);
    
    _cam_shot_size = size;
    FREDispatchStatusEventAsync(_ctx, (const uint8_t*)"CAM_SHOT", (const uint8_t*)"0");
}

void previewReadyCallback()
{
    FREDispatchStatusEventAsync(_ctx, (const uint8_t*)"PREVIEW_READY", (const uint8_t*)"0");
}

FREObject grabCamShot(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    FREObject ret = NULL;
    
    if (_cam_shot_data) {
        FREObject length = NULL;
        FRENewObjectFromUint32(_cam_shot_size, &length);
        FRESetObjectProperty(argv[0], (const uint8_t*) "length", length, NULL);
    
        FREObject objectBA = argv[0];
        FREByteArray baData;
        FREAcquireByteArray(objectBA, &baData);
        uint8_t *ba = baData.bytes;
        
        memcpy(ba, _cam_shot_data, _cam_shot_size);
    
        FREReleaseByteArray(objectBA);
        
        free(_cam_shot_data);
        _cam_shot_size = 0;
        _cam_shot_data = NULL;
        
        FRENewObjectFromUint32(1, &ret);
    } else {
        FRENewObjectFromUint32(0, &ret);
    }
    
    return ret;
}

FREObject captureStillImage(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t _id;
    FREGetObjectAsInt32(argv[0], &_id);
    
    CCapture* cap;
    cap = active_cams[_id];
    
    if(cap)
    {
        captureGetStillImage(cap, stillImageCallback);
    }
    
    return NULL;
}

FREObject disposeANE(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    dispose_ane();
    return NULL;
}

void imageSavedCallback()
{
    FREDispatchStatusEventAsync(_ctx, (const uint8_t *)"IMAGE_SAVED", (const uint8_t *)"0");
}

FREObject captureAndSaveImage(FREContext ctx, void* funcData, uint32_t argc, FREObject argv[])
{
    int32_t id, orientation;
    FREGetObjectAsInt32(argv[1], &orientation);

    CCapture *cap = NULL;
    if (0 <= active_cam_id && active_cam_id < MAX_ACTIVE_CAMS) {
        cap = active_cams[active_cam_id];
    }
    
    if(cap) {
        captureAndSave(cap, orientation);
    }
    return NULL;
}

//
// default init routines
//
void contextInitializer(void* extData, const uint8_t* ctxType, FREContext ctx, uint32_t* numFunctions, const FRENamedFunction** functions)
{
    _ctx = ctx;
    
    *numFunctions = 9;

    FRENamedFunction* func = (FRENamedFunction*) malloc(sizeof(FRENamedFunction) * (*numFunctions));

    func[0].name = (const uint8_t *)"startCamera";
    func[0].functionData = NULL;
    func[0].function = &getCapture;
    
    func[1].name = (const uint8_t *)"endCamera";
    func[1].functionData = NULL;
    func[1].function = &stopCapturing;

    func[2].name = (const uint8_t *)"requestFrame";
    func[2].functionData = NULL;
    func[2].function = &getCaptureFrame;
    
    func[3].name = (const uint8_t*) "listDevices";
    func[3].functionData = NULL;
    func[3].function = &listDevices;

    func[4].name = (const uint8_t*) "flipCamera";
    func[4].functionData = NULL;
    func[4].function = &flipCamera;

    func[5].name = (const uint8_t*) "focusAtPoint";
    func[5].functionData = NULL;
    func[5].function = &focusAndExposureAtPoint;
        
    func[6].name = (const uint8_t*) "setFlashMode";
    func[6].functionData = NULL;
    func[6].function = &setFlashMode;
    
    func[7].name = (const uint8_t*) "getFlashMode";
    func[7].functionData = NULL;
    func[7].function = &getFlashMode;
    
    func[8].name = (const uint8_t*) "captureAndSaveImage";
    func[8].functionData = NULL;
    func[8].function = &captureAndSaveImage;
    
    *functions = func;

    memset(active_cams, 0, sizeof(CCapture*) * MAX_ACTIVE_CAMS);
}

void contextFinalizer(FREContext ctx)
{
    dispose_ane();
}

void captureInitializer(void** extData, FREContextInitializer* ctxInitializer, FREContextFinalizer* ctxFinalizer)
{
    *ctxInitializer = &contextInitializer;
    *ctxFinalizer = &contextFinalizer;
}

void captureFinalizer(void* extData)
{
    return;
}
