package io.legado.app.help.update

import androidx.annotation.Keep
import io.legado.app.BuildConfig
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private const val API_ATTEMPT_TIMEOUT = 4_500L

    private val checkVariant: AppVariant
        get() = when {
            BuildConfig.GXJ_PRIVATE_UPDATE -> AppVariant.GXJ_PRIVATE
            AppConfig.updateToVariant == "official_version" -> AppVariant.OFFICIAL
            AppConfig.updateToVariant == "beta_release_version" -> AppVariant.BETA_RELEASE
            else -> AppConst.appInfo.appVariant
        }

    private fun releaseApiUrl(): String {
        val owner = BuildConfig.UPDATE_REPO_OWNER
        val repo = BuildConfig.UPDATE_REPO_NAME
        return if (checkVariant == AppVariant.BETA_RELEASE && !BuildConfig.GXJ_PRIVATE_UPDATE) {
            "https://api.github.com/repos/$owner/$repo/releases/tags/beta"
        } else {
            "https://api.github.com/repos/$owner/$repo/releases/latest"
        }
    }

    private suspend fun getLatestRelease(): List<AppReleaseInfo> {
        val gitHubUrl = releaseApiUrl()
        var lastError: Throwable? = null
        for (releaseUrl in AppUpdateRelay.apiCandidates(gitHubUrl)) {
            try {
                return withTimeout(API_ATTEMPT_TIMEOUT) {
                    getLatestRelease(releaseUrl)
                }
            } catch (error: TimeoutCancellationException) {
                lastError = error
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw NoStackTraceException(
            lastError?.localizedMessage?.let { "获取新版本出错 $it" } ?: "获取新版本出错"
        )
    }

    private suspend fun getLatestRelease(releaseUrl: String): List<AppReleaseInfo> {
        val res = okHttpClient.newCallResponse {
            url(releaseUrl)
        }
        res.use {
            if (!it.isSuccessful) {
                throw NoStackTraceException("获取新版本出错(${it.code})")
            }
            val body = it.body.text()
            if (body.isBlank()) {
                throw NoStackTraceException("获取新版本出错")
            }
            return GSON.fromJsonObject<GithubRelease>(body)
                .getOrElse { error ->
                    throw NoStackTraceException("获取新版本出错 " + error.localizedMessage)
                }
                .gitReleaseToAppReleaseInfo()
                .sortedWith(
                    compareByDescending<AppReleaseInfo> { release -> release.version }
                        .thenByDescending { release -> release.createdAt }
                )
        }
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            val installedVersion = AppVersion.parse(AppConst.appInfo.versionName)
                ?: throw NoStackTraceException("当前版本号格式异常")
            getLatestRelease()
                .filter { it.appVariant == checkVariant }
                .firstOrNull { it.version > installedVersion }
                ?.toUpdateInfo()
                ?: throw NoStackTraceException("已是最新版本")
        }.timeout(10000)
    }

    override fun latest(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            getLatestRelease()
                .firstOrNull { it.appVariant == checkVariant }
                ?.toUpdateInfo()
                ?: throw NoStackTraceException("没有可用版本")
        }.timeout(10000)
    }

    private fun AppReleaseInfo.toUpdateInfo() = AppUpdate.UpdateInfo(
        tagName = versionName,
        updateLog = note,
        downloadUrls = AppUpdateRelay.downloadCandidates(downloadUrl, name),
        fileName = name,
        fileSize = fileSize,
        sha256 = sha256,
    )
}
