package net.lunix.exptrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerEntry>>() {}.getType();

    private static Map<String, PlayerEntry> data = new HashMap<>();

    public static class PlayerEntry {
        public int threshold = 0;
    }

    private static Path dataPath() {
        return ExpTradeCommon.CONFIG_DIR.resolve("exptrade").resolve("playerdata.json");
    }

    public static void load() {
        Path path = dataPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, PlayerEntry> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) data = loaded;
            } catch (IOException e) {
                ExpTradeCommon.LOGGER.warn("[expTrade] Failed to load player data: {}", e.getMessage());
            }
        }
    }

    public static void save() {
        try {
            Path path = dataPath();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(data, MAP_TYPE, writer);
            }
        } catch (IOException e) {
            ExpTradeCommon.LOGGER.warn("[expTrade] Failed to save player data: {}", e.getMessage());
        }
    }

    public static int getThreshold(UUID uuid) {
        PlayerEntry entry = data.get(uuid.toString());
        return entry != null ? entry.threshold : 0;
    }

    public static void setThreshold(UUID uuid, int threshold) {
        data.computeIfAbsent(uuid.toString(), k -> new PlayerEntry()).threshold = threshold;
        save();
    }
}
