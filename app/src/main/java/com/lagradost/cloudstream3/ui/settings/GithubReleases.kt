package com.lagradost.cloudstream3.ui.settings

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.Throws

object GithubReleases {
    private const val ANDROID_UPDATER_URL = "https://version.jehadjoy69.workers.dev/android"

    @Serializable
    private data class GithubAsset(
        @JsonProperty("name") @SerialName("name") val name: String = "",
        @JsonProperty("size") @SerialName("size") val size: Int = 0, // Size in bytes
        @JsonProperty("browser_download_url") @SerialName("browser_download_url") val browserDownloadUrl: String = "",
        @JsonProperty("content_type") @SerialName("content_type") val contentType: String = "application/vnd.android.package-archive", // application/vnd.android.package-archive
        @JsonProperty("digest") @SerialName("digest") val digest: String? = null, // sha256:..., may be null
    )

    @Serializable
    private data class GithubRelease(
        @JsonProperty("tag_name") @SerialName("tag_name") val tagName: String = "", // Version code
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("body") @SerialName("body") val body: String? = "", // Description
        @JsonProperty("assets") @SerialName("assets") val assets: List<GithubAsset> = emptyList(),
        @JsonProperty("target_commitish") @SerialName("target_commitish") val targetCommitish: String? = null, // Branch
        @JsonProperty("prerelease") @SerialName("prerelease") val prerelease: Boolean = false,
        @JsonProperty("node_id") @SerialName("node_id") val nodeId: String? = null,
        @JsonProperty("created_at") @SerialName("created_at") val createdAt: String? = null, // YYYY-MM-DDTHH:MM:SSZ
    )

    @Serializable
    private data class GithubObject(
        @JsonProperty("sha") @SerialName("sha") val sha: String = "", // SHA-256 hash
    )

    @Serializable
    private data class GithubTag(
        @JsonProperty("object") @SerialName("object") val githubObject: GithubObject = GithubObject(),
    )

    /** GitHub file update package */
    @Immutable
    data class GithubFile(
        /** File digest, sha:xxx */
        val digest: String?,
        /** File url for download */
        val downloadUrl: String,
        /** Filename without the extension */
        val displayName: String,
        /** Changelog, aka the commit message */
        val changeLog: String,
        /** Name of the tag, aka unique release name like vX.X.X or pre-release */
        val tagName: String,
        /** Unique node id */
        val nodeId: String,
    )

    private val defaultHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Android; Mobile; JoyFlix)",
        "Accept" to "application/json"
    )

    @Throws
    suspend fun getShaFromTag(
        userName: String,
        repository: String,
        tag: String,
    ): String {
        return try {
            val response = app.get(url = ANDROID_UPDATER_URL, headers = defaultHeaders)
            val release = tryParseJson<GithubRelease>(response.text)
                ?: parseJson<Array<GithubRelease>>(response.text).firstOrNull()
            release?.tagName ?: "latest"
        } catch (_: Exception) {
            "latest"
        }
    }

    @Throws
    suspend fun getLatestReleaseFile(
        userName: String,
        repository: String,
        prerelease: Boolean,
        prereleaseTag: String,
        contentType: String,
    ): GithubFile? {
        val response = try {
            app.get(
                url = ANDROID_UPDATER_URL,
                headers = defaultHeaders
            )
        } catch (_: Exception) {
            return null
        }

        if (!response.isSuccessful) {
            return null
        }

        val latestRelease = try {
            tryParseJson<GithubRelease>(response.text)
                ?: parseJson<Array<GithubRelease>>(response.text).firstOrNull()
        } catch (_: Exception) {
            return null
        } ?: return null

        // Find the first correct APK asset, whether uploaded via web or GitHub action
        val foundAsset = latestRelease.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) || asset.contentType == contentType
        } ?: return null

        return GithubFile(
            digest = foundAsset.digest,
            downloadUrl = foundAsset.browserDownloadUrl,
            displayName = foundAsset.name.substringBeforeLast("."),
            changeLog = latestRelease.body ?: "",
            tagName = latestRelease.tagName,
            nodeId = latestRelease.nodeId ?: latestRelease.tagName
        )
    }
}