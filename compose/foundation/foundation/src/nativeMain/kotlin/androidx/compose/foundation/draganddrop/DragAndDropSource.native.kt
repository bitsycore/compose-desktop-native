/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.foundation.draganddrop

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Immutable
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.graphics.drawscope.DrawScope

/* Native actuals for the vendored `DragAndDropSource.kt` internal `expect`s.
 *
 * Upstream's skiko actuals use Skiko-JVM extensions (`asComposeCanvas`,
 * `skiaCanvas`, `PictureRecorder`) plus `PointerMatcher.mouse(...)` from the
 * desktop foundation surface. This K/N port doesn't expose those, so the
 * actuals here provide:
 *
 * - A start detector that fires on any primary-button drag via the standard
 *   `detectDragGestures` — no touch-vs-mouse distinction. Matches upstream's
 *   intent (drag begins on first pointer movement) for the mouse-only desktop
 *   use case.
 * - A no-op drag-shadow callback: the visual ghost of the dragged content
 *   isn't cached / redrawn during the drag. Cross-app drag-and-drop drops
 *   still work (the transfer data flows through `DragAndDropSourceModifierNode`);
 *   only the moving ghost preview is skipped.
 *
 * The public `Modifier.dragAndDropSource(...)` overloads in the vendored
 * common file therefore compile and are callable with the standard signature.
 */

@Immutable
internal actual object DragAndDropSourceDefaults {
    actual val DefaultStartDetector: DragAndDropStartDetector = {
        detectDragGestures(
            onDragStart = { offset -> requestDragAndDropTransfer(offset) },
            onDrag = { _, _ -> },
        )
    }
}

internal actual class CacheDrawScopeDragShadowCallback actual constructor() {
    actual fun drawDragShadow(drawScope: DrawScope) {
        // Skipped — see file header.
    }

    actual fun cachePicture(scope: CacheDrawScope): DrawResult {
        // Draw the content once, don't cache a Picture for later replay.
        return scope.onDrawWithContent { drawContent() }
    }
}
