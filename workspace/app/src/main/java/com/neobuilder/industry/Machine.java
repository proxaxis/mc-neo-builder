package com.neobuilder.industry;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 設置型マシンの基底クラス.
 * ブロック位置に紐付き、MachineManager のティックループで駆動される.
 * エネルギーは内部バッファに蓄積される.
 */
public abstract class Machine implements com.neobuilder.energy.EnergyManager.MjHolder {

    protected final NeoBuilderPlugin plugin;
    protected final String type;
    protected final String key;
    protected double energy;
    protected double maxEnergy = 2000;
    protected int progress;

    protected Machine(NeoBuilderPlugin plugin, String type, String key) {
        this.plugin = plugin;
        this.type = type;
        this.key = key;
    }

    /** メインスレッド上で毎tick呼ばれる。重い処理はここでtickバジェット分割すること */
    public abstract void tick(Block block);

    public String type() { return type; }
    public String key() { return key; }

    /** この機械がMJを受け入れる上限 */
    public double receiveEnergy(double amount) {
        double space = maxEnergy - energy;
        double take = Math.min(space, amount);
        energy += take;
        return take;
    }

    protected boolean consumeEnergy(double need) {
        if (energy < need) return false;
        energy -= need;
        return true;
    }

    public double energy() { return energy; }

    public Block block() {
        String[] p = com.neobuilder.core.util.Blocks.parse(key);
        World w = plugin.getServer().getWorld(p[0]);
        if (w == null) return null;
        return com.neobuilder.core.util.Blocks.getIfLoaded(w,
                Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    public Location location() {
        Block b = block();
        return b == null ? null : b.getLocation();
    }

    /** 永続化データ（JSON化される） */
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("key", key);
        m.put("energy", energy);
        m.put("progress", progress);
        return m;
    }

    public void deserialize(Map<String, Object> m) {
        Object e = m.get("energy");
        if (e instanceof Number n) energy = n.doubleValue();
        Object pr = m.get("progress");
        if (pr instanceof Number n) progress = n.intValue();
    }
}
