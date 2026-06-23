package apidemo

import com.compose.desktop.native.appDataDir
import com.compose.desktop.native.revealInFileManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath

// ==================
// MARK: Persisted app state
// ==================

/* Everything the app restores on relaunch: the theme, the global override env,
   every open pack (with its file path + dirty flag so unsaved work survives a
   quit) and which pack was active. `launched` flips to true after the very
   first run so the httpbin starter pack is only auto-loaded once. */
@Serializable
data class AppState(
    val launched: Boolean = false,
    val dark: Boolean = true,
    val globalEnv: List<KeyVal> = emptyList(),
    val packs: List<SavedPack> = emptyList(),
    val activePack: Int = 0,
    val currentSession: String? = null,        // path of the session file currently open (null = unsaved)
    val recentSessions: List<String> = emptyList(),   // most-recent-first session file paths
)

/* A pack as stored in the app state: the pack content plus where it was last
   saved (null = never saved to a file) and whether it has unsaved edits. */
@Serializable
data class SavedPack(
    val path: String? = null,
    val dirty: Boolean = false,
    val pack: Pack = Pack(),
)

/* A whole session, the export/import unit for the working set: every open pack
   (self-contained — full content embedded) plus the shared global env and which
   pack was active. Only one session is open at a time. */
@Serializable
data class Session(
    val packs: List<SavedPack> = emptyList(),
    val globalEnv: List<KeyVal> = emptyList(),
    val activePack: Int = 0,
)

// ==================
// MARK: Load / save (app-data dir via okio)
// ==================

private val fStateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    prettyPrintIndent = "  "
}

private const val kOrg = "ComposeDesktopNative"
private const val kApp = "ApiManager"
private const val kStateFile = "state.json"

/* Absolute path to the state file in the per-user app-data dir (created on
   demand by SDL_GetPrefPath). Null if no writable dir is available. */
private fun stateFilePath(): String? {
    val vDir = appDataDir(kOrg, kApp) ?: return null  // ends with a separator
    return vDir + kStateFile
}

/* Read the persisted app state, or a fresh (first-launch) AppState if there's
   nothing saved yet or it can't be read. */
fun loadAppState(): AppState {
    val vPath = stateFilePath() ?: return AppState()
    return try {
        val vText = FileSystem.SYSTEM.read(vPath.toPath()) { readUtf8() }
        fStateJson.decodeFromString<AppState>(vText)
    } catch (e: Throwable) {
        AppState()
    }
}

/* Write the app state. Best-effort — failures are swallowed (it's a cache of
   the session, not the user's exported .json packs). */
fun saveAppState(inState: AppState) {
    val vPath = stateFilePath() ?: return
    try {
        FileSystem.SYSTEM.write(vPath.toPath()) { writeUtf8(fStateJson.encodeToString(inState)) }
    } catch (e: Throwable) {
        // ignore — nothing the user can act on, and packs can still be exported
    }
}

/* Open the per-user app-data folder where the state file lives in the OS file
   manager. Revealing the state file opens its containing folder, which SDL has
   already created even when nothing's been saved yet. */
fun openSettingsFolder(): Boolean {
    val vPath = stateFilePath() ?: return false
    return revealInFileManager(vPath)
}
