package com.neobuilder.economy;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DL (Minecraft Dollar) 経済システム.
 * 残高はメモリ上で原子的に操作し、非同期でJSON永続化する.
 */
public final class EconomyManager {

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public EconomyManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Map<String, Object> data = plugin.dataStore().loadMap("economy");
        for (var e : data.entrySet()) {
            try { balances.put(UUID.fromString(e.getKey()), ((Number) e.getValue()).doubleValue()); }
            catch (Exception ignored) {}
        }
        // 定期保存 (60s)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty.getAndSet(false)) save();
        }, 1200L, 1200L);
    }

    public void disable() { save(); }

    private void save() {
        Map<String, Object> out = new ConcurrentHashMap<>();
        balances.forEach((k, v) -> out.put(k.toString(), v));
        plugin.dataStore().saveAsync("economy", out);
    }

    public double getBalance(UUID id) {
        return balances.getOrDefault(id, plugin.getConfig().getDouble("economy.starting-balance", 1000.0));
    }

    public void setBalance(UUID id, double amount) {
        balances.put(id, Math.max(0, amount));
        dirty.set(true);
    }

    public void deposit(UUID id, double amount) {
        if (amount <= 0) return;
        balances.merge(id, amount, (a, b) -> a + b);
        dirty.set(true);
    }

    /** 引き落とし（残高不足なら false、残高は変化しない） */
    public boolean withdraw(UUID id, double amount) {
        if (amount <= 0) return false;
        boolean[] ok = {false};
        balances.compute(id, (k, cur) -> {
            double c = cur == null ? getBalance(id) : cur;
            if (c >= amount) { ok[0] = true; return c - amount; }
            return c;
        });
        if (ok[0]) dirty.set(true);
        return ok[0];
    }

    /** 送金（原子性確保のため同期処理） */
    public synchronized boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;
        double fb = getBalance(from);
        if (fb < amount) return false;
        balances.put(from, fb - amount);
        balances.merge(to, amount, Double::sum);
        dirty.set(true);
        return true;
    }

    public String format(double amount) {
        return String.format("%,.0f DL", amount);
    }
}
