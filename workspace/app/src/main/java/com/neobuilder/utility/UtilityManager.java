package com.neobuilder.utility;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;

/**
 * CutAll / MineAll / DigAll 一括破壊ユーティリティ.
 * - 斧: 原木+葉を一括伐採 (CutAll)
 * - ツルハシ: 鉱石鉱脈を一括採掘 (MineAll)
 * - シャベル: 土・砂・砂利を一括掘削 (DigAll)
 * オーバーワールドのみ有効. BFSは反復で上限数・耐久値を考慮する.
 * TPS保護: ブロック破壊は複数tickに分割する.
 */
public final class UtilityManager implements Listener {

    private static final Set<Material> DIG_SET = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.SAND, Material.RED_SAND,
            Material.GRAVEL, Material.CLAY, Material.DIRT_PATH, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM, Material.SOUL_SAND,
            Material.SOUL_SOIL, Material.MUD, Material.MOSS_BLOCK);

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public UtilityManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {}

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        // 生活ワールドのみ
        if (!plugin.isLifeWorld(p.getWorld())) return;
        // クールダウン
        long now = System.currentTimeMillis();
        Long cd = cooldowns.get(p.getUniqueId());
        if (cd != null && now - cd < plugin.getConfig().getInt("mass-break.cooldown-ms", 500)) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null) return;
        Material tt = tool.getType();
        String mode = modeOf(tt, e.getBlock().getType());
        if (mode == null) return;

        cooldowns.put(p.getUniqueId(), now);
        e.setCancelled(true); // 手動で破壊処理（一括キュー）

        // ターゲット判定ルールを構築
        java.util.function.Predicate<Material> target = switch (mode) {
            case "cut" -> m -> Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m);
            case "mine" -> m -> isOre(m);
            case "dig" -> DIG_SET::contains;
            default -> m -> false;
        };

        // 反復BFS（非同期せず上限付きの浅い探索→破壊は分割）
        List<Block> found = bfs(e.getBlock(), target,
                plugin.getConfig().getInt("mass-break.max-blocks", 128), tool);
        if (found.isEmpty()) { e.setCancelled(false); return; }
        scheduleBreak(p, found, tool, 0);
    }

    /** ツールと起点ブロックからモード判定 */
    private String modeOf(Material tool, Material origin) {
        if (Tag.ITEMS_AXES.isTagged(tool)) {
            if (Tag.LOGS.isTagged(origin) || Tag.LEAVES.isTagged(origin)) return "cut";
        }
        if (Tag.ITEMS_PICKAXES.isTagged(tool)) {
            if (isOre(origin)) return "mine";
        }
        if (Tag.ITEMS_SHOVELS.isTagged(tool)) {
            if (DIG_SET.contains(origin)) return "dig";
        }
        return null;
    }

    private boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS
                || m == Material.GILDED_BLACKSTONE;
    }

    /** 反復BFS. 隣接26方向。上限とツール耐久で打ち切る */
    private List<Block> bfs(Block origin, java.util.function.Predicate<Material> target,
                            int maxBlocks, ItemStack tool) {
        List<Block> out = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        visited.add(com.neobuilder.core.util.Blocks.key(origin));
        queue.add(origin);
        // 耐久値の残りを見る
        int durabilityLeft = Integer.MAX_VALUE;
        if (tool.getItemMeta() instanceof Damageable dm) {
            durabilityLeft = tool.getType().getMaxDurability() - dm.getDamage();
        }
        int allowed = Math.min(maxBlocks, Math.max(1, durabilityLeft));
        while (!queue.isEmpty() && out.size() < allowed) {
            Block cur = queue.poll();
            if (!target.test(cur.getType())) continue;
            out.add(cur);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = cur.getRelative(dx, dy, dz);
                        String k = com.neobuilder.core.util.Blocks.key(n);
                        if (visited.add(k) && target.test(n.getType())) queue.add(n);
                    }
                }
            }
        }
        return out;
    }

    /** 破壊を複数tickに分割して適用（TPS保護） */
    private void scheduleBreak(Player p, List<Block> blocks, ItemStack tool, int idx) {
        final int PER_TICK = 8;
        int end = Math.min(blocks.size(), idx + PER_TICK);
        for (int i = idx; i < end; i++) {
            Block b = blocks.get(i);
            if (b.getType().isAir()) continue;
            // 通常破壊（ドロップ・経験値あり）
            b.breakNaturally(tool);
        }
        // ツール耐久を消費
        if (tool.getItemMeta() instanceof Damageable dm) {
            int used = end - idx;
            dm.setDamage(dm.getDamage() + used);
            tool.setItemMeta(dm);
        }
        if (end < blocks.size()) {
            int next = end;
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> scheduleBreak(p, blocks, tool, next), 1L);
        }
    }
}
