package net.lunix.exptrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = ExpTradePaper.getInstance().getLogger();

    public int timeoutSeconds = 60;
    public int maxQueueSize  = 10;

    private static ModConfig instance = new ModConfig();
    private static Path configPath;

    public static ModConfig get() { return instance; }

    public static void load(File dataFolder) {
        dataFolder.mkdirs();
        configPath = dataFolder.toPath().resolve("config.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) instance = loaded;
            } catch (IOException e) {
                LOG.warning("[expTrade] Failed to load config: " + e.getMessage());
                instance = new ModConfig();
            }
        }
        save();
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            LOG.warning("[expTrade] Failed to save config: " + e.getMessage());
        }
    }
}
