package sdl3backend

import compose.ui.Color
import kotlinx.cinterop.*
import sdl3.*
import sdl3.ttf.*

// ==================
// MARK: SDL3TextRenderer
// ==================

class SDL3TextRenderer(private val backend: SDL3Backend) {
    private val fontCache = mutableMapOf<Int, COpaquePointer>()

    fun init(): Boolean {
        if (!TTF_Init()) {
            println("TTF_Init failed: ${SDL_GetError()?.toKString()}")
            return false
        }
        return true
    }

    fun destroy() {
        fontCache.values.forEach { TTF_CloseFont(it.reinterpret()) }
        fontCache.clear()
        TTF_Quit()
    }

    // Gets or opens a font at the given size
    private fun getFont(size: Int): COpaquePointer? {
        fontCache[size]?.let { return it }

        val paths = listOf(
            "/System/Library/Fonts/Helvetica.ttc",              // macOS
            "/System/Library/Fonts/SFNSText.ttf",               // macOS SF
            "/System/Library/Fonts/SFNS.ttf",                   // macOS SF newer
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",  // Linux
            "/usr/share/fonts/TTF/DejaVuSans.ttf",              // Arch Linux
            "C:\\Windows\\Fonts\\segoeui.ttf",                  // Windows
            "C:\\Windows\\Fonts\\arial.ttf",                    // Windows fallback
        )

        for (path in paths) {
            val f = TTF_OpenFont(path, size.toFloat())
            if (f != null) {
                fontCache[size] = f
                return f
            }
        }

        println("Warning: No system font found. Text rendering disabled.")
        return null
    }

    fun renderText(text: String, x: Int, y: Int, color: Color, fontSize: Int) {
        val r = backend.renderer ?: return
        val f = getFont(fontSize) ?: return

        memScoped {
            val sdlColor = alloc<sdl3.ttf.SDL_Color>()
            sdlColor.r = color.r8.toUByte()
            sdlColor.g = color.g8.toUByte()
            sdlColor.b = color.b8.toUByte()
            sdlColor.a = color.a8.toUByte()

            val surface = TTF_RenderText_Blended(f.reinterpret(), text, 0u, sdlColor.readValue())
                ?: return
            val texture = SDL_CreateTextureFromSurface(r.reinterpret(), surface.reinterpret())
            SDL_DestroySurface(surface.reinterpret())
            if (texture == null) return

            val tw = alloc<FloatVar>()
            val th = alloc<FloatVar>()
            SDL_GetTextureSize(texture.reinterpret(), tw.ptr, th.ptr)

            val dst = alloc<SDL_FRect>()
            dst.x = x.toFloat()
            dst.y = y.toFloat()
            dst.w = tw.value
            dst.h = th.value
            SDL_RenderTexture(r.reinterpret(), texture.reinterpret(), null, dst.ptr)

            SDL_DestroyTexture(texture.reinterpret())
        }
    }

    fun measureText(text: String, fontSize: Int): Pair<Int, Int> {
        val f = getFont(fontSize)
            ?: return Pair((fontSize * 0.6 * text.length).toInt(), (fontSize * 1.3).toInt())

        memScoped {
            val w = alloc<IntVar>()
            val h = alloc<IntVar>()
            TTF_GetStringSize(f.reinterpret(), text, 0u, w.ptr, h.ptr)
            return Pair(w.value, h.value)
        }
    }
}
