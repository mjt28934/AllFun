package app.allfun.ui.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.allfun.data.AppContainer
import app.allfun.data.db.SourceEntity
import app.allfun.data.extension.ExtensionRepository
import app.allfun.data.extension.RepoExtension
import app.allfun.data.extension.RepositoryContents
import app.allfun.data.net.UserError
import app.allfun.data.net.toUserError
import app.allfun.data.net.userMessage
import app.allfun.data.settings.RepositoryConfig
import app.allfun.data.settings.RepositoryKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import app.allfun.data.net.str
import java.io.File

/** The four kinds of installable thing AllFun manages in one place. */
enum class ExtensionKind(val label: String, val plural: String) {
    MANGA("Manga extension", "Manga extensions"),
    NOVEL("Novel plugin", "Novel plugins"),
    STREMIO("Stremio addon", "Video addons"),
    JELLYFIN("Jellyfin server", "Video servers")
}

/**
 * One managed extension, whether it came from an APK, a JS plugin or a URL.
 *
 * [key] is stable across updates and matches the repository's own key (`manga:package`,
 * `novel:id`) so update checks never rely on display names.
 */
data class InstalledExtension(
    val key: String,
    val kind: ExtensionKind,
    val name: String,
    val version: String? = null,
    val developer: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val enabled: Boolean = true,
    val nsfw: Boolean = false,
    val repoUrl: String? = null,
    val packageName: String? = null,
    val pluginId: String? = null,
    val apkName: String? = null,
    val sourceIds: List<String> = emptyList(),
    val update: RepoExtension? = null,
    val broken: Boolean = false,
    val permissions: List<String> = emptyList(),
    val location: String? = null
) {
    val canToggle: Boolean get() = sourceIds.isNotEmpty()
}

/** A repository index plus the state of the last attempt to read it. */
data class RepositoryState(
    val config: RepositoryConfig,
    val builtIn: Boolean,
    val name: String,
    val contents: RepositoryContents? = null,
    val loading: Boolean = false,
    val error: UserError? = null
) {
    val extensionCount: Int get() = contents?.extensions?.size ?: 0
    val kindLabel: String
        get() = when (config.kind) {
            RepositoryKind.MANGA -> "Manga repository"
            RepositoryKind.NOVEL -> "Novel repository"
        }
}

class ExtensionsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val installed: List<InstalledExtension> = emptyList(),
        val repos: List<RepositoryState> = emptyList(),
        val repositories: List<RepositoryConfig> = emptyList(),
        val loadingInstalled: Boolean = true,
        val busyKey: String? = null,
        val busyLabel: String? = null,
        val addingRepository: Boolean = false,
        val addRepositoryError: UserError? = null,
        val repositoryAddTicket: Int = 0
    ) {
        /** Every entry from every repository that loaded, first repository wins. */
        val discover: List<RepoExtension>
            get() = repos.flatMap { it.contents?.extensions.orEmpty() }.distinctBy { it.key }

        val updates: List<InstalledExtension>
            get() = installed.filter { it.update != null }

        val disabled: List<InstalledExtension>
            get() = installed.filter { !it.enabled && it.canToggle }

        val loadedRepositoryCount: Int
            get() = repos.count { it.contents != null }

        val failedRepositoryCount: Int
            get() = repos.count { it.error != null }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var sources: List<SourceEntity> = emptyList()
    private var repoJob: Job? = null
    private var installedGeneration = 0
    private var lastRepositorySignature: String? = null

    init {
        viewModelScope.launch {
            container.sources.observeAll().collect { list ->
                sources = list
                refreshInstalled()
            }
        }
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                val signature = settings.repositories.joinToString("|") { "${it.kind}:${it.url}" }
                if (signature == lastRepositorySignature) return@collect
                lastRepositorySignature = signature
                _state.update { it.copy(repositories = settings.repositories) }
                loadRepositories()
            }
        }
    }

    fun refresh() {
        refreshInstalled()
        loadRepositories()
    }

    // ---------------------------------------------------------------- installed

    private fun refreshInstalled() {
        val generation = ++installedGeneration
        val sourceSnapshot = sources
        val repoSnapshot = _state.value.repos
        viewModelScope.launch {
            val built = withContext(Dispatchers.IO) { buildInstalled(sourceSnapshot, repoSnapshot) }
            if (generation == installedGeneration) {
                _state.update { it.copy(installed = built, loadingInstalled = false) }
            }
        }
    }

    private fun buildInstalled(
        sourceRows: List<SourceEntity>,
        repos: List<RepositoryState>
    ): List<InstalledExtension> {
        val repoExtensions = repos.flatMap { it.contents?.extensions.orEmpty() }
            .distinctBy { it.key }
            .associateBy { it.key }
        val apkInfos = container.extensions.installed().associateBy { it.apk.name }
        val rows = sourceRows.filter { !it.builtIn }
        val result = mutableListOf<InstalledExtension>()
        val seenApks = mutableSetOf<String>()

        rows.filter { it.configKind() == "komikku" }
            .groupBy { it.configValue("apk").orEmpty() }
            .forEach { (apkName, group) ->
                if (apkName.isBlank()) return@forEach
                seenApks += apkName
                val apk = apkInfos[apkName]
                val packageName = apk?.packageName
                val key = packageName?.let { "manga:$it" } ?: "manga-apk:$apkName"
                val remote = repoExtensions[key]
                val localVersion = apk?.versionName
                    ?: group.mapNotNull { it.version }
                        .reduceOrNull { left, right ->
                            if (ExtensionRepository.isNewer(left, right)) left else right
                        }
                result += InstalledExtension(
                    key = key,
                    kind = ExtensionKind.MANGA,
                    name = apk?.displayName ?: group.first().name,
                    version = localVersion,
                    developer = remote?.developer,
                    description = remote?.description,
                    iconUrl = remote?.iconUrl,
                    enabled = group.any { it.enabled },
                    nsfw = apk?.nsfw ?: false,
                    repoUrl = group.firstNotNullOfOrNull { it.repoUrl },
                    packageName = packageName,
                    apkName = apkName,
                    sourceIds = group.map { it.id },
                    update = remote?.takeIf { ExtensionRepository.isNewer(it.version, localVersion) },
                    broken = apk == null,
                    permissions = if (apk != null) {
                        container.extensions.declaredPermissions(apkName)
                    } else {
                        emptyList()
                    },
                    location = "Private extensions folder"
                )
            }

        // An APK can be present without source rows (for example after a database reset).
        // It is still a real installed file, so list it and let the user remove it.
        apkInfos.values.filterNot { it.apk.name in seenApks }.forEach { apk ->
            val key = "manga:${apk.packageName}"
            val remote = repoExtensions[key]
            result += InstalledExtension(
                key = key,
                kind = ExtensionKind.MANGA,
                name = apk.displayName,
                version = apk.versionName,
                developer = remote?.developer,
                description = "Installed file with no registered sources. " +
                    "Remove it and install again to recreate its sources.",
                iconUrl = remote?.iconUrl,
                enabled = true,
                nsfw = apk.nsfw,
                packageName = apk.packageName,
                apkName = apk.apk.name,
                update = remote?.takeIf { ExtensionRepository.isNewer(it.version, apk.versionName) },
                permissions = container.extensions.declaredPermissions(apk.apk.name),
                location = "Private extensions folder"
            )
        }

        rows.filter { it.configKind() == "lnreader" }.forEach { row ->
            val pluginId = row.configValue("pluginId")
            val key = pluginId?.let { "novel:$it" } ?: "novel-source:${row.id}"
            val remote = repoExtensions[key]
            val localVersion = row.version ?: row.configValue("version")
            result += InstalledExtension(
                key = key,
                kind = ExtensionKind.NOVEL,
                name = row.name,
                version = localVersion,
                developer = row.baseUrl?.hostOf(),
                description = row.baseUrl?.let { "Plugin for $it" },
                enabled = row.enabled,
                repoUrl = row.repoUrl,
                pluginId = pluginId,
                sourceIds = listOf(row.id),
                update = remote?.takeIf { ExtensionRepository.isNewer(it.version, localVersion) },
                broken = !container.sourceHub.isAvailable(row),
                permissions = listOf(
                    "Network requests are sent through AllFun's HTTP client",
                    "Runs in a sandboxed offscreen WebView",
                    "Cannot read AllFun's files or other apps' data"
                ),
                location = row.baseUrl
            )
        }

        rows.filter { it.configKind() == "stremio" }.forEach { row ->
            result += InstalledExtension(
                key = "source:${row.id}",
                kind = ExtensionKind.STREMIO,
                name = row.name,
                description = row.baseUrl?.let { "Addon at $it" },
                enabled = row.enabled,
                sourceIds = listOf(row.id),
                permissions = listOf(
                    "Network access to ${row.baseUrl ?: "the addon"}",
                    "Provides catalogue entries and playback streams"
                ),
                location = row.baseUrl
            )
        }

        rows.filter { it.configKind() == "jellyfin" }.forEach { row ->
            result += InstalledExtension(
                key = "source:${row.id}",
                kind = ExtensionKind.JELLYFIN,
                name = row.name,
                description = row.baseUrl?.let { "Server at $it" },
                enabled = row.enabled,
                sourceIds = listOf(row.id),
                permissions = listOf(
                    "Network access to ${row.baseUrl ?: "your server"}",
                    "Stores a Jellyfin access token in AllFun's private app storage"
                ),
                location = row.baseUrl
            )
        }

        return result.sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))
    }

    // -------------------------------------------------------------- repositories

    private fun loadRepositories() {
        repoJob?.cancel()
        val configs = (ExtensionRepository.BUILT_IN + _state.value.repositories)
            .distinctBy { "${it.kind}:${it.url}" }
        val previous = _state.value.repos.associateBy { "${it.config.kind}:${it.config.url}" }
        _state.update { current ->
            current.copy(
                repos = configs.map { config ->
                    val before = previous["${config.kind}:${config.url}"]
                    RepositoryState(
                        config = config,
                        builtIn = isBuiltIn(config),
                        name = before?.name ?: repositoryName(config, before?.contents),
                        contents = before?.contents,
                        loading = true,
                        error = null
                    )
                }
            )
        }
        repoJob = viewModelScope.launch {
            coroutineScope {
                configs.forEach { config ->
                    launch {
                        runCatching { container.extensionRepo.load(config.kind, config.url) }
                            .onSuccess { contents ->
                                updateRepository(config) {
                                    it.copy(
                                        contents = contents,
                                        loading = false,
                                        error = null,
                                        name = repositoryName(config, contents)
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                if (throwable is CancellationException) throw throwable
                                updateRepository(config) {
                                    it.copy(
                                        loading = false,
                                        error = throwable.toUserError("this repository")
                                    )
                                }
                            }
                    }
                }
            }
            refreshInstalled()
        }
    }

    fun retryRepository(config: RepositoryConfig) {
        viewModelScope.launch {
            updateRepository(config) { it.copy(loading = true, error = null) }
            runCatching { container.extensionRepo.load(config.kind, config.url) }
                .onSuccess { contents ->
                    updateRepository(config) {
                        it.copy(
                            contents = contents,
                            loading = false,
                            error = null,
                            name = repositoryName(config, contents)
                        )
                    }
                    refreshInstalled()
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    updateRepository(config) {
                        it.copy(loading = false, error = throwable.toUserError("this repository"))
                    }
                }
        }
    }

    fun addRepository(kind: RepositoryKind, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            _state.update {
                it.copy(
                    addRepositoryError = UserError(
                        "Repository URLs must start with http:// or https://",
                        null
                    )
                )
            }
            return
        }
        val duplicate = (ExtensionRepository.BUILT_IN + _state.value.repositories).any {
            it.kind == kind && it.url.trimEnd('/').equals(trimmed.trimEnd('/'), ignoreCase = true)
        }
        if (duplicate) {
            _state.update {
                it.copy(
                    addRepositoryError = UserError("That repository is already configured.", null)
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(addingRepository = true, addRepositoryError = null) }
            try {
                // Read it before saving: a URL that serves HTML or an unreadable index is
                // rejected now rather than becoming a permanently failing row.
                container.extensionRepo.load(kind, trimmed)
                container.settings.setRepositories(
                    _state.value.repositories + RepositoryConfig(kind, trimmed)
                )
                _state.update {
                    it.copy(
                        addingRepository = false,
                        repositoryAddTicket = it.repositoryAddTicket + 1
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                _state.update {
                    it.copy(
                        addingRepository = false,
                        addRepositoryError = throwable.toUserError("this repository")
                    )
                }
            }
        }
    }

    fun clearRepositoryError() {
        _state.update { it.copy(addRepositoryError = null) }
    }

    fun removeRepository(config: RepositoryConfig) {
        viewModelScope.launch {
            container.settings.setRepositories(_state.value.repositories.filterNot { it == config })
            _messages.send("Removed ${repositoryName(config, null)}")
        }
    }

    private fun updateRepository(
        config: RepositoryConfig,
        transform: (RepositoryState) -> RepositoryState
    ) {
        _state.update { current ->
            current.copy(
                repos = current.repos.map { repo ->
                    if (repo.config.kind == config.kind && repo.config.url == config.url) {
                        transform(repo)
                    } else {
                        repo
                    }
                }
            )
        }
    }

    private fun isBuiltIn(config: RepositoryConfig): Boolean = ExtensionRepository.BUILT_IN.any {
        it.kind == config.kind && it.url == config.url
    }

    private fun repositoryName(config: RepositoryConfig, contents: RepositoryContents?): String {
        if (isBuiltIn(config)) return ExtensionRepository.builtInName(config)
        contents?.name?.takeIf { it.isNotBlank() }?.let { return it }
        return config.url.hostOf() ?: config.url
    }

    // ------------------------------------------------------------------ actions

    fun install(entry: RepoExtension) {
        if (_state.value.busyKey != null) return
        val existing = _state.value.installed.firstOrNull { it.key == entry.key }
        val isUpdate = existing != null && ExtensionRepository.isNewer(entry.version, existing.version)
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busyKey = entry.key,
                    busyLabel = (if (isUpdate) "Updating " else "Installing ") + entry.name
                )
            }
            try {
                performInstall(entry)
                _messages.send((if (isUpdate) "Updated " else "Installed ") + entry.name)
                refreshInstalled()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                _messages.send(
                    "Could not install ${entry.name}: ${throwable.userMessage("the extension")}"
                )
            } finally {
                _state.update { it.copy(busyKey = null, busyLabel = null) }
            }
        }
    }

    fun installAllUpdates() {
        val pending = _state.value.updates.mapNotNull { item -> item.update?.let { item to it } }
        if (pending.isEmpty() || _state.value.busyKey != null) return
        viewModelScope.launch {
            var succeeded = 0
            val failed = mutableListOf<String>()
            pending.forEachIndexed { index, (item, entry) ->
                _state.update {
                    it.copy(
                        busyKey = entry.key,
                        busyLabel = "Updating ${item.name} (${index + 1}/${pending.size})"
                    )
                }
                try {
                    performInstall(entry)
                    succeeded++
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    failed += item.name
                }
            }
            _state.update { it.copy(busyKey = null, busyLabel = null) }
            refreshInstalled()
            _messages.send(
                buildString {
                    if (succeeded > 0) {
                        append("Updated $succeeded ${if (succeeded == 1) "extension" else "extensions"}")
                    }
                    if (failed.isNotEmpty()) {
                        if (succeeded > 0) append(". ")
                        append("Could not update: ")
                        append(failed.joinToString(", "))
                    }
                }
            )
        }
    }

    private suspend fun performInstall(entry: RepoExtension) {
        when (entry.kind) {
            RepositoryKind.MANGA -> {
                val bytes = container.http.getBytes(entry.downloadUrl, maxBytes = 96L * 1024 * 1024)
                if (bytes.size < 64) error("The repository returned an empty extension file")
                val signingKey = _state.value.repos
                    .firstOrNull { it.config.url == entry.repoUrl }
                    ?.contents
                    ?.signingKey
                val temp = File.createTempFile("allfun-extension-", ".apk", container.cacheDir)
                try {
                    temp.outputStream().use { it.write(bytes) }
                    container.sourceHub
                        .addExtension(
                            file = temp,
                            repoUrl = entry.repoUrl,
                            version = entry.version,
                            expectedSigningKey = signingKey
                        )
                        .getOrThrow()
                } finally {
                    temp.delete()
                }
            }

            RepositoryKind.NOVEL -> {
                val script = container.http.get(entry.downloadUrl)
                if (script.isBlank()) error("The repository returned an empty plugin")
                if (script.length > 4 * 1024 * 1024) {
                    error("That plugin is larger than the 4 MB limit")
                }
                container.sourceHub.installLnReader(script, entry.repoUrl, entry.version)
            }
        }
    }

    fun setEnabled(item: InstalledExtension, enabled: Boolean) {
        if (item.sourceIds.isEmpty()) return
        viewModelScope.launch {
            item.sourceIds.forEach { container.sources.setEnabled(it, enabled) }
            _messages.send(
                if (enabled) {
                    "Enabled ${item.name}"
                } else {
                    "Disabled ${item.name}. Its library entries are kept."
                }
            )
        }
    }

    fun uninstall(item: InstalledExtension) {
        if (_state.value.busyKey != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyKey = item.key, busyLabel = "Removing ${item.name}") }
            try {
                item.sourceIds.forEach { container.sourceHub.remove(it) }
                item.apkName?.let { container.extensions.remove(it) }
                _messages.send(
                    "Removed ${item.name}. Library entries stay, but need this extension to load."
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                _messages.send("Could not remove ${item.name}: ${throwable.userMessage("the extension")}")
            } finally {
                _state.update { it.copy(busyKey = null, busyLabel = null) }
                refreshInstalled()
            }
        }
    }

    private fun SourceEntity.configKind(): String? = configValue("kind")

    private fun SourceEntity.configValue(key: String): String? = runCatching {
        Json.parseToJsonElement(configJson ?: "{}").jsonObject.str(key)
    }.getOrNull()

    private fun String.hostOf(): String? = runCatching {
        java.net.URI(this).host?.removePrefix("www.")
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
