## Flutter setup from source aarch64
1. Install required packages 
```
apt install -y git dart
```
2. clone flutter stbale channel
```
git clone --depth 1 -b stable https://github.com/flutter/flutter.git
```
3. Patch the OS check
```
sed -i "/'linux': <String>\['linux-\$arch'/a\\ 'android': <String>['linux-\$arch', 'linux-\$arch/\$artifactName.zip']," ~/flutter/packages/flutter_tools/lib/src/flutter_cache.dart
sed -i 's/if (platform.isLinux) {/if (platform.isLinux || platform.isAndroid) {/' \ ~/flutter/packages/flutter_tools/lib/src/artifacts.dart
sed -i '60s/if (platform.isLinux)/if (platform.isLinux || platform.isAndroid)/' \ ~/flutter/packages/flutter_tools/lib/src/web/chrome.dart
```
4. Create cache directory
```
mkdir -p ~/flutter/bin/cache
```
5. Create stamp files BEFORE flutter runs
```
cat ~/flutter/bin/internal/engine.version > ~/flutter/bin/cache/engine.stamp
cp ~/flutter/bin/cache/engine.stamp ~/flutter/bin/cache/engine-dart-sdk.stamp
```
6. Create realm file
```
echo "" > ~/flutter/bin/cache/engine.realm
```
7. Symlink Termux's Dart SDK
```
ln -sf /data/data/com.neonide.studio/files/usr/lib/dart-sdk ~/flutter/bin/cache/dart-sdk
```
8. Verify installation
```
~/flutter/bin/flutter doctor -v
```
### Flutter Engine aarch64 
pre built flutter engine artifacts are required to build flutter projects,
you can download and copy the files from [flutter engine build](https://github.com/AndroidStudio-App/flutter-for-termux/releases/tag/v3.44.2)
