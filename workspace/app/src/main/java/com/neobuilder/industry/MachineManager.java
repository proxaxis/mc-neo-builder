package com.neobuilder.industry;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import com.neobuilder.industry.machines.*;
import com.neobuilder.item.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 設置型マシンの登録・ティック駆動・永続化.
 * TPS保護のため、1 tick のマシン処理はリストをラウンドロビンで分割実行する.
 */
public final class MachineManager implements Listener {

    /** マシンのアイテムID→ファクトリ */
    private final Map<String, java.util.function.BiFunction<NeoBuilderPlugin, String, Machine>> factories
            = new HashMap<>();
    private final Map<String, Machine> machines = new ConcurrentHashMap<>();
    private final List<Machine> tickList = new ArrayList<>();
    private int cursor = 0;
    private volatile boolean dirty = false;

    private final NeoBuilderPlugin plugin;

    public MachineManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
        factories.put("engine_redstone", (p, k) -> new EngineMachine(p, k, EngineMachine.Kind.REDSTONE));
        factories.put("engine_stirling", (p, k) -> new EngineMachine(p, k, EngineMachine.Kind.STIRLING));
        factories.put("engine_combustion", (p, k) -> new EngineMachine(p, k, EngineMachine.Kind.COMBUSTION));
        factories.put("engine_nuclear", (p, k) -> new EngineMachine(p, k, EngineMachine.Kind.NUCLEAR));
        factories.put("quarry", QuarryMachine::new);
        factories.put("filler", FillerMachine::new);
        factories.put("macerator", MaceratorMachine::new);
        // IC2系マシン
        for (String t : new String[]{"generator","solar_panel","nuclear_reactor","batbox","mfe","mfsu",
                "pump","refinery","tank","mass_fabricator","tesla_coil","auto_workbench","teleporter",
                "electric_furnace","induction_furnace","iron_furnace","compressor","extractor"}) {
            factories.put(t, (pl,k) -> Ic2Machines.create(pl,k,t));
        }
    }

    public boolean isMachineItem(String itemId) {
        return factories.containsKey(itemId);
    }

    public void enable() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 毎tick: 全マシンを slice ずつ処理（1 tick の処理量を平準化）
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickSlice, 1L, 1L);
        // 定期保存
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) { dirty = false; save(); }
        }, 1200L, 1200L);
    }

    public void disable() { save(); }

    public int count() { return machines.size(); }

    public Map<String, Machine> machines() { return machines; }

    public Machine at(Block b) { return machines.get(Blocks.key(b)); }
    public Machine at(String key) { return machines.get(key); }

    /** マシン登録 */
    public void register(String itemId, Block b) {
        var f = factories.get(itemId);
        if (f == null) return;
        Machine m = f.apply(plugin, Blocks.key(b));
        machines.put(Blocks.key(b), m);
        synchronized (tickList) { tickList.add(m); }
        dirty = true;
    }

    public void unregister(Block b) {
        Machine m = machines.remove(Blocks.key(b));
        if (m != null) {
            synchronized (tickList) { tickList.remove(m); }
            dirty = true;
        }
    }

    /** ラウンドロビンで1 tick に一部のマシンのみ処理（TPS保護） */
    private void tickSlice() {
        int size;
        synchronized (tickList) { size = tickList.size(); }
        if (size == 0) return;
        // 1 tick あたり最大64基 or 全体の1/20の大きい方を処理 → 全マシンが遅くとも20tickに1回動く
        int slice = Math.max(1, Math.min(64, Math.max(size / 20, 1)));
        for (int i = 0; i < slice; i++) {
            Machine m;
            synchronized (tickList) {
                if (tickList.isEmpty()) return;
                cursor = (cursor + 1) % tickList.size();
                m = tickList.get(cursor);
            }
            try {
                Block b = m.block();
                if (b != null) m.tick(b); // チャンク未ロード時はスキップ
            } catch (Exception e) {
                // Fail-Safe: 該当マシンのみ停止。サーバ全体は継続
                plugin.getLogger().warning("[NeoBuilder] machine tick error at "
                        + m.key() + ": " + e.getMessage());
            }
        }
    }

    // --- 設置/破壊 ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        String id = plugin.items().itemId(item);
        if (id == null) return;
        // 技術排他チェック
        if (plugin.allegiance().isForbidden(e.getPlayer(), item)) {
            e.setCancelled(true);
            return;
        }
        if (isMachineItem(id)) {
            register(id, e.getBlockPlaced());
            e.getPlayer().sendMessage("§b" + id + " を設置しました");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Machine m = at(e.getBlock());
        if (m == null) return;
        unregister(e.getBlock());
        // カスタムアイテムをドロップ
        e.setDropItems(false);
        ItemStack drop = plugin.items().create(m.type());
        if (drop != null) {
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), drop);
        }
    }

    // --- 永続化 ---

    private void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Machine m : machines.values()) out.add(m.serialize());
        plugin.dataStore().saveAsync("machines", out);
    }

    @SuppressWarnings("unchecked")
    private void load() {
        var list = plugin.dataStore().load("machines",
                new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType(),
                Collections.<Map<String, Object>>emptyList());
        for (var m : list) {
            try {
                String type = String.valueOf(m.get("type"));
                String key = String.valueOf(m.get("key"));
                var f = factories.get(type);
                if (f == null) continue;
                Machine machine = f.apply(plugin, key);
                machine.deserialize(m);
                machines.put(key, machine);
                tickList.add(machine);
            } catch (Exception ignored) {}
        }
    }
}
