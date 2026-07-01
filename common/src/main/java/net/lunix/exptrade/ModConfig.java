package net.lunix.exptrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int timeoutSeconds = 60;
    public int maxQueueSize  = 10;

    private static ModConfig instance = new ModConfig();

    public static ModConfig get() {
        return instance;
    }

    private static Path configPath() {
        return ExpTradeCommon.CONFIG_DIR.resolve("exptrade.json");
    }

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) instance = loaded;
            } catch (IOException e) {
                ExpTradeCommon.LOGGER.warn("[expTrade] Failed to load config: {}", e.getMessage());
                instance = new ModConfig();
            }
        }
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(configPath())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            ExpTradeCommon.LOGGER.warn("[expTrade] Failed to save config: {}", e.getMessage());
        }
    }
}
