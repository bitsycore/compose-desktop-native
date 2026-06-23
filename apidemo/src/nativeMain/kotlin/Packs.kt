package apidemo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.FileSystem
import okio.Path.Companion.toPath

// ==================
// MARK: Pack persistence (okio + kotlinx.serialization)
// ==================

private val fJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/* Write the pack as pretty JSON to inPath. Returns null on success, else the
   error message. */
fun exportPack(inPack: Pack, inPath: String): String? = try {
    FileSystem.SYSTEM.write(inPath.trim().toPath()) {
        writeUtf8(fJson.encodeToString(inPack))
    }
    null
} catch (e: Throwable) {
    e.message ?: e.toString()
}

/* Read a pack back from inPath. */
fun importPack(inPath: String): Result<Pack> = try {
    val vText = FileSystem.SYSTEM.read(inPath.trim().toPath()) { readUtf8() }
    Result.success(fJson.decodeFromString<Pack>(vText))
} catch (e: Throwable) {
    Result.failure(e)
}

/* Write a whole self-contained session to inPath. Returns null on success. */
fun exportSession(inSession: Session, inPath: String): String? = try {
    FileSystem.SYSTEM.write(inPath.trim().toPath()) {
        writeUtf8(fJson.encodeToString(inSession))
    }
    null
} catch (e: Throwable) {
    e.message ?: e.toString()
}

/* Read a session back from inPath. */
fun importSession(inPath: String): Result<Session> = try {
    val vText = FileSystem.SYSTEM.read(inPath.trim().toPath()) { readUtf8() }
    Result.success(fJson.decodeFromString<Session>(vText))
} catch (e: Throwable) {
    Result.failure(e)
}

/* Rename inPath to inNewName within the same directory (adding a .json
   extension when the new name has none), returning the new path on success. */
fun renameFile(inPath: String, inNewName: String): Result<String> = try {
    val vSrc = inPath.trim().toPath()
    val vLeaf = inNewName.trim().let { if (it.contains('.')) it else "$it.json" }
    val vDst = vSrc.parent?.div(vLeaf) ?: vLeaf.toPath()
    FileSystem.SYSTEM.atomicMove(vSrc, vDst)
    Result.success(vDst.toString())
} catch (e: Throwable) {
    Result.failure(e)
}

/* Write arbitrary text (a response body / headers dump) to inPath. Returns null
   on success, else the error message. */
fun writeTextFile(inPath: String, inText: String): String? = try {
    FileSystem.SYSTEM.write(inPath.trim().toPath()) { writeUtf8(inText) }
    null
} catch (e: Throwable) {
    e.message ?: e.toString()
}

/* Write raw bytes (a binary response body, e.g. an image) to inPath. */
fun writeBytesFile(inPath: String, inBytes: ByteArray): String? = try {
    FileSystem.SYSTEM.write(inPath.trim().toPath()) { write(inBytes) }
    null
} catch (e: Throwable) {
    e.message ?: e.toString()
}

/* Re-indent a JSON response for display; returns the input unchanged if it
   isn't valid JSON (so plain-text / HTML responses still show). */
fun prettyJsonOrRaw(inText: String): String = try {
    fJson.encodeToString(JsonElement.serializer(), fJson.parseToJsonElement(inText))
} catch (e: Throwable) {
    inText
}
