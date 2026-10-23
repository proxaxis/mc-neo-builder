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
        BuildTask task = switch (rng.nextInt(6)) {
            case 0 -> tower(at);
            case 1 -> crypt(at);
            case 2 -> pirateShip(at);
            case 3 -> desertPyramid(at);
            case 4 -> snowCastle(at);
            default -> necroKeep(at);
        };
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

    // ================= CQ系ダンジョン =================

    /** CQモブ配置ショートカット */
    private void cqMob(BuildTask t, int x, int y, int z,
                       com.neobuilder.cq.CqMobs.Race race, com.neobuilder.cq.CqMobs.Role role) {
        t.after.offer(() -> plugin.cqm().spawn(new Location(t.world, x + 0.5, y, z + 0.5), race, role));
    }
    private void cqBoss(BuildTask t, int x, int y, int z, com.neobuilder.cq.CqBosses.Kind kind) {
        t.after.offer(() -> plugin.cqb().spawn(new Location(t.world, x + 0.5, y, z + 0.5), kind));
    }

    /** 海賊船: 木造船体+マスト+船長室。海賊Mob+海賊船長 */
    private BuildTask pirateShip(Location at) {
        BuildTask t = new BuildTask(at.getWorld());
        int x0 = at.getBlockX() - 6, z0 = at.getBlockZ() - 3, y0 = at.getBlockY();
        // 船底→甲板: 13x7の船体
        for (int dx = 0; dx < 13; dx++) {
            int half = (dx == 0 || dx == 12) ? 1 : (dx == 1 || dx == 11) ? 2 : 3;
            for (int dz = -half; dz <= half; dz++) {
                t.places.offer(new Place(x0 + dx, y0, z0 + dz, Material.DARK_OAK_PLANKS, null));
                t.places.offer(new Place(x0 + dx, y0 - 1, z0 + dz, Material.DARK_OAK_LOG, null));
            }
            for (int dz : new int[]{-half - 1, half + 1}) {
                if (half >= 3) t.places.offer(new Place(x0 + dx, y0 + 1, z0 + dz,
                        Material.DARK_OAK_FENCE, null));
            }
        }
        // マスト2本
        for (int mx : new int[]{x0 + 4, x0 + 9}) {
            for (int dy = 1; dy <= 9; dy++)
                t.places.offer(new Place(mx, y0 + dy, z0, Material.DARK_OAK_LOG, null));
            for (int dx = -2; dx <= 2; dx++)
                for (int dy = 6; dy <= 8; dy++)
                    t.places.offer(new Place(mx + dx, y0 + dy, z0, Material.WHITE_WOOL, null));
        }
        // 船尾楼: 宝箱+金ブロック
        for (int dx = 10; dx <= 12; dx++)
            for (int dz = -2; dz <= 2; dz++)
                t.places.offer(new Place(x0 + dx, y0 + 1, z0 + dz, Material.DARK_OAK_PLANKS, null));
        t.places.offer(new Place(x0 + 11, y0 + 2, z0, Material.CHEST, null));
        int[] cp = {x0 + 11, y0 + 2, z0};
        t.after.offer(() -> fillChest(t.world, cp, 6));
        t.places.offer(new Place(x0 + 12, y0 + 2, z0 - 2, Material.GOLD_BLOCK, null));
        // 海賊4体+船長
        for (int i = 0; i < 4; i++) {
            int px = x0 + 2 + i * 3, py = y0 + 1, pz = z0;
            var role = i == 1 ? com.neobuilder.cq.CqMobs.Role.ARCHER
                    : com.neobuilder.cq.CqMobs.Role.WARRIOR;
            cqMob(t, px, py, pz, com.neobuilder.cq.CqMobs.Race.PIRATE, role);
        }
        cqBoss(t, x0 + 11, y0 + 2, z0, com.neobuilder.cq.CqBosses.Kind.PIRATE_CAPTAIN);
        return t;
    }

    /** 砂漠の砦: 砂岩ピラミッド+地下石室。ミイラ+ファラオ */
    private BuildTask desertPyramid(Location at) {
        BuildTask t = new BuildTask(at.getWorld());
        int x0 = at.getBlockX(), z0 = at.getBlockZ(), y0 = at.getBlockY();
        // ピラミッド: 底辺15、5層
        for (int lvl = 0; lvl < 5; lvl++) {
            int half = 7 - lvl;
            for (int dx = -half; dx <= half; dx++)
                for (int dz = -half; dz <= half; dz++) {
                    boolean edge = Math.abs(dx) == half || Math.abs(dz) == half || lvl == 0;
                    t.places.offer(new Place(x0 + dx, y0 + lvl, z0 + dz,
                            edge ? Material.SANDSTONE : Material.AIR, null));
                }
        }
        // 地下の間: 7x7x3、最深部にファラオ
        int ry = y0 - 4;
        for (int dy = 0; dy < 3; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    boolean edge = dy == 2 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    t.places.offer(new Place(x0 + dx, ry + dy, z0 + dz,
                            edge ? Material.CHISELED_SANDSTONE : Material.AIR, null));
                }
        t.places.offer(new Place(x0 - 2, ry, z0 - 2, Material.CHEST, null));
        int[] cp = {x0 - 2, ry, z0 - 2};
        t.after.offer(() -> fillChest(t.world, cp, 5));
        for (int i = 0; i < 4; i++)
            cqMob(t, x0 - 2 + i, ry, z0 + 2, com.neobuilder.cq.CqMobs.Race.MUMMY,
                    i % 3 == 0 ? com.neobuilder.cq.CqMobs.Role.SPEARMAN
                            : com.neobuilder.cq.CqMobs.Role.WARRIOR);
        cqBoss(t, x0, ry, z0, com.neobuilder.cq.CqBosses.Kind.PHARAOH);
        return t;
    }

    /** 氷原の城: 氷壁の方形城+塔。ウォーカー+ウォーカーキング */
    private BuildTask snowCastle(Location at) {
        BuildTask t = new BuildTask(at.getWorld());
        int x0 = at.getBlockX() - 7, z0 = at.getBlockZ() - 7, y0 = at.getBlockY();
        // 15x15の城壁（高さ6）+ 内部ホール
        for (int y = 0; y < 6; y++)
            for (int dx = 0; dx < 15; dx++)
                for (int dz = 0; dz < 15; dz++) {
                    boolean edge = dx == 0 || dx == 14 || dz == 0 || dz == 14;
                    if (edge) {
                        // 南壁中央に入口
                        if (dz == 0 && dx == 7 && y < 3) {
                            t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz, Material.AIR, null));
                        } else {
                            t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz,
                                    (y + dx + dz) % 4 == 0 ? Material.PACKED_ICE : Material.SNOW_BLOCK, null));
                        }
                    } else if (y == 0) {
                        t.places.offer(new Place(x0 + dx, y0, z0 + dz, Material.STONE_BRICKS, null));
                    } else {
                        t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz, Material.AIR, null));
                    }
                }
        // 中央塔（玉座の間）
        for (int y = 0; y < 9; y++)
            for (int dx = 4; dx <= 10; dx++)
                for (int dz = 4; dz <= 10; dz++) {
                    boolean edge = dx == 4 || dx == 10 || dz == 4 || dz == 10 || y == 0;
                    t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz,
                            edge ? Material.PACKED_ICE : Material.AIR, null));
                }
        t.places.offer(new Place(x0 + 7, y0 + 8, z0 + 7, Material.CHEST, null));
        int[] cp = {x0 + 7, y0 + 8, z0 + 7};
        t.after.offer(() -> fillChest(t.world, cp, 6));
        for (int i = 0; i < 5; i++)
            cqMob(t, x0 + 3 + i * 2, y0 + 1, z0 + 10, com.neobuilder.cq.CqMobs.Race.WALKER,
                    switch (i % 4) {
                        case 1 -> com.neobuilder.cq.CqMobs.Role.DEFENDER;
                        case 2 -> com.neobuilder.cq.CqMobs.Role.SPEARMAN;
                        case 3 -> com.neobuilder.cq.CqMobs.Role.ARCHER;
                        default -> com.neobuilder.cq.CqMobs.Role.WARRIOR;
                    });
        cqBoss(t, x0 + 7, y0 + 7, z0 + 7, com.neobuilder.cq.CqBosses.Kind.WALKER_KING);
        return t;
    }

    /** ネクロマンサーの城: 石の小砦+武装スケルトン+ネクロマンサー */
    private BuildTask necroKeep(Location at) {
        BuildTask t = new BuildTask(at.getWorld());
        int x0 = at.getBlockX() - 5, z0 = at.getBlockZ() - 5, y0 = at.getBlockY();
        // 11x11、高さ8の砦（2層構造）
        for (int y = 0; y < 8; y++)
            for (int dx = 0; dx < 11; dx++)
                for (int dz = 0; dz < 11; dz++) {
                    boolean edge = dx == 0 || dx == 10 || dz == 0 || dz == 10;
                    boolean floor = y == 0 || y == 4 || y == 7;
                    if (edge && !(dz == 0 && dx == 5 && y < 3))
                        t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz,
                                rng.nextDouble() < 0.15 ? Material.MOSSY_STONE_BRICKS
                                        : Material.STONE_BRICKS, null));
                    else if (floor)
                        t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz,
                                Material.STONE_BRICKS, null));
                    else
                        t.places.offer(new Place(x0 + dx, y0 + y, z0 + dz, Material.AIR, null));
                }
        for (int y = 0; y < 8; y++)
            t.places.offer(new Place(x0 + 8, y0 + y, z0 + 8, Material.LADDER,
                    facingData(Material.LADDER, BlockFace.WEST)));
        t.places.offer(new Place(x0 + 5, y0 + 8, z0 + 5, Material.CHEST, null));
        int[] cp = {x0 + 5, y0 + 8, z0 + 5};
        t.after.offer(() -> fillChest(t.world, cp, 6));
        for (int i = 0; i < 5; i++)
            cqMob(t, x0 + 3 + (i % 3) * 3, y0 + (i < 3 ? 1 : 5), z0 + 5,
                    com.neobuilder.cq.CqMobs.Race.SKELETON,
                    i == 4 ? com.neobuilder.cq.CqMobs.Role.MAGE
                            : com.neobuilder.cq.CqMobs.Role.WARRIOR);
        cqBoss(t, x0 + 5, y0 + 8, z0 + 5, com.neobuilder.cq.CqBosses.Kind.NECROMANCER);
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
