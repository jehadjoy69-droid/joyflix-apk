$ErrorActionPreference = "Stop"

$srcApk = "app\build\outputs\apk\stable\release\app-stable-release.apk"
$destApk = "JoyFlix-v5.0.9.apk"
Write-Host "Copying APK to $destApk..."
Copy-Item $srcApk $destApk -Force

Write-Host "Committing git changes..."
git add -A
git commit -m "feat: JoyFlix v5.0.9 - TMDB-Driven Home & Search (Adult Content Support), Stream Source Selector, Player Layout Logic Polish & Facebook-Style Chat"
git tag -a v5.0.9 -m "Release JoyFlix v5.0.9 (Build 86)"

Write-Host "Pushing git commits and tag..."
git push origin main
git push origin v5.0.9

Write-Host "Retrieving GitHub credential..."
$credInput = "protocol=https`nhost=github.com`n"
$cred = $credInput | git credential fill
$token = ($cred -split "`n" | Where-Object { $_ -like "password=*" }).Substring(9).Trim()

$repo = "jehadjoy15-stack/joyflix-cloud"
$tagName = "v5.0.9"
$releaseName = "JoyFlix v5.0.9"
$apkSizeMB = [math]::Round((Get-Item $destApk).Length / 1MB, 2)

$body = @"
## JoyFlix v5.0.9 (Build 86)

### What's New & Fixed:
- **TMDB-Powered Home & Multi-Search Catalog**:
  - The entire Home page catalog and global search suggestions are now powered by comprehensive TMDB metadata (Popular Movies/TV, Trending, Top Rated, and in-depth details).
  - Added support for 18+ adult content in search queries with explicit adult search flag support (`includeAdult = true`).
- **Interactive Stream Source Selector**:
  - Users can now select and switch their preferred streaming provider/source directly from the episode/stream list modal instead of relying solely on background auto-scraping.
- **Player Layout & Settings Logic Polish**:
  - Fixed title length truncation key mismatch (`prefer_limit_title_key`) so title length limits and "Hide Title" behave correctly.
  - Fixed player video codec/audio metadata HUD visibility leak when controls auto-hide.
  - Safeguarded `hideControlsNames()` hierarchy traversal against ClassCastExceptions.
  - Aligned Compose and XML player action preferences (`""` for Select Stream Source, `"AutoPlay"` for Auto Play).
  - Prevented crash on negative index selections in Player Settings.
  - Resolved `ConstraintSet.clone()` crash on portrait mode rotation by assigning IDs to all layout children.
- **Facebook-Style Portrait Watch Together Chat**:
  - Redesigned chat interface with modern rounded message bubbles, avatar circles, nickname headers, and timestamp indicators.
  - Added direct message reply quoting and @mention capabilities.
  - Live active speaker toast and speaker status indicators when participants talk in voice room.

### Download:
- APK: [JoyFlix-v5.0.9.apk](https://github.com/jehadjoy15-stack/joyflix-cloud/releases/download/v5.0.9/JoyFlix-v5.0.9.apk)
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
