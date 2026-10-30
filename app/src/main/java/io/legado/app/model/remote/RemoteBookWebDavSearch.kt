package io.legado.app.model.remote

import io.legado.app.constant.AppPattern.archiveFileRegex
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.exception.NoStackTraceException
import io.legado.app.lib.webdav.WebDav
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.printOnDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

private const val REMOTE_SEARCH_PARALLELISM = 3

class RemoteBookSearchProgress(
    val scannedDirs: Int,
    val matched: Int,
    val pendingDirs: Int,
)

suspend fun RemoteBookWebDav.searchRemoteBooks(
    rootPath: String,
    filterKey: String,
    onProgress: (RemoteBookSearchProgress) -> Unit = {},
): List<RemoteBook> {
    requireBookPath(rootPath)
    if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可用")
    val keyword = filterKey.trim()
    if (keyword.isEmpty()) return emptyList()
    val predicate: (String) -> Boolean = { name -> name.contains(keyword, ignoreCase = true) }

    return try {
        val deep = searchWithDepthInfinity(rootPath, predicate)
        onProgress(RemoteBookSearchProgress(1, deep.size, 0))
        deep
    } catch (error: Exception) {
        error.printOnDebug()
        searchWithBfs(rootPath, predicate, onProgress)
    }
}

private suspend fun RemoteBookWebDav.searchWithDepthInfinity(
    rootPath: String,
    predicate: (String) -> Boolean,
): List<RemoteBook> {
    val files = WebDav(rootPath, authorization).listFilesDeep()
    if (files.isEmpty()) return emptyList()
    return files.mapNotNull { webDavFile ->
        if (webDavFile.isDir) return@mapNotNull null
        if (!bookFileRegex.matches(webDavFile.displayName) &&
            !archiveFileRegex.matches(webDavFile.displayName)
        ) {
            return@mapNotNull null
        }
        if (!predicate(webDavFile.displayName)) return@mapNotNull null
        RemoteBook(webDavFile)
    }
}

private suspend fun RemoteBookWebDav.searchWithBfs(
    rootPath: String,
    predicate: (String) -> Boolean,
    onProgress: (RemoteBookSearchProgress) -> Unit,
): List<RemoteBook> = coroutineScope {
    val matched = mutableListOf<RemoteBook>()
    val queue = ArrayDeque<String>()
    queue.add(rootPath)
    val semaphore = Semaphore(REMOTE_SEARCH_PARALLELISM)
    var scannedDirs = 0

    while (queue.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val batch = buildList {
            while (size < REMOTE_SEARCH_PARALLELISM && queue.isNotEmpty()) {
                add(queue.removeFirst())
            }
        }
        coroutineScope {
            batch.map { path ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        val dirs = mutableListOf<String>()
                        getRemoteBookList(path).forEach { item ->
                            if (item.isDir) {
                                dirs.add(item.path)
                            } else if (predicate(item.filename)) {
                                synchronized(matched) { matched.add(item) }
                            }
                        }
                        dirs
                    }
                }
            }.awaitAll().forEach { dirs -> queue.addAll(dirs) }
        }
        scannedDirs += batch.size
        onProgress(RemoteBookSearchProgress(scannedDirs, matched.size, queue.size))
    }
    matched
}
