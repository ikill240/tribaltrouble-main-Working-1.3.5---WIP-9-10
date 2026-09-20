package com.oddlabs.procedural;

import org.jspecify.annotations.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-authored map: a painted height Channel plus placed resource nodes and player start
 * positions. Everything else the game's landscape generator needs (slope, access, water_map,
 * dock_map) is mechanically derived from height by the same code the procedural generator uses -
 * this class only carries the data an editor can't derive on its own.
 *
 * <p>Uses a plain binary format (magic number + version + raw floats) rather than JSON: a
 * heightmap for a full-size island is a lot of floats, and this avoids both the file-size bloat
 * and the parsing cost of text-encoding thousands of pixel values. The version field allows the
 * format to be extended later without breaking previously saved maps.
 *
 * <p>Implements Serializable (as does Channel, and the two records below) because
 * CustomMapGenerator holds an AuthoredTerrain as a field on a class that implements
 * WorldGenerator extends Serializable - the whole generator gets shipped across the network to
 * joining players, even for singleplayer games, which still route through loopback networking.
 * //added by ikill240c
 */
public final class AuthoredTerrain implements Serializable { //added by ikill240c
    @Serial //added by ikill240c
    private static final long serialVersionUID = 1L; //added by ikill240c

    private static final int MAGIC = 0x54544D50; // "TTMP"
    private static final int VERSION = 2; //added by ikill240c - bumped: StartPosition gained a team field, with backward-compatible loading of version-1 files (see load())

    public enum ResourceType {
        TREE,
        PALM_TREE,
        ROCK,
        IRON
    }

    public record ResourceNode(@NonNull ResourceType type, int x, int y) implements Serializable { //added by ikill240c
    }

    public record StartPosition(int player_index, int x, int y, int team) implements Serializable { //added by ikill240c
    }

    private final @NonNull Channel height;
    private final @NonNull List<ResourceNode> resources = new ArrayList<>();
    private final @NonNull List<StartPosition> starts = new ArrayList<>();

    public AuthoredTerrain(@NonNull Channel height) {
        this.height = height;
    }

    public @NonNull Channel getHeightChannel() {
        return height;
    }

    public void addResource(@NonNull ResourceType type, int x, int y) {
        resources.add(new ResourceNode(type, x, y));
    }

    /**
     * Removes the resource node nearest to (x, y), if any exists within max_distance. Returns
     * true if a node was removed.
     */
    public boolean removeNearestResource(int x, int y, double max_distance) {
        ResourceNode nearest = null;
        double nearest_dist = Double.MAX_VALUE;
        for (ResourceNode node : resources) {
            double dx = node.x() - x;
            double dy = node.y() - y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < nearest_dist) {
                nearest_dist = dist;
                nearest = node;
            }
        }
        if (nearest != null && nearest_dist <= max_distance) {
            resources.remove(nearest);
            return true;
        }
        return false;
    }

    // Kept for source compatibility with existing callers that don't care about teams -
    // delegates with team=-1 ("no team assigned"), matching this codebase's existing convention
    // for that (see GameOverTrigger's ai_team == -1 check). //added by ikill240c
    public void addStart(int player_index, int x, int y) { //added by ikill240c
        addStart(player_index, x, y, -1); //added by ikill240c
    }

    public void addStart(int player_index, int x, int y, int team) { //added by ikill240c
        starts.removeIf(s -> s.player_index() == player_index); // one start per player
        starts.add(new StartPosition(player_index, x, y, team)); //added by ikill240c
    }

    /**
     * Removes the start position nearest to (x, y), if any exists within max_distance. Returns
     * true if a start was removed.
     */
    public boolean removeNearestStart(int x, int y, double max_distance) {
        StartPosition nearest = null;
        double nearest_dist = Double.MAX_VALUE;
        for (StartPosition start : starts) {
            double dx = start.x() - x;
            double dy = start.y() - y;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < nearest_dist) {
                nearest_dist = dist;
                nearest = start;
            }
        }
        if (nearest != null && nearest_dist <= max_distance) {
            starts.remove(nearest);
            return true;
        }
        return false;
    }

    public @NonNull List<ResourceNode> getResources() {
        return resources;
    }

    public @NonNull List<StartPosition> getStarts() {
        return starts;
    }

    public void save(@NonNull File file) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(height.getWidth());
            out.writeInt(height.getHeight());
            // Channel.getPixels() returns pixels[y][x] (row-major, y first) - see Channel's own
            // constructor (pixels = new float[height][width]). Writing/reading must respect this
            // exact order or a loaded map's terrain will come out transposed.
            float[][] pixels = height.getPixels();
            for (int y = 0; y < height.getHeight(); y++) {
                for (int x = 0; x < height.getWidth(); x++) {
                    out.writeFloat(pixels[y][x]);
                }
            }
            out.writeInt(resources.size());
            for (ResourceNode r : resources) {
                out.writeInt(r.type().ordinal());
                out.writeInt(r.x());
                out.writeInt(r.y());
            }
            out.writeInt(starts.size());
            for (StartPosition s : starts) {
                out.writeInt(s.player_index());
                out.writeInt(s.x());
                out.writeInt(s.y());
                out.writeInt(s.team()); //added by ikill240c
            }
        }
    }

    public static @NonNull AuthoredTerrain load(@NonNull File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC)
                throw new IOException("Not a Tribal Trouble map file: " + file);
            int version = in.readInt();
            // Version 1 files have no per-start team field - version 2 added it. Loading a
            // version-1 file still works (defaulting every start's team to -1, via the addStart()
            // overload below), so maps saved before this change keep working rather than becoming
            // permanently unloadable. //added by ikill240c
            if (version != 1 && version != VERSION) //added by ikill240c
                throw new IOException("Unsupported map file version: " + version);
            int width = in.readInt();
            int height_size = in.readInt();
            Channel height = new Channel(width, height_size);
            float[][] pixels = height.getPixels();
            for (int y = 0; y < height_size; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y][x] = in.readFloat();
                }
            }
            AuthoredTerrain terrain = new AuthoredTerrain(height);
            int num_resources = in.readInt();
            ResourceType[] types = ResourceType.values();
            for (int i = 0; i < num_resources; i++) {
                int type_ordinal = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                terrain.addResource(types[type_ordinal], x, y);
            }
            int num_starts = in.readInt();
            for (int i = 0; i < num_starts; i++) {
                int player_index = in.readInt();
                int x = in.readInt();
                int y = in.readInt();
                if (version >= 2) { //added by ikill240c
                    int team = in.readInt(); //added by ikill240c
                    terrain.addStart(player_index, x, y, team); //added by ikill240c
                } else { //added by ikill240c
                    terrain.addStart(player_index, x, y); // version 1 - no team field, defaults to -1 //added by ikill240c
                } //added by ikill240c
            }
            return terrain;
        }
    }
}
