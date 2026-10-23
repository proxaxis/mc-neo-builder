package com.neobuilder.industry.machines;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.industry.Machine;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;

/**
 * IC2系マシン群（複数クラスを1ファイルに集約）.
 * create() で type に応じた Machine を生成する.
 */
public final class Ic2Machines {

    private Ic2Machines() {}

    public static Machine create(NeoBuilderPlugin plugin, String key, String type) {
        return switch (type) {
            case "generator" -> new GeneratorMachine(plugin, key);
            case "solar_panel" -> new SolarMachine(plugin, key);
            case "nuclear_reactor" -> new EngineMachine(plugin, key, EngineMachine.Kind.NUCLEAR);
            case "batbox", "mfe", "mfsu" -> new BatteryMachine(plugin, key, type);
            case "pump" -> new PumpMachine(plugin, key);
            case "refinery" -> new RefineryMachine(plugin, key);
            case "tank" -> new TankMachine(plugin, key);
            case "mass_fabricator" -> new MassFabMachine(plugin, key);
            case "tesla_coil" -> new TeslaCoilMachine(plugin, key);
            case "auto_workbench" -> new AutoWorkbenchMachine(plugin, key);
            case "teleporter" -> new TeleporterMachine(plugin, key);
            case "electric_furnace", "induction_furnace", "iron_furnace" -> new PoweredFurnaceMachine(plugin, key, type);
            case "compressor" -> new CompressorMachine(plugin, key);
            case "extractor" -> new ExtractorMachine(plugin, key);
            default -> null;
        };
    }

    // ---------- 共通ヘルパー ----------

    static Inventory findInventory(Block b, boolean includeUp) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian() || (f == BlockFace.UP && !includeUp)) continue;
            Block adj = b.getRelative(f);
            if (adj.getState() instanceof org.bukkit.inventory.InventoryHolder ih) return ih.getInventory();
        }
        return null;
    }

    static ItemStack takeOne(Inventory inv, java.util.function.Predicate<ItemStack> pred) {
        if (inv == null) return null;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && pred.test(s)) {
                ItemStack t = s.clone();
                t.setAmount(1);
                s.setAmount(s.getAmount() - 1);
                inv.setItem(i, s.getAmount() > 0 ? s : null);
                return t;
            }
        }
        return null;
    }

    static void output(Block machine, ItemStack out) {
        Block up = machine.getRelative(BlockFace.UP);
        if (up.getState() instanceof org.bukkit.inventory.InventoryHolder ih) {
            if (ih.getInventory().addItem(out).isEmpty()) return;
        }
        Inventory inv = findInventory(machine, false);
        if (inv != null && inv.addItem(out).isEmpty()) return;
        machine.getWorld().dropItemNaturally(machine.getLocation(), out);
    }

    // ---------- 発電機（固体燃料） ----------
    static class GeneratorMachine extends Machine {
        private double burn;
        GeneratorMachine(NeoBuilderPlugin p, String k) { super(p, "generator", k); this.maxEnergy = 4000; }
        @Override public void tick(Block b) {
            if (burn > 0) { burn--; energy += 10; }
            else {
                ItemStack fuel = takeOne(findInventory(b, false),
                        s -> s.getType() == Material.COAL || s.getType() == Material.CHARCOAL
                                || s.getType() == Material.BLAZE_ROD);
                if (fuel != null) burn = fuel.getType() == Material.BLAZE_ROD ? 240 : 160;
            }
            energy = Math.min(energy, maxEnergy);
            if (energy > 20) energy = plugin.energy().distributeToNeighbors(b, energy * 0.5) ;
        }
    }

    // ---------- ソーラーパネル ----------
    static class SolarMachine extends Machine {
        SolarMachine(NeoBuilderPlugin p, String k) { super(p, "solar_panel", k); this.maxEnergy = 1000; }
        @Override public void tick(Block b) {
            if (b.getWorld().isDayTime() && b.getWorld().getHighestBlockYAt(b.getLocation()) <= b.getY() + 1) {
                energy = Math.min(energy + 1, maxEnergy); // 1 MJ/t
            }
            if (energy > 4) energy = plugin.energy().distributeToNeighbors(b, energy * 0.5);
        }
    }

    // ---------- 蓄電機（MJバッテリ）----------
    static class BatteryMachine extends Machine {
        private final double rateOut;
        BatteryMachine(NeoBuilderPlugin p, String k, String type) {
            super(p, type, k);
            this.maxEnergy = switch (type) { case "batbox" -> 40000; case "mfe" -> 600000; default -> 10000000; };
            this.rateOut = switch (type) { case "batbox" -> 32; case "mfe" -> 128; default -> 512; };
        }
        @Override public void tick(Block b) {
            // 蓄えたエネルギーをrateOut/tまで周囲へ放電
            double out = Math.min(energy, rateOut);
            if (out > 0) energy = plugin.energy().distributeToNeighbors(b, out);
        }
    }

    // ---------- ポンプ（水を汲み上げ大釜へ） ----------
    static class PumpMachine extends Machine {
        PumpMachine(NeoBuilderPlugin p, String k) { super(p, "pump", k); this.maxEnergy = 500; }
        @Override public void tick(Block b) {
            if (progress++ % 40 != 0) return; // 2秒周期
            if (!consumeEnergy(100)) return;
            // 直下の水源を探す
            Block below = b.getRelative(0, -1, 0);
            for (int dy = 0; dy < 8 && below.getType() != Material.WATER; dy++)
                below = below.getRelative(0, -1, 0);
            if (below.getType() != Material.WATER) return;
            // 隣接大釜へ水を注ぐ
            for (BlockFace f : BlockFace.values()) {
                if (!f.isCartesian()) continue;
                Block adj = b.getRelative(f);
                if (adj.getType() == Material.CAULDRON) { adj.setType(Material.WATER_CAULDRON); return; }
                if (adj.getType() == Material.WATER_CAULDRON
                        && adj.getBlockData() instanceof org.bukkit.block.data.Levelled lv
                        && lv.getLevel() < lv.getMaximumLevel()) {
                    lv.setLevel(lv.getLevel() + 1); adj.setBlockData(lv); return;
                }
            }
        }
    }

    // ---------- 精製所（原油→燃料） ----------
    static class RefineryMachine extends Machine {
        RefineryMachine(NeoBuilderPlugin p, String k) { super(p, "refinery", k); this.maxEnergy = 3000; }
        @Override public void tick(Block b) {
            if (progress > 0) {
                if (consumeEnergy(10) && ++progress >= 100) {
                    output(b, plugin.items().create("fuel_bucket"));
                    progress = 0;
                }
                return;
            }
            ItemStack oil = takeOne(findInventory(b, false),
                    s -> "oil_bucket".equals(plugin.items().itemId(s)) || s.getType() == Material.LAVA_BUCKET);
            if (oil != null) progress = 1;
        }
    }

    // ---------- タンク（大容量流体貯蔵） ----------
    static class TankMachine extends Machine {
        private String fluid = "none"; // water/lava
        private int level;             // 333mB 単位 (max 48 = 16000mB)
        TankMachine(NeoBuilderPlugin p, String k) { super(p, "tank", k); }
        public String fluid() { return fluid; }
        public int level() { return level; }
        public boolean insertFluid(String f, int amt) {
            if (!"none".equals(fluid) && !fluid.equals(f)) return false;
            int cap = 48 - level;
            if (cap <= 0) return false;
            fluid = f; level += Math.min(cap, amt);
            return true;
        }
        public boolean drainFluid(int amt) {
            if (level <= 0) return false;
            level -= Math.min(level, amt);
            if (level <= 0) fluid = "none";
            return true;
        }
        @Override public void tick(Block b) {}
        @Override public Map<String, Object> serialize() {
            var m = super.serialize(); m.put("fluid", fluid); m.put("level", level); return m;
        }
        @Override public void deserialize(Map<String, Object> m) {
            super.deserialize(m);
            fluid = String.valueOf(m.getOrDefault("fluid", "none"));
            if (m.get("level") instanceof Number n) level = n.intValue();
        }
    }

    // ---------- 物質生成機 ----------
    static class MassFabMachine extends Machine {
        MassFabMachine(NeoBuilderPlugin p, String k) { super(p, "mass_fabricator", k); this.maxEnergy = 100000; }
        @Override public void tick(Block b) {
            if (progress >= 1000) { // 1000tick×1MJ→大容量
                output(b, plugin.items().create("matter"));
                progress = 0;
            } else if (consumeEnergy(1)) progress++;
        }
    }

    // ---------- テスラコイル ----------
    static class TeslaCoilMachine extends Machine {
        TeslaCoilMachine(NeoBuilderPlugin p, String k) { super(p, "tesla_coil", k); this.maxEnergy = 5000; }
        @Override public void tick(Block b) {
            if (progress++ % 10 != 0) return;
            if (!consumeEnergy(50)) return;
            for (var ent : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5), 4, 4, 4)) {
                if (ent instanceof Monster mob) {
                    mob.damage(6.0);
                    b.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                            mob.getLocation().add(0, 1, 0), 15);
                }
            }
        }
    }

    // ---------- 自動作業台（固定レシピで自動クラフト） ----------
    static class AutoWorkbenchMachine extends Machine {
        AutoWorkbenchMachine(NeoBuilderPlugin p, String k) { super(p, "auto_workbench", k); this.maxEnergy = 500; }
        @Override public void tick(Block b) {
            if (progress++ % 20 != 0) return;
            Inventory inv = findInventory(b, false);
            if (inv == null || !consumeEnergy(10)) return;
            // 固定レシピ: 原木→板材x4 / インゴット9個→ブロック / 丸石→石
            ItemStack out = null;
            if (takeOne(inv, s -> org.bukkit.Tag.LOGS.isTagged(s.getType())) != null) {
                out = new ItemStack(Material.OAK_PLANKS, 4);
            } else if (takeOne(inv, s -> s.getType() == Material.COBBLESTONE) != null) {
                out = new ItemStack(Material.STONE, 1);
            }
            if (out != null) output(b, out);
        }
    }

    // ---------- テレポーター（ペアで双方向） ----------
    static class TeleporterMachine extends Machine {
        TeleporterMachine(NeoBuilderPlugin p, String k) { super(p, "teleporter", k); this.maxEnergy = 10000; }
        @Override public void tick(Block b) {
            if (progress++ % 20 != 0) return;
            // 同型マシンのうち他方を探して乗っているエンティティを送る
            var self = key;
            for (var e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 1, 0.5), 0.6, 1.2, 0.6)) {
                // 別テレポーターを探索（同一ワールド・近距離は探索コスト低）
                Machine target = plugin.machines().machines().values().stream()
                        .filter(m -> "teleporter".equals(m.type()) && !m.key().equals(self)
                                && m.block() != null && m.block().getWorld() == b.getWorld())
                        .findFirst().orElse(null);
                if (target != null && consumeEnergy(500)) {
                    e.teleport(target.block().getLocation().add(0.5, 1, 0.5));
                }
            }
        }
    }

    // ---------- 電気かまど系 ----------
    static class PoweredFurnaceMachine extends Machine {
        private final int ticks;
        PoweredFurnaceMachine(NeoBuilderPlugin p, String k, String type) {
            super(p, type, k);
            this.maxEnergy = 2000;
            this.ticks = switch (type) { case "induction_furnace" -> 40; case "iron_furnace" -> 80; default -> 100; };
        }
        @Override public void tick(Block b) {
            if (progress == 0) {
                Inventory inv = findInventory(b, false);
                if (inv == null) return;
                ItemStack out = smeltOf(takeSmeltable(inv));
                if (out == null) { giveBack(b); return; }
                progress = 1;
            }
            if (consumeEnergy(5) && ++progress >= ticks) {
                Inventory inv = findInventory(b, false);
                ItemStack smelted = takeSmeltable(inv);
                if (smelted != null) {
                    ItemStack out = smeltOf(smelted);
                    if (out != null) output(b, out);
                }
                progress = 0;
            }
        }
        private void giveBack(Block b) {} // 簡易: 原料はプロセス中保持扱い
        private ItemStack takeSmeltable(Inventory inv) {
            return takeOne(inv, s -> smeltOf(s) != null);
        }
        private ItemStack smeltOf(ItemStack in) {
            if (in == null) return null;
            var it = plugin.getServer().recipeIterator();
            while (it.hasNext()) {
                Recipe r = it.next();
                if (r instanceof org.bukkit.inventory.FurnaceRecipe fr
                        && fr.getInput().getType() == in.getType()) return fr.getResult();
            }
            return null;
        }
    }

    // ---------- 圧縮機 ----------
    static class CompressorMachine extends Machine {
        CompressorMachine(NeoBuilderPlugin p, String k) { super(p, "compressor", k); this.maxEnergy = 2000; }
        @Override public void tick(Block b) {
            if (progress == 0) {
                Inventory inv = findInventory(b, false);
                if (inv == null) return;
                if (countTake(inv, s -> s.getType() == Material.COAL, 9)) {
                    pending = new ItemStack(Material.COAL_BLOCK);
                    progress = 1;
                } else if (takeOne(inv, s -> "raw_carbon_fibre".equals(plugin.items().itemId(s))) != null
                        && takeOne(inv, s -> "raw_carbon_fibre".equals(plugin.items().itemId(s))) != null) {
                    pending = plugin.items().create("raw_carbon_mesh");
                    progress = 1;
                }
            }
            if (progress > 0 && consumeEnergy(10) && ++progress >= 60) {
                output(b, pending); pending = null; progress = 0;
            }
        }
        private ItemStack pending;
        private boolean countTake(Inventory inv, java.util.function.Predicate<ItemStack> pred, int n) {
            int found = 0;
            for (var s : inv.getContents()) if (s != null && pred.test(s)) found += s.getAmount();
            if (found < n) return false;
            for (int i = 0; i < inv.getSize() && n > 0; i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || !pred.test(s)) continue;
                int t = Math.min(n, s.getAmount());
                s.setAmount(s.getAmount() - t); n -= t;
                inv.setItem(i, s.getAmount() > 0 ? s : null);
            }
            return true;
        }
    }

    // ---------- 抽出機 ----------
    static class ExtractorMachine extends Machine {
        ExtractorMachine(NeoBuilderPlugin p, String k) { super(p, "extractor", k); this.maxEnergy = 2000; }
        @Override public void tick(Block b) {
            if (progress == 0) {
                Inventory inv = findInventory(b, false);
                if (inv == null) return;
                // ゴム原木→ゴム / スイカ→スイカの種 など
                ItemStack t = takeOne(inv, s -> "rubber_wood".equals(plugin.items().itemId(s)));
                if (t != null) { pending = plugin.items().create("rubber"); progress = 1; }
            }
            if (progress > 0 && consumeEnergy(10) && ++progress >= 60) {
                output(b, pending); pending = null; progress = 0;
            }
        }
        private ItemStack pending;
    }
}
