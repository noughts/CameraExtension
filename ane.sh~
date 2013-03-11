MY_DIR=${0%/*}
source $MY_DIR/config.cfg

ANE_FILE=capture.ane
SWC_FILE=CaptureInterface.swc
JAR_FILE=android.jar
LIB_FILE=libcaptureIOS.a

echo "Copying .a file..."
mkdir -p ./bin/tmp/platform/ios/
mkdir -p ./bin/tmp/platform/default/
cp ${LIB_PATH}/${LIB_FILE} ./platform/ios/
cp -r ./platform/ios/ ./bin/tmp/platform/ios/
cp -r ./platform/android/ ./bin/tmp/platform/android/
echo "Extracting .swf file..."
unzip -o ./bin/${SWC_FILE} library.swf -d ./bin/tmp/platform/ios/
unzip -o ./bin/${SWC_FILE} library.swf -d ./bin/tmp/platform/android/
unzip -o ./bin/${SWC_FILE} -d ./bin/tmp/platform/default/
echo "Embedding class files to jar..."
jar -uf ./platform/android/bin/${JAR_FILE} -C ./platform/android/classes com
cp ./platform/android/bin/${JAR_FILE} ./bin/tmp/platform/android/
echo "Packaging ANE file..."
"${ADT}" \
-package \
-target ane ./${ANE_FILE} ./extension.xml \
-swc ./bin/${SWC_FILE} \
-platform iPhone-ARM \
-C ./bin/tmp/platform/ios . \
-platform Android-ARM \
-C ./bin/tmp/platform/android . \
-platform default \
-C ./bin/tmp/platform/default . 
echo "Done."

echo "Copying ANE file to Sample App..."
cp -R ./${ANE_FILE} ../SampleApp/
echo "Done!"