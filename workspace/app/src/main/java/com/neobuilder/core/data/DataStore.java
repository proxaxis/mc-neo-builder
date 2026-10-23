package com.neobuilder.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neobuilder.NeoBuilderPlugin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

/**
 * JSON ファイルベースの簡易永続化.
 * 読み込みは起動時に同期、書き込みは非同期キューで行う.
 */
public final class DataStore {

    private final NeoBuilderPlugin plugin;
    private final Path dir;
    private final Gson gson = new GsonBuilder().create();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NeoBuilder-IO");
        t.setDaemon(true);
        return t;
    });

    public DataStore(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath();
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
    }

    private Path path(String name) {
        return dir.resolve(name + ".json");
    }

    /** 同期読み込み（起動時のみ使用） */
    public <T> T load(String name, Type type, T fallback) {
        Path p = path(name);
        if (!Files.exists(p)) return fallback;
        try {
            String json = Files.readString(p, StandardCharsets.UTF_8);
            T v = gson.fromJson(json, type);
            return v == null ? fallback : v;
        } catch (Exception e) {
            plugin.getLogger().warning("[NeoBuilder] failed to load " + name + ": " + e.getMessage());
            return fallback;
        }
    }

    public Map<String, Object> loadMap(String name) {
        Type t = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> m = load(name, t, null);
        return m == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(m);
    }

    /** 非同期保存 */
    public void saveAsync(String name, Object data) {
        String json;
        try {
            json = gson.toJson(data);
        } catch (Exception e) {
            plugin.getLogger().warning("[NeoBuilder] serialize failed " + name + ": " + e.getMessage());
            return;
        }
        io.submit(() -> {
            try {
                Path p = path(name);
                Path tmp = dir.resolve(name + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                plugin.getLogger().warning("[NeoBuilder] failed to save " + name + ": " + e.getMessage());
            }
        });
    }

    public void close() {
        io.shutdown();
        try { io.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
    }
}
