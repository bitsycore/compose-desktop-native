package demo.registry

// JVM has no project-only "Native" screens (those live in :demo's nativeMain and
// aren't shared here), so the dropdown shows exactly the common Core + Material 3
// categories — the shared set whose parity with the native build we're checking.
actual fun getPlatformCategories(): List<DemoCategory> = emptyList()
