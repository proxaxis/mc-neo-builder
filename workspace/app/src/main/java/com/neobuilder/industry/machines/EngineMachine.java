package com.neobuilder.industry.machines;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.industry.Machine;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * エンジン. 燃料を消費してMJを生成し、隣接マシンまたはキネシスパイプへ配分する.
 * REDSTONE: レッドストーン信号で作動（燃料不要）
 * STIRLING: 石炭・木炭で作動
 * COMBUSTION: 精製油（溶岩バケツ代替）で作動
 * NUCLEAR: ウラン代替品で高出力
 */
public final class EngineMachine extends Machine {

    public enum Kind { REDSTONE, STIRLING, COMBUSTION, NUCLEAR }

    private final Kind kind;
    private double fuelBurn; // 燃焼残りtick

    public EngineMachine(NeoBuilderPlugin plugin, String key, Kind kind) {
        super(plugin, "engine_" + kind.name().toLowerCase(), key);
        this.kind = kind;
        this.maxEnergy = 5000;
    }

    private double outputPerTick() {
        return switch (kind) {
            case REDSTONE -> plugin.getConfig().getDouble("energy.redstone-engine-mj", 1.0);
            case STIRLING -> plugin.getConfig().getDouble("energy.stirling-engine-mj", 5.0);
            case COMBUSTION -> plugin.getConfig().getDouble("energy.combustion-engine-mj", 20.0);
            case NUCLEAR -> plugin.getConfig().getDouble("energy.nuclear-engine-mj", 100.0);
        };
    }

    /** 稼働中か判定し、燃料を補給 */
    private boolean isActive(Block b) {
        switch (kind) {
            case REDSTONE -> { return b.isBlockPowered() || b.isBlockIndirectlyPowered(); }
            case STIRLING -> {
                if (fuelBurn > 0) { fuelBurn--; return true; }
                // 隣接インベントリから石炭/木炭を補給
                if (consumeFuel(b, Material.COAL, Material.CHARCOAL)) { fuelBurn = 1600; return true; }
                return false;
            }
            case COMBUSTION -> {
                if (fuelBurn > 0) { fuelBurn--; return true; }
                // 燃料: 溶岩バケツ、または隣接大釜から流体パイプ網経由で供給される溶岩
                if (consumeFuel(b, Material.LAVA_BUCKET)) { fuelBurn = 2000; return true; }
                if (plugin.pipes().fluids() != null && plugin.pipes().fluids().drainLavaAdjacent(b)) {
                    fuelBurn = 1000; return true;
                }
                return false;
            }
            case NUCLEAR -> {
                if (fuelBurn > 0) { fuelBurn--; return true; }
                // ウラン代替としてエンダーパール・ブレイズロッドを使用
                if (consumeFuel(b, Material.BLAZE_ROD, Material.ENDER_PEARL)) { fuelBurn = 8000; return true; }
                return false;
            }
        }
        return false;
    }

    /** 隣接インベントリから燃料を1個消費 */
    private boolean consumeFuel(Block b, Material... fuels) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = b.getRelative(f);
            if (!(adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih)) continue;
            var inv = ih.getInventory();
            for (Material fuel : fuels) {
                int slot = inv.first(fuel);
                if (slot >= 0) {
                    ItemStack s = inv.getItem(slot);
                    s.setAmount(s.getAmount() - 1);
                    inv.setItem(slot, s.getAmount() > 0 ? s : null);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void tick(Block block) {
        if (!isActive(block)) return;
        energy += outputPerTick();
        // バッファが一定以上なら周囲へ配分（実際に届いた分だけ減らす）
        if (energy > outputPerTick() * 4) {
            double push = energy * 0.5;
            double leftover = plugin.energy().distributeToNeighbors(block, push);
            energy = Math.min(energy - push + leftover, maxEnergy);
        }
        if (energy > maxEnergy) energy = maxEnergy;
    }

    /** エンジンは発電側なのでMJを受け取らない（キネシス網へ逆流させない） */
    @Override
    public double receiveEnergy(double amount) {
        return 0;
    }

    @Override
    public Map<String, Object> serialize() {
        var m = super.serialize();
        m.put("fuelBurn", fuelBurn);
        return m;
    }

    @Override
    public void deserialize(Map<String, Object> m) {
        super.deserialize(m);
        if (m.get("fuelBurn") instanceof Number n) fuelBurn = n.doubleValue();
    }
}
