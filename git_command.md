. Gỡ app:
adb uninstall com.yourname.smartrecorder
2. Cài app:
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
3. Cài app và tự chạy:
adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.example.autoenglish/.MainActivity
4. Build + Cài + Chạy (all-in-one):
.\gradlew.bat assembleDebug; adb install -r "app\build\outputs\apk\debug\app-debug.apk"; adb shell am start -n com.example.autoenglish/.MainActivity
5. Xem logcat (để debug):
adb logcat -s "AudioConverter:*" "VoskEngine:*" "VoskAudioTranscriber:*" "ModelProvider:*"
6. Xem tất cả log:
adb logcat
7. Clear app data (reset app):
adb shell pm clear com.example.autoenglis