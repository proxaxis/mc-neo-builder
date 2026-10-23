package com.neobuilder.industry.machines;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import com.neobuilder.industry.Machine;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * クァーリー. 自身を中心とした正方形領域を岩盤まで掘削する.
 * 1 tick の破壊数と消費MJは config で制御（TPS保護）.
 * 採掘物は隣接チェストへ、なければ自然ドロップする.
 */
public final class QuarryMachine extends Machine {

    // カーソル: 相対座標オフセット（-r..r, -r..r）をエンコード
    private int cx = -1, cz = -1, cy = -1;
    private int radius = 8;
    private boolean finished = false;

    public QuarryMachine(NeoBuilderPlugin plugin, String key) {
        super(plugin, "quarry", key);
        this.maxEnergy = 10000;
    }

    @Override
    public void tick(Block block) {
        if (finished) return;
        int maxSize = plugin.getConfig().getInt("quarry.max-size", 64);
        int r = Math.min(radius, maxSize / 2);
        double mjPer = plugin.getConfig().getDouble("quarry.mj-per-block", 60.0);
        int budget = plugin.getConfig().getInt("quarry.blocks-per-tick", 4);

        if (cy == -1) { // 初期化
            cy = block.getY() - 1;
        }

        int done = 0;
        while (done < budget) {
            if (cy < block.getWorld().getMinHeight() + 1) {
                // 次の列へ
                cy = block.getY() - 1;
                cx++;
                if (cx > r) { cx = -r; cz++; }
                if (cz > r) { finished = true; return; }
            }
            if (cx == 0 && cz == 0) { cy--; continue; } // 自分自身の列は掘らない
            Block target = Blocks.getIfLoaded(block.getWorld(),
                    block.getX() + cx, cy, block.getZ() + cz);
            cy--;
            if (target == null) { done++; continue; } // 未ロードチャンクはスキップ（先に進める）
            Material t = target.getType();
            if (t.isAir() || t == Material.BEDROCK || !t.isBlock()) continue;
            if (!consumeEnergy(mjPer)) return; // エネルギー不足 → 次tickへ
            // ドロップ回収
            var drops = target.getDrops();
            target.setType(Material.AIR);
            depositDrops(block, drops);
            done++;
            // 環境負荷
            plugin.world().addEfp(1.0);
        }
    }

    /** 採掘物を隣接インベントリへ入れ、余剰は自然ドロップ */
    private void depositDrops(Block machine, java.util.Collection<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemStack rest = tryInsert(machine, drop);
            if (rest != null) {
                machine.getWorld().dropItemNaturally(machine.getLocation(), rest);
            }
        }
    }

    private ItemStack tryInsert(Block machine, ItemStack stack) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = machine.getRelative(f);
            if (!(adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih)) continue;
            Inventory inv = ih.getInventory();
            HashMap<Integer, ItemStack> rest = inv.addItem(stack);
            if (rest.isEmpty()) return null;
            stack = rest.values().iterator().next();
        }
        return stack;
    }

    @Override
    public Map<String, Object> serialize() {
        var m = super.serialize();
        m.put("cx", cx); m.put("cz", cz); m.put("cy", cy);
        m.put("radius", radius); m.put("finished", finished);
        return m;
    }

    @Override
    public void deserialize(Map<String, Object> m) {
        super.deserialize(m);
        if (m.get("cx") instanceof Number n) cx = n.intValue();
        if (m.get("cz") instanceof Number n) cz = n.intValue();
        if (m.get("cy") instanceof Number n) cy = n.intValue();
        if (m.get("radius") instanceof Number n) radius = n.intValue();
        if (m.get("finished") instanceof Boolean b) finished = b;
    }
}
