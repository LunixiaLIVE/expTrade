package net.lunix.exptrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = ExpTradePaper.getInstance().getLogger();

    private static Map<UUID, Integer> thresholds = new HashMap<>();
    private static Path dataPath;

    public static void load(File dataFolder) {
        dataFolder.mkdirs();
        dataPath = dataFolder.toPath().resolve("player_data.json");
        if (Files.exists(dataPath)) {
            try (Reader reader = Files.newBufferedReader(dataPath)) {
                Map<String, Integer> raw = GSON.fromJson(reader,
                        new TypeToken<Map<String, Integer>>() {}.getType());
                if (raw != null) {
                    thresholds = new HashMap<>();
                    raw.forEach((k, v) -> thresholds.put(UUID.fromString(k), v));
                }
            } catch (IOException e) {
                LOG.warning("[expTrade] Failed to load player data: " + e.getMessage());
            }
        }
    }

    public static void save() {
        if (dataPath == null) return;
        try (Writer writer = Files.newBufferedWriter(dataPath)) {
            Map<String, Integer> raw = new HashMap<>();
            thresholds.forEach((k, v) -> raw.put(k.toString(), v));
            GSON.toJson(raw, writer);
        } catch (IOException e) {
            LOG.warning("[expTrade] Failed to save player data: " + e.getMessage());
        }
    }

    public static int getThreshold(UUID uuid) {
        return thresholds.getOrDefault(uuid, 0);
    }

    public static void setThreshold(UUID uuid, int threshold) {
        if (threshold == 0) thresholds.remove(uuid);
        else thresholds.put(uuid, threshold);
        save();
    }
}
