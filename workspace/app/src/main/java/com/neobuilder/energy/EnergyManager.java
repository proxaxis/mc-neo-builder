package com.neobuilder.energy;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * MJ エネルギー基盤.
 * エンジンが生成したエネルギーを隣接マシン・キネシスパイプ経由で分配する.
 * 輸送路探索はキャッシュされたネットワークを用い、探索自体は呼び出し側の
 * ティックバジェット内で実施される.
 */
public final class EnergyManager {

    /** MJを保持・消費できる機器のインターフェース */
    public interface MjHolder {
        /** 受け入れた量を返す */
        double receiveEnergy(double amount);
        /** エネルギー入力を受け付けるか */
        default boolean acceptsEnergy() { return true; }
    }

    private final NeoBuilderPlugin plugin;

    public EnergyManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {}

    public void disable() {}

    /** 隣接するエネルギー受容機器へ配分する（直結輸送） */
    public double distributeToNeighbors(Block engineBlock, double amount) {
        double remaining = amount;
        for (BlockFace f : BlockFace.values()) {
            if (remaining <= 0) break;
            if (!f.isCartesian()) continue;
            Block adj = engineBlock.getRelative(f);
            var m = plugin.machines().at(adj);
            if (m instanceof MjHolder h && h.acceptsEnergy()) {
                remaining -= h.receiveEnergy(remaining);
            }
        }
        // 残りはキネシスパイプ網へ
        if (remaining > 0) {
            remaining = plugin.pipes().injectEnergy(engineBlock, remaining);
        }
        return remaining;
    }
}
