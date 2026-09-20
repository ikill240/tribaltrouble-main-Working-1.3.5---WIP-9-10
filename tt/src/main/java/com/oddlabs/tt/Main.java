package com.oddlabs.tt;

import com.oddlabs.tt.render.Renderer;
import com.oddlabs.tt.steam.SteamManager;
import com.oddlabs.tt.util.Utils;
import org.jspecify.annotations.NonNull;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final ResourceBundle bundle = ResourceBundle.getBundle(Main.class.getName());

    private static @NonNull String i18n(@NonNull String key, @NonNull Object @NonNull... args) {
        return Utils.getBundleString(bundle, key, args);
    }

    public static void fail(@NonNull Throwable t) {
        logger.log(Level.SEVERE, "Critical Failure", t);

        if (!Boolean.getBoolean("com.oddlabs.tt.developer")) {
            while (t.getCause() != null) {
                t = t.getCause();
            }
            String error = i18n("error");
            String error_msg;
            // OutOfMemoryError's own toString() is just "java.lang.OutOfMemoryError: Java heap
            // space" - meaningless to a player, and unlike every other failure here it isn't really
            // "a bug in this specific action", it's a hard resource ceiling that different actions
            // can hit under different conditions (bigger maps, longer sessions). Give it a message
            // that actually suggests something the player can do about it, same spirit as the
            // "error_message" bundle string. //added by ikill240c 2026-09-17
            if (t instanceof OutOfMemoryError) { //added by ikill240c 2026-09-17
                error_msg = i18n("error_out_of_memory"); //added by ikill240c 2026-09-17
            } else { //added by ikill240c 2026-09-17
                try {
                    error_msg = i18n("error_message", t.toString());
                } catch (IllegalArgumentException e) {
                    // Fallback if message formatting fails (e.g. quotes in exception message)
                    error_msg = "Error: " + t;
                }
            } //added by ikill240c 2026-09-17
            logger.log(Level.SEVERE, error + ": " + error_msg);
            TinyFileDialogs.tinyfd_messageBox(error, error_msg.replace("\"", "\\\""), "ok", "error", 1);
        }
    }

    public static void shutdown(int status) {
        SteamManager.shutdown();
        Renderer.getRenderer().close();
        logger.info("Exiting");
        System.exit(status);
    }

    static void main(@NonNull String @NonNull... args) {
        int status = 1;
        try {
            SteamManager.init();
            logger.info("Starting game....");
            Renderer.getRenderer().run(args);
            status = 0;
        } catch (Throwable t) {
            fail(t);
        } finally {
            shutdown(status);
        }
    }
}
