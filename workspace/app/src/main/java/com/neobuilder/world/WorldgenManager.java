package com.neobuilder.world;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Random;

/**
 * ワールド生成ロジック（生活ワールド world_life 限定）.
 * 新規チャンク生成時に鉱石・ゴムの木・原油脈を追加生成する.
 * - ウラン鉱石: y<32 に確率生成（エメラルド鉱石ブロックを使用）
 * - イリジウム鉱石: y<16 に極稀生成（ダイヤ鉱石ブロックを使用）
 * - ゴムの木: 地表に低確率で一本（ジャングル原木＋葉）
 * - 原油脈: 砂漠/平地に稀に黒い池（ブラックコンクリート）＋油層
 * TPS保護: 1イベントあたりの操作は小規模（数十ブロック）に限定.
 */
public final class WorldgenManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Random rng = new Random();

    public WorldgenManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        if (!plugin.isLifeWorld(e.getWorld())) return;
        World w = e.getWorld();
        int bx = e.getChunk().getX() * 16, bz = e.getChunk().getZ() * 16;

        // ウラン鉱脈: 60% のチャンクで1-3箇所
        for (int i = 0; i < 3 && rng.nextDouble() < 0.6; i++) {
            vein(w, bx + rng.nextInt(16), 8 + rng.nextInt(24), bz + rng.nextInt(16),
                    Material.EMERALD_ORE, 2 + rng.nextInt(3));
        }
        // イリジウム: 8% のチャンクで1箇所
        if (rng.nextDouble() < 0.08) {
            vein(w, bx + rng.nextInt(16), w.getMinHeight() + 6 + rng.nextInt(10),
                    bz + rng.nextInt(16), Material.DIAMOND_ORE, 1 + rng.nextInt(2));
        }
        // ゴムの木: 3% のチャンクで1本
        if (rng.nextDouble() < 0.03) {
            rubberTree(w, bx + 4 + rng.nextInt(8), bz + 4 + rng.nextInt(8));
        }
        // 原油脈: 1.5% のチャンクで黒い池
        if (rng.nextDouble() < 0.015) {
            oilPool(w, bx + rng.nextInt(16), bz + rng.nextInt(16));
        }
    }

    /** 鉱脈: 起点からランダムウォークでnブロック置換 */
    private void vein(World w, int x, int y, int z, Material ore, int n) {
        for (int i = 0; i < n; i++) {
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() == Material.STONE || b.getType() == Material.DEEPSLATE) {
                b.setType(ore, false);
            }
            x += rng.nextInt(3) - 1; y += rng.nextInt(3) - 1; z += rng.nextInt(3) - 1;
        }
    }

    /** ゴムの木: 原木5+葉（幹の一部は rubber_wood 判定用にジャングル原木） */
    private void rubberTree(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        Block base = w.getBlockAt(x, y, z);
        if (!base.getType().isSolid()) return;
        for (int i = 1; i <= 5; i++) {
            w.getBlockAt(x, y + i, z).setType(Material.JUNGLE_LOG, false);
        }
        for (int dy = 4; dy <= 6; dy++) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.JUNGLE_LEAVES, false);
        }
    }

    /** 原油脈: 浅い黒い池（周辺にオイル染み） */
    private void oilPool(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z) - 1;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 6) continue;
                Block b = w.getBlockAt(x + dx, y, z + dz);
                b.setType(Material.BLACK_CONCRETE, false);
                w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.AIR, false);
            }
    }
}
