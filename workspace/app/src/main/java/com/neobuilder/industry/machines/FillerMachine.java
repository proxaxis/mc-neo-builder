package com.neobuilder.industry.machines;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import com.neobuilder.industry.Machine;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * フィラー. 隣接チェストのブロックを消費し、自身の周囲の空洞を埋める/整地する.
 * 簡易実装: 16x16 範囲で水平に足場ブロックを配置するフラットモード.
 */
public final class FillerMachine extends Machine {

    private int cx = -8, cz = -8;
    private boolean finished = false;
    private static final int RANGE = 8;

    public FillerMachine(NeoBuilderPlugin plugin, String key) {
        super(plugin, "filler", key);
        this.maxEnergy = 5000;
    }

    @Override
    public void tick(Block block) {
        if (finished) return;
        double mjPer = plugin.getConfig().getDouble("filler.mj-per-block", 20.0);
        int budget = plugin.getConfig().getInt("filler.blocks-per-tick", 8);
        int y = block.getY() - 1;

        int done = 0;
        while (done < budget) {
            if (cx > RANGE) { finished = true; return; }
            Block target = Blocks.getIfLoaded(block.getWorld(),
                    block.getX() + cx, y, block.getZ() + cz);
            advance();
            if (target == null) continue;
            if (!target.getType().isAir() && target.getType() != Material.WATER
                    && target.getType() != Material.LAVA && !isPlant(target.getType())) continue;
            // 供給チェストからブロック取得
            ItemStack mat = takeBlock(block);
            if (mat == null) return; // 資材切れ
            if (!consumeEnergy(mjPer)) { giveBack(block, mat); return; }
            target.setType(mat.getType());
            done++;
        }
    }

    private void advance() {
        cx++;
        if (cx > RANGE) { cx = -RANGE; cz++; }
        if (cz > RANGE) { finished = true; }
    }

    private boolean isPlant(Material m) {
        return switch (m) {
            case SHORT_GRASS, TALL_GRASS, DANDELION, POPPY, FERN, LARGE_FERN, DEAD_BUSH, SUGAR_CANE -> true;
            default -> false;
        };
    }

    private ItemStack takeBlock(Block machine) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = machine.getRelative(f);
            if (!(adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih)) continue;
            Inventory inv = ih.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && s.getType().isBlock() && s.getType().isSolid()) {
                    ItemStack take = s.clone();
                    take.setAmount(1);
                    s.setAmount(s.getAmount() - 1);
                    inv.setItem(i, s.getAmount() > 0 ? s : null);
                    return take;
                }
            }
        }
        return null;
    }

    private void giveBack(Block machine, ItemStack stack) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = machine.getRelative(f);
            if (!(adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih)) continue;
            if (ih.getInventory().addItem(stack).isEmpty()) return;
        }
        machine.getWorld().dropItemNaturally(machine.getLocation(), stack);
    }

    @Override
    public Map<String, Object> serialize() {
        var m = super.serialize();
        m.put("cx", cx); m.put("cz", cz); m.put("finished", finished);
        return m;
    }

    @Override
    public void deserialize(Map<String, Object> m) {
        super.deserialize(m);
        if (m.get("cx") instanceof Number n) cx = n.intValue();
        if (m.get("cz") instanceof Number n) cz = n.intValue();
        if (m.get("finished") instanceof Boolean b) finished = b;
    }
}
