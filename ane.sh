MY_DIR=${0%/*}
source $MY_DIR/config.cfg

ANE_FILE=capture.ane
SWC_FILE=CaptureInterface.swc
JAR_FILE=android.jar
LIB_FILE=libcaptureIOS.a

echo "Copying .a file..."
cp ${LIB_PATH}/${LIB_FILE} ./platform/ios/
echo "Extracting .swf file..."
unzip -o ./bin/${SWC_FILE} library.swf -d ./platform/ios/
unzip -o ./bin/${SWC_FILE} library.swf -d ./platform/android/
unzip -o ./bin/${SWC_FILE} -d ./platform/default/
echo "Embedding class files to jar..."
jar -uf ./platform/android/bin/${JAR_FILE} -C ./platform/android/classes com
cp ./platform/android/bin/${JAR_FILE} ./platform/android/
echo "Packaging ANE file..."
"${ADT}" \
-package \
-target ane ./${ANE_FILE} ./extension.xml \
-swc ./bin/${SWC_FILE} \
-platform iPhone-ARM \
-C ./platform/ios . \
-platform Android-ARM \
-C ./platform/android . \
-platform default \
-C ./platform/default . 
echo "Done."

echo "Copying ANE file to Sample App..."
cp -R ./${ANE_FILE} ../SampleApp/
echo "Done!"