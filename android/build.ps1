$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = "$sdk\build-tools\34.0.0"
$jar  = "$sdk\platforms\android-34\android.jar"
$jdk  = "C:\Program Files\Android\Android Studio\jbr\bin"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$b = "$root\build"
Remove-Item -Recurse -Force $b -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$b\libs\arm64-v8a","$b\libs\armeabi-v7a","$b\libs\x86_64","$b\classes","$b\dex" | Out-Null

# 1. Rust core for the three Android CPU types
$ndk = (Get-ChildItem "$sdk\ndk" | Where-Object { Test-Path "$($_.FullName)\toolchains\llvm\prebuilt\windows-x86_64" } | Sort-Object Name | Select-Object -Last 1).FullName
if (-not $ndk) { throw "No complete Android NDK found under $sdk\ndk" }
$tc = "$ndk\toolchains\llvm\prebuilt\windows-x86_64\bin"
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER   = "$tc\aarch64-linux-android26-clang.cmd"
$env:CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER = "$tc\armv7a-linux-androideabi26-clang.cmd"
$env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER    = "$tc\x86_64-linux-android26-clang.cmd"
Push-Location "$root\core"
$map = @{ "aarch64-linux-android"="arm64-v8a"; "armv7-linux-androideabi"="armeabi-v7a"; "x86_64-linux-android"="x86_64" }
foreach ($t in $map.Keys) {
  cargo build --release --target $t
  if ($LASTEXITCODE) { throw "cargo build failed for $t" }
  Copy-Item "target\$t\release\libfreescroll_core.so" "$b\libs\$($map[$t])\"
}
Pop-Location

# 2. resources + manifest
& "$bt\aapt2.exe" compile --dir "$root\app\res" -o "$b\res.zip"; if ($LASTEXITCODE) { throw "aapt2 compile" }
& "$bt\aapt2.exe" link -o "$b\base.apk" -I $jar --manifest "$root\app\AndroidManifest.xml" `
    --min-sdk-version 26 --target-sdk-version 34 --version-code 3 --version-name 0.1.2 "$b\res.zip"
if ($LASTEXITCODE) { throw "aapt2 link" }

# 3. Java shell -> dex
$src = Get-ChildItem "$root\app\src" -Recurse -Filter *.java | % FullName
& "$jdk\javac.exe" --release 8 -cp $jar -d "$b\classes" $src; if ($LASTEXITCODE) { throw "javac" }
$cls = Get-ChildItem "$b\classes" -Recurse -Filter *.class | % FullName
& "$sdk\build-tools\36.0.0\d8.bat" --lib $jar --min-api 26 --output "$b\dex" $cls; if ($LASTEXITCODE) { throw "d8" }

# 4. assemble, align, sign
python -P "$root\pack_apk.py" "$b\base.apk" "$b\dex\classes.dex" "$b\libs" "$b\unsigned.apk"
& "$bt\zipalign.exe" -f 4 "$b\unsigned.apk" "$b\aligned.apk"; if ($LASTEXITCODE) { throw "zipalign" }
$ks = "$root\debug.keystore"
if (-not (Test-Path $ks)) {
  & "$jdk\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias fs -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=FreeScroll Debug"
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out "$root\FreeScroll.apk" "$b\aligned.apk"
if ($LASTEXITCODE) { throw "apksigner" }
& "$bt\apksigner.bat" verify --verbose "$root\FreeScroll.apk" | Select-Object -First 3
"BUILT: $root\FreeScroll.apk  ($([int]((Get-Item "$root\FreeScroll.apk").Length/1KB)) KB)"
