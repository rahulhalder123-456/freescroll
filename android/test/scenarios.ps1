# Lifecycle scenarios for FreeScroll, run against an emulator/device with stand-in apps installed
# (see stub/build_stubs.ps1). Each scenario resets FreeScroll's state, then drives the phone with adb.
# Usage: .\scenarios.ps1 -A com.facebook.katana -B com.instagram.android
param([string]$A = "com.facebook.katana", [string]$B = "com.instagram.android", [string[]]$Only = @())
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$svc = "com.freescroll/com.freescroll.FreeScrollService"

function Focus   { ((& $adb shell dumpsys window | Select-String "mCurrentFocus" | Out-String).Trim()) -replace "mCurrentFocus=Window\{\w+ u0 ","" }
function OnFS    { (Focus) -match "com\.freescroll" }
function InApp($p) { (Focus) -match [regex]::Escape($p) }
function Vol     { [int](((& $adb shell cmd media_session volume --stream 3 --get 2>&1) | Select-String "volume is (\d+)").Matches[0].Groups[1].Value) }
function Reset   { & $adb shell input keyevent 3; Start-Sleep 2   # leave the previous app so FreeScroll gives the sound back before its state is wiped
                   & $adb shell cmd media_session volume --stream 3 --set 5 2>&1 | Out-Null
                   & $adb shell settings put system accelerometer_rotation 0; & $adb shell settings put system user_rotation 0
                   & $adb shell pm clear com.freescroll | Out-Null; & $adb shell settings put secure enabled_accessibility_services ""; Start-Sleep 1
                   & $adb shell settings put secure enabled_accessibility_services $svc; Start-Sleep 3; & $adb shell input keyevent 3; Start-Sleep 1 }
function Launch($p) { & $adb shell am start -n "$p/com.stub.Main" 2>&1 | Out-Null; Start-Sleep 3 }
function PassGate { & $adb shell input tap 540 1237; Start-Sleep 1; & $adb shell input text "testing"; Start-Sleep 1; & $adb shell input keyevent 4; Start-Sleep 10; & $adb shell input tap 540 1390; Start-Sleep 2 }
function Home    { & $adb shell input keyevent 3; Start-Sleep 2 }
function Recents { & $adb shell input keyevent 187; Start-Sleep 3 }
function ResumeFromRecents { Recents; & $adb shell input tap 540 1200; Start-Sleep 3 }   # newest app is the centre card
function ScrollUntilStop([int]$max = 25) { for ($i = 1; $i -le $max; $i++) { & $adb shell input swipe 540 1900 540 500 200; Start-Sleep -Milliseconds 450; if (OnFS) { return $i } }; return 0 }

$results = @()
function Check($name, $ok, $detail = "") { $script:results += [pscustomobject]@{ scenario = $name; result = $(if ($ok) { "ok" } else { "FAIL" }); detail = $detail } }
function Run($id, [scriptblock]$body) { if ($Only.Count -eq 0 -or $Only -contains $id) { Reset; & $body } }

Run "S1" {  # fresh open shows the gate
  Launch $A; Check "S1 fresh open -> gate" (OnFS) (Focus) }

Run "S2" {  # leave the stop screen unanswered, come back from Recents: must still be stopped
  Launch $A; PassGate; $n = ScrollUntilStop; Check "S2a stop reached" ($n -gt 0)
  Home; ResumeFromRecents
  Check "S2b resume from Recents after unanswered stop -> stop again" (OnFS) (Focus) }

Run "S3" {  # pass the gate, minimise, resume from Recents, keep scrolling: stop must still trigger
  Launch $A; PassGate; Home; ResumeFromRecents
  Check "S3a resume from Recents shows the app (gate still valid)" (InApp $A) (Focus)
  $n = ScrollUntilStop; Check "S3b scrolling after resume still hits the stop" ($n -gt 0) "swipes=$n" }

Run "S4" {  # resume with the launcher/intent instead of Recents
  Launch $A; PassGate; Home; Launch $A
  $n = ScrollUntilStop; Check "S4 resume via launcher, stop still triggers" ($n -gt 0) "swipes=$n" }

Run "S5" {  # leave during the gate: coming back must ask again
  Launch $A; Check "S5a gate shown" (OnFS); Home; ResumeFromRecents
  Check "S5b gate again after leaving it unanswered" (OnFS) (Focus) }

Run "S6" {  # break active, resume from Recents: locked
  Launch $A; PassGate; [void](ScrollUntilStop); & $adb shell input tap 540 1331; Start-Sleep 2   # take the break
  Home; ResumeFromRecents
  Check "S6 break still locked after Recents resume" (OnFS) (Focus) }

Run "S7" {  # two apps: gate for B must not be skipped because A was passed, and A stays passed
  Launch $A; PassGate; Home; Launch $B
  Check "S7a second app asks for its own gate" (OnFS) (Focus)
  PassGate; Home; Launch $A
  Check "S7b first app still passed (no new gate)" (InApp $A) (Focus) }

Run "S8" {  # screen off/on while a passed app is in front, then keep scrolling
  Launch $A; PassGate; & $adb shell input keyevent 26; Start-Sleep 2; & $adb shell input keyevent 26; Start-Sleep 2; & $adb shell input keyevent 82; Start-Sleep 2
  $n = ScrollUntilStop; Check "S8 stop still works after screen off/on" ($n -gt 0) ((Focus) + " swipes=$n") }

Run "S9" {  # app removed from Recents (swiped away) then opened again: gate needed only if pass expired -> still valid
  Launch $A; PassGate; & $adb shell am force-stop $A; Start-Sleep 1; Home; Launch $A
  $n = ScrollUntilStop; Check "S9 cold restart of the app keeps the rules" ($n -gt 0) "swipes=$n" }

Run "S10" {  # volume is restored and nothing is stuck over the home screen after every path
  Launch $A; $v0 = 5; Home; Start-Sleep 1
  Check "S10a gate dismissed by Home" (-not (OnFS)) (Focus)
  Check "S10b volume back after Home" ((Vol) -gt 0) ("vol=" + (Vol)) }

Run "S11" {  # long idle in the app after passing: scroll counter keeps counting across many small scrolls
  Launch $A; PassGate
  $hit = 0; for ($i = 1; $i -le 40; $i++) { & $adb shell input swipe 540 1500 540 1100 150; Start-Sleep -Milliseconds 300; if (OnFS) { $hit = $i; break } }
  Check "S11 many small scrolls eventually stop" ($hit -gt 0) "small swipes=$hit" }

Run "S12" {  # away longer than the limit -> asked again; staying in the app -> not asked again
  Launch $A; PassGate; Home; Start-Sleep 130; Launch $A
  Check "S12a away > 2 min -> gate again" (OnFS) (Focus)
  Home; Reset; Launch $A; PassGate
  for ($i = 1; $i -le 6; $i++) { & $adb shell input swipe 540 1500 540 1300 200; Start-Sleep 25 }   # ~2.5 min of slow scrolling, never leaves
  Check "S12b staying in the app is not interrupted by the gate" (-not (OnFS)) (Focus) }

Run "S13" {  # Back button while the gate is showing must not leave the app usable
  Launch $A; Check "S13a gate shown" (OnFS)
  & $adb shell input keyevent 4; Start-Sleep 3
  $f = Focus; Check "S13b after Back the app is not left open without the gate" ((OnFS) -or (-not (InApp $A))) $f }

Run "S16" {  # switch straight from one Hard app to another without going Home
  Launch $A; PassGate; Launch $B
  Check "S16a switching directly to a second app asks its gate" (OnFS) (Focus)
  PassGate; Launch $A
  Check "S16b back to the first app: still passed" (InApp $A) (Focus) }

Run "S20" {  # rotate while the gate is showing: still covered, still usable
  Launch $A
  & $adb shell settings put system user_rotation 1; Start-Sleep 3
  Check "S20a overlay still up after rotating" (OnFS) (Focus)
  & $adb shell settings put system user_rotation 0; Start-Sleep 3
  Check "S20b overlay still up after rotating back" (OnFS) (Focus) }

$results | Format-Table -AutoSize | Out-String
