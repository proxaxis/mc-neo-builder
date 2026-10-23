package com.neobuilder.world;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ダンジョン自動生成（戦闘用ワールド world_dungeon 限定）.
 * Chocolatin's/Better Dungeons 系の構造を参考にした2種の構造物:
 *  - TOWER: 石塔（3層・各階スポナー・頂上宝箱）
 *  - CRYPT: 地下石室（スポナー・宝箱2つ・TNTトラップ）
 * 確率でボスを同時出現させる.
 * TPS保護: ブロック配置は1tick blocks-per-tick 個に分割してキュー処理する.
 */
public final class DungeonManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Queue<BuildTask> buildQueue = new ConcurrentLinkedQueue<>();
    private BuildTask active;
    private final Random rng = new Random();

    /** 配置ブロック指示 */
    private record Place(int x, int y, int z, Material mat, BlockData data) {}
    /** 構築タスク */
    private static final class BuildTask {
        final World world;
        final Queue<Place> places = new ArrayDeque<>();
        final List<int[]> chests = new ArrayList<>();   // 設置後に loot 充填
        final List<int[]> spawners = new ArrayList<>();
        final List<int[]> traps = new ArrayList<>();
        int[] bossLoc;                                   // [x,y,z] or null
        final Queue<Runnable> after = new ArrayDeque<>(); // ブロック全設置後の後処理
        BuildTask(World w) { this.world = w; }
    }

    public DungeonManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 構築キューを1tickずつ処理
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBuild, 2L, 2L);
    }

    public void disable() { buildQueue.clear(); active = null; }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk()) return;
        if (!plugin.isOtherWorld(e.getWorld())) return;
        if (rng.nextDouble() >= plugin.getConfig().getDouble("dungeon.spawn-chance", 0.03)) return;
        // チャンク中心に構造物を生成
        Chunk c = e.getChunk();
        int cx = c.getX() * 16 + 8;
        int cz = c.getZ() * 16 + 8;
        Location at = new Location(e.getWorld(), cx,
                e.getWorld().getHighestBlockYAt(cx, cz), cz);
        BuildTask task = rng.nextBoolean() ? tower(at) : crypt(at);
        buildQueue.offer(task);
        plugin.getLogger().info("[NeoBuilder] dungeon queued at "
                + at.getWorld().getName() + " " + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ());
    }

    /** キュー先頭の構造物を1tick分割配置する */
    private void tickBuild() {
        if (active == null) active = buildQueue.poll();
        if (active == null) return;
        int budget = plugin.getConfig().getInt("dungeon.blocks-per-tick", 256);
        int done = 0;
        while (done < budget && !active.places.isEmpty()) {
            Place p = active.places.poll();
            if (p == null) break;
            Block b = active.world.getBlockAt(p.x(), p.y(), p.z());
            if (p.data() != null) {
                b.setBlockData(p.data(), false);
            } else {
                b.setType(p.mat(), false);
            }
            done++;
        }
        if (active.places.isEmpty()) {
            // 後処理: 宝箱充填・スポナー設定・ボス出現
            Runnable r;
            while ((r = active.after.poll()) != null) {
                try { r.run(); }
                catch (Exception ex) {
                    plugin.getLogger().warning("[NeoBuilder] dungeon post-build error: " + ex.getMessage());
                }
            }
            active = null;
        }
    }

    // ================= 構造物定義 =================

    /** 石塔: 7x7, 高さ15, 3層・各層スポナー・頂上宝箱 */
    private BuildTask tower(Location at) {
        BuildTask t = new BuildTask(at.getWorld());
        int x0 = at.getBlockX() - 3, z0 = at.getBlockZ() - 3;
        int y0 = at.getBlockY();
        Material wall = Material.STONE_BRICKS;
        Material deco = Material.MOSSY_STONE_BRICKS;

        for (int y = 0; y < 15; y++) {
            for (int dx = 0; dx < 7; dx++) {
                for (int dz = 0; dz < 7; dz++) {
                    boolean edge = dx == 0 || dx == 6 || dz == 0 || dz == 6;
                    boolean floor = y % 5 == 0; // 各層の床
                    int bx = x0 + dx, by = y0 + y, bz = z0 + dz;
                    if (edge) {
                        // 各層に開口部（入口）
                        if (y % 5 >= 1 && y % 5 <= 3 && dx == 3 && dz == 0 && y < 11) {
                            t.places.offer(new Place(bx, by, bz, Material.AIR, null));
                        } else {
                            t.places.offer(new Place(bx, by, bz,
                                    rng.nextDouble() < 0.15 ? deco : wall, null));
                        }
                    } else if (floor) {
                        t.places.offer(new Place(bx, by, bz, wall, null));
                    } else {
                        t.places.offer(new Place(bx, by, bz, Material.AIR, null));
                    }
                }
            }
        }
        // 内部はしご
        for (int y = 0; y < 15; y++) {
            t.places.offer(new Place(x0 + 5, y0 + y, z0 + 5, Material.LADDER,
                    facingData(Material.LADDER, BlockFace.WEST)));
        }
        // 各層中央にスポナー
        for (int fl = 1; fl <= 2; fl++) {
            int sy = y0 + fl * 5 + 1;
            t.places.offer(new Place(x0 + 3, sy, z0 + 3, Material.SPAWNER, null));
            int[] pos = {x0 + 3, sy, z0 + 3};
            EntityType type = fl == 1 ? EntityType.ZOMBIE : EntityType.SKELETON;
            t.after.offer(() -> setSpawner(t.world, pos, type));
        }
        // 頂上宝箱
        t.places.offer(new Place(x0 + 3, y0 + 15, z0 + 3, Material.CHEST, null));
        int[] cp = {x0 + 3, y0 + 15, z0 + 3};
        t.after.offer(() -> fillChest(t.world, cp, 6));
        // ボス
        if (rng.nextDouble() < plugin.getConfig().getDouble("dungeon.boss-chance", 0.15)) {
            int[] bl = {x0 + 3, y0 + 16, z0 + 3};
            t.after.offer(() -> plugin.world().spawnBoss(
                    new Location(t.world, bl[0], bl[1], bl[2])));
        }
        return t;
    }

    /** 地下石室: 11x11x7 の石室を地表の10ブロック下に生成 */
    private BuildTask crypt(Location at) {
        BuildTask t = new BuildTask(at.getWorld());
        int x0 = at.getBlockX() - 5, z0 = at.getBlockZ() - 5;
        int y0 = Math.max(at.getWorld().getMinHeight() + 4, at.getBlockY() - 10);
        Material wall = Material.STONE_BRICKS;

        for (int y = 0; y < 7; y++) {
            for (int dx = 0; dx < 11; dx++) {
                for (int dz = 0; dz < 11; dz++) {
                    boolean edge = dx == 0 || dx == 10 || dz == 0 || dz == 10 || y == 0 || y == 6;
                    int bx = x0 + dx, by = y0 + y, bz = z0 + dz;
                    if (edge) {
                        t.places.offer(new Place(bx, by, bz,
                                rng.nextDouble() < 0.2 ? Material.MOSSY_STONE_BRICKS : wall, null));
                    } else {
                        t.places.offer(new Place(bx, by, bz, Material.AIR, null));
                    }
                }
            }
        }
        // 中央スポナー
        t.places.offer(new Place(x0 + 5, y0 + 1, z0 + 5, Material.SPAWNER, null));
        int[] sp = {x0 + 5, y0 + 1, z0 + 5};
        t.after.offer(() -> setSpawner(t.world, sp, EntityType.CAVE_SPIDER));
        // 宝箱2つ
        t.places.offer(new Place(x0 + 1, y0 + 1, z0 + 1, Material.CHEST, null));
        t.places.offer(new Place(x0 + 9, y0 + 1, z0 + 9, Material.CHEST, null));
        int[] c1 = {x0 + 1, y0 + 1, z0 + 1}, c2 = {x0 + 9, y0 + 1, z0 + 9};
        t.after.offer(() -> { fillChest(t.world, c1, 4); fillChest(t.world, c2, 4); });
        // TNTトラップ: 中央通路に感圧板 + 直下TNT
        t.places.offer(new Place(x0 + 5, y0 + 1, z0 + 2, Material.STONE_PRESSURE_PLATE, null));
        t.places.offer(new Place(x0 + 5, y0, z0 + 2, Material.TNT, null));
        // 松明
        t.places.offer(new Place(x0 + 1, y0 + 3, z0 + 5, Material.WALL_TORCH,
                facingData(Material.WALL_TORCH, BlockFace.EAST)));
        t.places.offer(new Place(x0 + 9, y0 + 3, z0 + 5, Material.WALL_TORCH,
                facingData(Material.WALL_TORCH, BlockFace.WEST)));
        return t;
    }

    // ================= ヘルパー =================

    private BlockData facingData(Material mat, BlockFace face) {
        BlockData d = mat.createBlockData();
        if (d instanceof org.bukkit.block.data.Directional dir) {
            dir.setFacing(face);
        }
        return d;
    }

    private void setSpawner(World w, int[] pos, EntityType type) {
        Block b = w.getBlockAt(pos[0], pos[1], pos[2]);
        if (b.getState() instanceof CreatureSpawner cs) {
            cs.setSpawnedType(type);
            cs.setDelay(200);
            cs.update();
        }
    }

    /** 宝箱へランダム戦利品を充填 */
    private void fillChest(World w, int[] pos, int slots) {
        Block b = w.getBlockAt(pos[0], pos[1], pos[2]);
        if (!(b.getState() instanceof Chest chest)) return;
        var inv = chest.getInventory();
        List<ItemStack> pool = new ArrayList<>();
        pool.add(new ItemStack(Material.IRON_INGOT, 1 + rng.nextInt(4)));
        pool.add(new ItemStack(Material.GOLD_INGOT, 1 + rng.nextInt(3)));
        pool.add(new ItemStack(Material.ARROW, 8 + rng.nextInt(16)));
        pool.add(new ItemStack(Material.BREAD, 2 + rng.nextInt(4)));
        pool.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 1 + rng.nextInt(3)));
        var coin = plugin.items().create("coin", 1 + rng.nextInt(4));
        if (coin != null) pool.add(coin);
        if (rng.nextDouble() < 0.15) {
            var core = plugin.items().create("void_core");
            if (core != null) pool.add(core);
        }
        Collections.shuffle(pool, rng);
        for (int i = 0; i < Math.min(slots, pool.size()); i++) {
            inv.setItem(rng.nextInt(inv.getSize()), pool.get(i));
        }
    }
}
