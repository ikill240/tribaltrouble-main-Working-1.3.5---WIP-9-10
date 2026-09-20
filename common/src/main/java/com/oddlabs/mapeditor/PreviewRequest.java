package com.oddlabs.mapeditor;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A minimal, thread-safe handoff point between the Swing map editor (running on its own AWT event
 * dispatch thread) and the game's own single-threaded main loop.
 *
 * <p>The game's GUI framework (GUIObject/Form/GUIRoot etc.) has no existing mechanism for a
 * different thread to safely schedule work onto it - everything is written assuming exclusive
 * access from the game's own main thread (see how Animated/AnimationManager drives every
 * per-frame update). Calling directly into game GUI code from Swing's event thread would be an
 * unverified concurrency risk with no guarantee those objects tolerate concurrent access.
 *
 * <p>Instead of that, this class is a single, static, thread-safe slot: the Swing side calls
 * {@link #request(String)} when the user wants to preview a map, and the game side polls with
 * {@link #pollAndClear()} once per frame from its OWN thread (via a small Animated registered in
 * MainMenu - see that class), performing the actual GUI transition only there. Neither side ever
 * touches the other's objects directly. //added by ikill240c
 */
public final class PreviewRequest {
    private static final AtomicReference<@Nullable String> pending_map_path = new AtomicReference<>();

    private PreviewRequest() {
    }

    /**
     * Called from the Swing editor (any thread) to request that the game preview the map at the
     * given path. Overwrites any previous unconsumed request - only the most recent request
     * matters.
     */
    public static void request(String map_file_path) {
        pending_map_path.set(map_file_path);
    }

    /**
     * Called from the game's own main thread once per frame. Returns the pending path and atomically
     * clears it, or null if there's no pending request.
     */
    public static @Nullable String pollAndClear() {
        return pending_map_path.getAndSet(null);
    }
}
