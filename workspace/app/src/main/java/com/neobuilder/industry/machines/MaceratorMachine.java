package com.neobuilder.industry.machines;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.industry.Machine;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * 粉砕機 (Macerator). 鉱石/原石を粉に変換する.
 * 粉はかまどで精錬してインゴットになる（粉砕で鉱石2倍化）.
 * 入力: 隣接インベントリの鉱石 → 出力: 隣接（別）インベントリ.
 */
public final class MaceratorMachine extends Machine {

    /** 変換結果 */
    private record Recipe(Material product, int count) {}

    /** 粉アイテムIDを返す（粉ならcustom item、そうでなければvanilla倍化） */
    private record Result(String dustId, Material fallback, int count) {}

    private ItemStack processing;
    private ItemStack output;

    public MaceratorMachine(NeoBuilderPlugin plugin, String key) {
        super(plugin, "macerator", key);
        this.maxEnergy = 5000;
    }

    /** 鉱石 → 粉の対応表 */
    private Result recipeFor(Material m) {
        return switch (m) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON -> new Result("dust_iron", null, 2);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD -> new Result("dust_gold", null, 2);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER -> new Result("dust_copper", null, 2);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new Result(null, Material.DIAMOND, 2);
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new Result(null, Material.COAL, 2);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new Result(null, Material.EMERALD, 2);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new Result(null, Material.LAPIS_LAZULI, 4);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new Result(null, Material.REDSTONE, 6);
            case NETHER_QUARTZ_ORE -> new Result(null, Material.QUARTZ, 2);
            case ANCIENT_DEBRIS -> new Result(null, Material.NETHERITE_SCRAP, 2);
            default -> null;
        };
    }

    @Override
    public void tick(Block block) {
        double need = plugin.getConfig().getDouble("macerator.mj-per-operation", 400.0);
        int ticks = plugin.getConfig().getInt("macerator.ticks-per-operation", 100);
        double perTick = need / ticks;

        if (processing == null) {
            ItemStack ore = takeOre(block);
            if (ore == null) return;
            Result r = recipeFor(ore.getType());
            processing = new ItemStack(ore.getType(), 1);
            output = r.dustId() != null ? plugin.items().create(r.dustId(), r.count())
                                        : new ItemStack(r.fallback(), r.count());
            progress = 0;
        }
        // 加工進行（エネルギーがあれば進む）
        if (consumeEnergy(perTick)) {
            progress++;
        }
        if (progress >= ticks) {
            eject(block, output);
            processing = null;
            output = null;
            progress = 0;
        }
    }

    private ItemStack takeOre(Block machine) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian() || f == BlockFace.UP) continue;
            Block adj = machine.getRelative(f);
            if (!(adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih)) continue;
            Inventory inv = ih.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s != null && recipeFor(s.getType()) != null) {
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

    private void eject(Block machine, ItemStack out) {
        // まず上のインベントリへ、だめなら側面へ、それでもだめならドロップ
        Block up = machine.getRelative(BlockFace.UP);
        if (up.getState() instanceof org.bukkit.inventory.InventoryHolder ih) {
            if (ih.getInventory().addItem(out).isEmpty()) return;
        }
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian() || f == BlockFace.UP) continue;
            Block adj = machine.getRelative(f);
            if (!(adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih)) continue;
            if (ih.getInventory().addItem(out).isEmpty()) return;
        }
        machine.getWorld().dropItemNaturally(machine.getLocation(), out);
    }
}
