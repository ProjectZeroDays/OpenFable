@echo off
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
set ANDROID_SDK_ROOT=C:\Users\Project Zero\AppData\Local\Android\Sdk
set ANDROID_HOME=C:\Users\Project Zero\AppData\Local\Android\Sdk
cd /d "C:\Projects\OpenFable\android"
call gradlew.bat --no-daemon assembleFullRelease 2>&1
echo "=== FULL BUILD DONE ==="
call gradlew.bat --no-daemon assembleLimitedRelease 2>&1
echo "=== LIMITED BUILD DONE ==="
