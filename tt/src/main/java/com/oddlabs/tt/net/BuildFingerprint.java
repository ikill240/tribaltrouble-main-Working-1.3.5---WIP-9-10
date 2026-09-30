package com.oddlabs.tt.net; //added by ikill240c

import com.oddlabs.net.ARMIEvent; //added by ikill240c
import com.oddlabs.tt.Main; //added by ikill240c
import org.jspecify.annotations.NonNull; //added by ikill240c
import org.jspecify.annotations.Nullable; //added by ikill240c

import java.io.IOException; //added by ikill240c
import java.io.InputStream; //added by ikill240c
import java.net.URISyntaxException; //added by ikill240c
import java.nio.charset.StandardCharsets; //added by ikill240c
import java.nio.file.Files; //added by ikill240c
import java.nio.file.Path; //added by ikill240c
import java.security.CodeSource; //added by ikill240c
import java.security.MessageDigest; //added by ikill240c
import java.security.NoSuchAlgorithmException; //added by ikill240c
import java.util.Comparator; //added by ikill240c
import java.util.HexFormat; //added by ikill240c
import java.util.List; //added by ikill240c
import java.util.jar.JarEntry; //added by ikill240c
import java.util.jar.JarFile; //added by ikill240c
import java.util.stream.Stream; //added by ikill240c

/**
 * A fingerprint of the game code this computer is running: SHA-256 over every compiled class of the game and common
 * modules, in a fixed order (by class path name), so two computers with the same build get the same value.
 * <p>
 * Multiplayer is lockstep - every computer simulates the whole match and they must stay identical - so players on
 * different builds go out of sync (a checksum mismatch) seconds into the game. The lobby compares fingerprints when a
 * player joins and warns before the match instead (see Server "build check"). Only a warning: class files can differ
 * harmlessly (e.g. compiled by a slightly different JDK), and that shouldn't lock anyone out.
 * //added by ikill240c
 */
public final class BuildFingerprint { //added by ikill240c
    public static final String UNKNOWN = "unknown"; //added by ikill240c - never counted as a mismatch
    private static @Nullable String cached; //added by ikill240c

    private BuildFingerprint() { //added by ikill240c
    } //added by ikill240c

    public static synchronized @NonNull String get() { //added by ikill240c
        if (cached == null) { //added by ikill240c
            cached = compute(); //added by ikill240c
            IO.println("Build fingerprint: " + cached); //added by ikill240c
        } //added by ikill240c
        return cached; //added by ikill240c
    } //added by ikill240c

    /** First 8 characters, for showing to players. */ //added by ikill240c
    public static @NonNull String shortForm(@Nullable String fingerprint) { //added by ikill240c
        if (fingerprint == null) //added by ikill240c
            return UNKNOWN; //added by ikill240c
        return fingerprint.length() > 8 ? fingerprint.substring(0, 8) : fingerprint; //added by ikill240c
    } //added by ikill240c

    /** True unless both are known and different. */ //added by ikill240c
    public static boolean matches(@Nullable String a, @Nullable String b) { //added by ikill240c
        if (a == null || b == null || UNKNOWN.equals(a) || UNKNOWN.equals(b)) //added by ikill240c
            return true; //added by ikill240c
        return a.equals(b); //added by ikill240c
    } //added by ikill240c

    private static @NonNull String compute() { //added by ikill240c
        try { //added by ikill240c
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); //added by ikill240c
            // Main anchors the game module, ARMIEvent the common module (the network code both sides share).
            for (Class<?> anchor : new Class<?>[]{Main.class, ARMIEvent.class}) { //added by ikill240c
                Path root = codeRoot(anchor); //added by ikill240c
                if (root == null) //added by ikill240c
                    return UNKNOWN; //added by ikill240c
                if (Files.isDirectory(root)) //added by ikill240c
                    hashDirectory(root, digest); //added by ikill240c - running from the build folder (gradle run)
                else //added by ikill240c
                    hashJar(root, digest); //added by ikill240c - running from a packaged jar
            } //added by ikill240c
            return HexFormat.of().formatHex(digest.digest()); //added by ikill240c
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) { //added by ikill240c
            IO.println("Couldn't compute the build fingerprint: " + e); //added by ikill240c
            return UNKNOWN; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    private static @Nullable Path codeRoot(@NonNull Class<?> anchor) { //added by ikill240c
        CodeSource source = anchor.getProtectionDomain().getCodeSource(); //added by ikill240c
        if (source == null || source.getLocation() == null) //added by ikill240c
            return null; //added by ikill240c
        try { //added by ikill240c
            return Path.of(source.getLocation().toURI()); //added by ikill240c
        } catch (URISyntaxException | IllegalArgumentException e) { //added by ikill240c
            return null; //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    private static void hashDirectory(@NonNull Path root, @NonNull MessageDigest digest) throws IOException { //added by ikill240c
        List<Path> classes; //added by ikill240c
        try (Stream<Path> walk = Files.walk(root)) { //added by ikill240c
            classes = walk.filter(p -> p.toString().endsWith(".class")) //added by ikill240c
                    .sorted(Comparator.comparing(p -> relativeName(root, p))).toList(); //added by ikill240c
        } //added by ikill240c
        for (Path p : classes) { //added by ikill240c
            digest.update(relativeName(root, p).getBytes(StandardCharsets.UTF_8)); //added by ikill240c
            digest.update(Files.readAllBytes(p)); //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    private static void hashJar(@NonNull Path jar_path, @NonNull MessageDigest digest) throws IOException { //added by ikill240c
        try (JarFile jar = new JarFile(jar_path.toFile())) { //added by ikill240c
            List<JarEntry> classes = jar.stream().filter(e -> e.getName().endsWith(".class")) //added by ikill240c
                    .sorted(Comparator.comparing(JarEntry::getName)).toList(); //added by ikill240c
            for (JarEntry entry : classes) { //added by ikill240c
                digest.update(entry.getName().getBytes(StandardCharsets.UTF_8)); //added by ikill240c
                try (InputStream in = jar.getInputStream(entry)) { //added by ikill240c
                    digest.update(in.readAllBytes()); //added by ikill240c
                } //added by ikill240c
            } //added by ikill240c
        } //added by ikill240c
    } //added by ikill240c

    // Same on Windows and Linux: forward slashes. //added by ikill240c
    private static @NonNull String relativeName(@NonNull Path root, @NonNull Path file) { //added by ikill240c
        return root.relativize(file).toString().replace('\\', '/'); //added by ikill240c
    } //added by ikill240c
} //added by ikill240c
