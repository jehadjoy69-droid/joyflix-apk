$ErrorActionPreference = "Stop"

$srcApk = "app\build\outputs\apk\stable\release\app-stable-release.apk"
$destApk = "JoyFlix-v5.0.10.apk"
Write-Host "Copying APK to $destApk..."
Copy-Item $srcApk $destApk -Force

Write-Host "Committing git changes..."
git add -A
git commit -m "feat: JoyFlix v5.0.10 - Temporarily Disable Voice Call (Resolve Audio Delay & Background Noise)"
git tag -a v5.0.10 -m "Release JoyFlix v5.0.10 (Build 87)"

Write-Host "Pushing git commits and tag..."
git push origin main
git push origin v5.0.10

Write-Host "Retrieving GitHub credential..."
$credInput = "protocol=https`nhost=github.com`n"
$cred = $credInput | git credential fill
$token = ($cred -split "`n" | Where-Object { $_ -like "password=*" }).Substring(9).Trim()

$repo = "jehadjoy15-stack/joyflix-cloud"
$tagName = "v5.0.10"
$releaseName = "JoyFlix v5.0.10"
$apkSizeMB = [math]::Round((Get-Item $destApk).Length / 1MB, 2)

$body = @"
## JoyFlix v5.0.10 (Build 87)

### What's Changed:
- **Temporarily Disabled Voice Call in Watch Together**:
  - Temporarily removed live voice call feature to eliminate noticeable audio relay latency and background mic noise.
  - Hidden microphone controls in the player bottom bar (`playerMicBtt`), chat sidebar (`btn_sidebar_mic`), floating mic bubble (`player_floating_mic_container`), and Watch Together room dialog (`layout_dialog_voice`).
  - Gated all background audio recording, polling, Base64 chunk transfers, and speaker toasts behind `IS_VOICE_ENABLED = false` to keep video playback smooth and audio volume unducked.

### Download:
- APK: [JoyFlix-v5.0.10.apk](https://github.com/jehadjoy15-stack/joyflix-cloud/releases/download/v5.0.10/JoyFlix-v5.0.10.apk)
"@

$releasePayload = @{
    tag_name = $tagName
    target_commitish = "main"
    name = $releaseName
    body = $body
    draft = $false
    prerelease = $false
} | ConvertTo-Json

$headers = @{
    "Authorization" = "Bearer $token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "PowerShell-JoyFlix-Release"
}

Write-Host "Creating GitHub release $tagName..."
$releaseResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Body $releasePayload -Headers $headers -ContentType "application/json; charset=utf-8"
$releaseId = $releaseResponse.id
Write-Host "Release created with ID: $releaseId"

$uploadUri = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$destApk"
Write-Host "Uploading asset $destApk ($apkSizeMB MB)..."
$uploadHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/vnd.android.package-archive"
    "User-Agent" = "PowerShell-JoyFlix-Release"
}

$uploadResponse = Invoke-RestMethod -Uri $uploadUri -Method Post -InFile $destApk -Headers $uploadHeaders
Write-Host "Asset uploaded successfully! Download URL: $($uploadResponse.browser_download_url)"

Remove-Item $destApk -Force -ErrorAction SilentlyContinue
