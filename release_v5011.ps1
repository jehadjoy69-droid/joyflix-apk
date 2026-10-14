$ErrorActionPreference = "Stop"

$srcApk = "app\build\outputs\apk\stable\release\app-stable-release.apk"
$destApk = "JoyFlix-v5.0.11.apk"
Write-Host "Copying APK to $destApk..."
Copy-Item $srcApk $destApk -Force

Write-Host "Committing git changes..."
git add -A
git commit -m "feat: JoyFlix v5.0.11 - Stream Source Selector, Smart TV Crash Fix, Anime Filter & 100% TMDB Discovery"
git tag -a v5.0.11 -m "Release JoyFlix v5.0.11 (Build 88)"

Write-Host "Pushing git commits and tag..."
git push origin main
git push origin v5.0.11

Write-Host "Retrieving GitHub credential..."
$credInput = "protocol=https`nhost=github.com`n"
$cred = $credInput | git credential fill
$token = ($cred -split "`n" | Where-Object { $_ -like "password=*" }).Substring(9).Trim()

$repo = "jehadjoy15-stack/joyflix-cloud"
$tagName = "v5.0.11"
$releaseName = "JoyFlix v5.0.11"
$apkSizeMB = [math]::Round((Get-Item $destApk).Length / 1MB, 2)

$body = @"
## JoyFlix v5.0.11 (Build 88)

### What's New & Fixed:
- **Modern Stream Source Selector (JoyFlix Design)**:
  - Inside TMDB movies/series and search results, clicking an episode or Play button now displays the modern Stream Sources screen.
  - Live progress display with "Active scrapers" tags (`StreamFlix`, `Cinevibe`, `HDHub4u`, `MoviesDrive`, `HiAnime`, `4KHDHub`, etc.).
  - Real-time `Fetching...` status indicator for each scraper.
  - Dynamically populates stream source link cards with quality badges (`1080p`, `720p`, `480p`, etc.) as they arrive.
  - Top horizontal filter chips (`All`, `[ProviderName]`) for instant filtering.
  - Scrapers that return 0 playable links are automatically hidden upon completion.
  - Direct 1-tap playback from any selected link, with alternate stream sources and subtitles cached for in-player switching.
  - Full support for Android TV D-pad remote navigation and mobile touch.

- **Fixed Smart TV Memory Allocation Crash (`U_MEMORY_ALLOCATION_ERROR`)**:
  - Resolved fatal `OkHttp Dispatcher` crash on Hisense SmartTV 4K (Android 12 SDK 31) caused by unbounded client instances and regex cache collisions in image loader Coil.
  - Migrated to a singleton `sharedImageOkHttpClient` with centralized connection pooling.

- **Smart Anime Filter Rule**:
  - Zero requests sent to anime extensions/scrapers when viewing non-anime movies or TV shows, dramatically speeding up scraping.
  - Anime content seamlessly queries anime extensions (`HiAnime`, `AnimeKai`, etc.).

- **100% TMDB-Driven Home & Search**:
  - Default home page set to "JoyFlix Discover".
  - Prioritized TMDB ranking in search results.

### Download:
- APK: [JoyFlix-v5.0.11.apk](https://github.com/jehadjoy15-stack/joyflix-cloud/releases/download/v5.0.11/JoyFlix-v5.0.11.apk)
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
Write-Host "Release process completed successfully!"
