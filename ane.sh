MY_DIR=${0%/*}
source $MY_DIR/config.cfg

ANE_FILE=capture.ane
SWC_FILE=CaptureInterface.swc
JAR_FILE=captureandroid.jar
LIB_FILE=libcaptureIOS.a

echo "Copying .a file..."
cp ${LIB_PATH}/${LIB_FILE} ./platform/ios/
echo "Extracting .swf file..."
unzip -o ./bin/${SWC_FILE} library.swf -d ./platform/ios/
unzip -o ./bin/${SWC_FILE} library.swf -d ./platform/android/
cp ./platform/android/bin/${JAR_FILE} ./platform/android/
echo "Packaging ANE file..."
"${ADT}" \
-package \
-target ane ./${ANE_FILE} ./extension.xml \
-swc ./bin/${SWC_FILE} \
-platform iPhone-ARM \
-C ./platform/ios . \
-platform Android-ARM \
-C ./platform/android . 
echo "Done."
