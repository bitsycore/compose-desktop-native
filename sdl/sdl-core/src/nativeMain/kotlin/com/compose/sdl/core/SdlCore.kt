package com.compose.sdl.core

// :sdl-core is the NAKED sdl3 cinterop (the bindings live in package `sdl3`, generated
// from src/nativeInterop/cinterop/sdl3.def). This marker exists so the native `main`
// compilation has at least one Kotlin declaration and therefore emits a publishable
// main klib. A cinterop-only module with zero Kotlin sources produces no main klib, and
// maven-publish's generateMetadataFileFor<Target>Publication then fails with
// FileNotFoundException on sdl-core-<target>Main-<version>.klib. Do not delete.
internal const val SDL_CORE_MODULE = "sdl-core"
