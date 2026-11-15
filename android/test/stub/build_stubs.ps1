param([string[]]$Packages)
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$sdk = "$env:LOCALAPPDATA\Android\Sdk"; $bt = "$sdk\build-tools\34.0.0"; $jar = "$sdk\platforms\android-34\android.jar"
$jdk = "C:\Program Files\Android\Android Studio\jbr\bin"; $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$ks = "c:\Users\Rahul Halder\Desktop\freescroll-repo\android\debug.keystore"
$out = "$here\out"; New-Item -ItemType Directory -Force "$out\classes","$out\dex","$out\libs" | Out-Null
& "$jdk\javac.exe" --release 8 -cp $jar -d "$out\classes" "$here\src\com\stub\Main.java" 2>&1 | Out-Null
$cls = Get-ChildItem "$out\classes" -Recurse -Filter *.class | % FullName
& "$sdk\build-tools\36.0.0\d8.bat" --lib $jar --min-api 26 --output "$out\dex" $cls
foreach ($p in $Packages) {
  $m = "$out\$p.xml"
  @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$p">
  <application android:label="Stub $p">
    <activity android:name="com.stub.Main" android:exported="true">
      <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>
"@ | Set-Content $m -Encoding utf8
  & "$bt\aapt2.exe" link -o "$out\$p.base.apk" -I $jar --manifest $m --min-sdk-version 26 --target-sdk-version 34
  python -P "c:\Users\Rahul Halder\Desktop\freescroll-repo\android\pack_apk.py" "$out\$p.base.apk" "$out\dex\classes.dex" "$out\libs" "$out\$p.unsigned.apk" | Out-Null
  & "$bt\zipalign.exe" -f 4 "$out\$p.unsigned.apk" "$out\$p.aligned.apk"
  & "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$out\$p.apk" "$out\$p.aligned.apk"
  "built $p"
}
