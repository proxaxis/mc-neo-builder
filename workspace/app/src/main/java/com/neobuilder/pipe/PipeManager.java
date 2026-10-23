package com.neobuilder.pipe;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Wall;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * パイプネットワーク管理.
 * - 抽出パイプ: 隣接インベントリからアイテムを抜き、パイプ網経由で別インベントリへ輸送
 * - テレポートパイプ: 同一周波数の相手へ転送
 * - 黒曜石パイプ: 周囲のドロップアイテムを吸収
 * - キネシスパイプ: MJを中継
 * - ロジスティクス: provider/request のネットワーク索引
 * TPS保護: 1 tick の処理に時間バジェット（config）を設け、BFSにはノード上限をかける.
 */
public final class PipeManager implements Listener {

    private static final int MAX_BFS_NODES = 512;
    private static final int MAX_LOGISTICS_NODES = 1024;

    // パイプ描画用ホスト: 壁ブロック(N/E/S/W高さ+up+waterlogged)を種別ごとに割当
    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    private final NeoBuilderPlugin plugin;
    private final Map<String, Pipe> pipes = new ConcurrentHashMap<>();
    private final Map<String, List<Pipe>> byChunk = new ConcurrentHashMap<>(); // "world:cx,cz" -> pipes
    private final List<Pipe> extracts = new ArrayList<>();   // 抽出パイプ
    private final List<Pipe> obsidians = new ArrayList<>();  // 黒曜石パイプ
    private final List<Pipe> fluids = new ArrayList<>();     // 流体パイプ
    private FluidManager fluidManager;
    private int cursor = 0;
    private int fluidCursor = 0;
    private volatile boolean dirty = false;

    // ロジスティクス索引キャッシュ
    private volatile Map<String, List<LogisticsEntry>> logisticsIndex = Map.of();
    private volatile long logisticsBuiltTick = -1;

    public record LogisticsEntry(Pipe provider, String invKey) {}

    public PipeManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.fluidManager = new FluidManager(plugin, this);
        load();
        // 既存パイプを新ホストブロック描画へ移行
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Pipe p : pipes.values()) applyVisual(p);
        }, 40L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) { dirty = false; save(); }
        }, 1200L, 1200L);
    }

    public void disable() { save(); }

    public int count() { return pipes.size(); }

    public Pipe at(Block b) { return pipes.get(Blocks.key(b)); }

    public FluidManager fluids() { return fluidManager; }

    // --- 登録 ---

    private void addPipe(Pipe p) {
        pipes.put(p.key(), p);
        String[] pr = Blocks.parse(p.key());
        String ck = pr[0] + ":" + (Integer.parseInt(pr[1]) >> 4) + "," + (Integer.parseInt(pr[3]) >> 4);
        byChunk.computeIfAbsent(ck, k -> new ArrayList<>()).add(p);
        if ("pipe_extract".equals(p.type())) synchronized (extracts) { extracts.add(p); }
        if ("pipe_obsidian".equals(p.type())) synchronized (obsidians) { obsidians.add(p); }
        if (p.isFluid()) synchronized (fluids) { fluids.add(p); }
        invalidateLogistics();
        dirty = true;
    }

    private void removePipe(String key) {
        Pipe p = pipes.remove(key);
        if (p == null) return;
        String[] pr = Blocks.parse(key);
        List<Pipe> cl = byChunk.get(pr[0] + ":" + (Integer.parseInt(pr[1]) >> 4) + "," + (Integer.parseInt(pr[3]) >> 4));
        if (cl != null) cl.remove(p);
        synchronized (extracts) { extracts.remove(p); }
        synchronized (obsidians) { obsidians.remove(p); }
        synchronized (fluids) { fluids.remove(p); }
        invalidateLogistics();
        dirty = true;
    }

    private void invalidateLogistics() { logisticsIndex = Map.of(); logisticsBuiltTick = -1; }

    // --- 設置/破壊/操作 ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = plugin.items().itemId(e.getItemInHand());
        if (id == null || !(id.startsWith("pipe_") || id.startsWith("cable_") || id.startsWith("transformer_"))) {
            refreshAround(e.getBlockPlaced()); // チェスト等の設置に追従
            return;
        }
        if (plugin.allegiance().isForbidden(e.getPlayer(), e.getItemInHand())) {
            e.setCancelled(true);
            return;
        }
        Pipe p = new Pipe(id, Blocks.key(e.getBlockPlaced()));
        addPipe(p);
        applyVisual(p);
        refreshAround(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Pipe p = at(b);
        if (p == null) { refreshAround(b); return; }
        removePipe(p.key());
        e.setDropItems(false);
        ItemStack drop = plugin.items().create(p.type());
        if (drop != null) b.getWorld().dropItemNaturally(b.getLocation(), drop);
        refreshAround(b);
    }

    /** チャンク読み込み時にパイプ見た目を再適用（起動時に未ロードだった分の補正） */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        List<Pipe> l = byChunk.get(e.getWorld().getName() + ":" + e.getChunk().getX() + "," + e.getChunk().getZ());
        if (l == null || l.isEmpty()) return;
        for (Pipe p : l) applyVisual(p);
    }

    // --- パイプ見た目（壁blockstate 連動） ---

    private record WallHost(Material mat, boolean wl) {}

    private static final Map<String, WallHost> WALL_HOSTS = Map.ofEntries(
            Map.entry("pipe_transport", new WallHost(Material.COBBLESTONE_WALL, false)),
            Map.entry("pipe_stone", new WallHost(Material.STONE_BRICK_WALL, false)),
            Map.entry("pipe_sandstone", new WallHost(Material.SANDSTONE_WALL, false)),
            Map.entry("pipe_iron", new WallHost(Material.DIORITE_WALL, false)),
            Map.entry("pipe_gold", new WallHost(Material.GRANITE_WALL, false)),
            Map.entry("pipe_diamond", new WallHost(Material.PRISMARINE_WALL, false)),
            Map.entry("pipe_obsidian", new WallHost(Material.BLACKSTONE_WALL, false)),
            Map.entry("pipe_emerald", new WallHost(Material.MOSSY_COBBLESTONE_WALL, false)),
            Map.entry("pipe_void", new WallHost(Material.POLISHED_BLACKSTONE_WALL, false)),
            Map.entry("pipe_extract", new WallHost(Material.ANDESITE_WALL, false)),
            Map.entry("pipe_distribution", new WallHost(Material.MUD_BRICK_WALL, false)),
            Map.entry("pipe_teleport", new WallHost(Material.END_STONE_BRICK_WALL, false)),
            Map.entry("pipe_teleport_structure", new WallHost(Material.DEEPSLATE_TILE_WALL, false)),
            Map.entry("pipe_logistics", new WallHost(Material.BRICK_WALL, false)),
            Map.entry("pipe_provider", new WallHost(Material.NETHER_BRICK_WALL, false)),
            Map.entry("pipe_request", new WallHost(Material.RED_NETHER_BRICK_WALL, false)),
            Map.entry("pipe_supplier", new WallHost(Material.MOSSY_STONE_BRICK_WALL, false)),
            Map.entry("pipe_crafting", new WallHost(Material.POLISHED_DEEPSLATE_WALL, false)),
            Map.entry("pipe_satellite", new WallHost(Material.DEEPSLATE_BRICK_WALL, false)),
            Map.entry("pipe_fluid", new WallHost(Material.COBBLESTONE_WALL, true)),
            Map.entry("pipe_fluid_cobble", new WallHost(Material.STONE_BRICK_WALL, true)),
            Map.entry("pipe_fluid_stone", new WallHost(Material.SANDSTONE_WALL, true)),
            Map.entry("pipe_fluid_sand", new WallHost(Material.PRISMARINE_WALL, true)),
            Map.entry("pipe_fluid_iron", new WallHost(Material.DIORITE_WALL, true)),
            Map.entry("pipe_fluid_gold", new WallHost(Material.GRANITE_WALL, true)),
            Map.entry("pipe_fluid_emerald", new WallHost(Material.MOSSY_COBBLESTONE_WALL, true)),
            Map.entry("pipe_fluid_void", new WallHost(Material.BLACKSTONE_WALL, true)),
            Map.entry("pipe_teleport_fluid", new WallHost(Material.END_STONE_BRICK_WALL, true)),
            Map.entry("pipe_kinesis", new WallHost(Material.TUFF_WALL, false)),
            Map.entry("cable_copper", new WallHost(Material.BRICK_WALL, true)),
            Map.entry("cable_gold", new WallHost(Material.MUD_BRICK_WALL, true)),
            Map.entry("cable_gold_ins2", new WallHost(Material.NETHER_BRICK_WALL, true)),
            Map.entry("cable_hv", new WallHost(Material.RED_NETHER_BRICK_WALL, true)),
            Map.entry("cable_hv_ins2", new WallHost(Material.MOSSY_STONE_BRICK_WALL, true)),
            Map.entry("cable_hv_ins4", new WallHost(Material.POLISHED_DEEPSLATE_WALL, true)),
            Map.entry("cable_glass", new WallHost(Material.ANDESITE_WALL, true)),
            Map.entry("transformer_lv", new WallHost(Material.RED_SANDSTONE_WALL, false)),
            Map.entry("transformer_mv", new WallHost(Material.COBBLED_DEEPSLATE_WALL, false)),
            Map.entry("transformer_hv", new WallHost(Material.TUFF_BRICK_WALL, false)));
    private static final WallHost DEF_HOST = new WallHost(Material.ANDESITE_WALL, false);

    /** パイプ種別 → 見た目ファミリ (item/fluid/energy) — 接続判定用 */
    private String familyOf(Pipe p) {
        if (p.isFluid()) return "fluid";
        if (p.isKinesis()) return "energy";
        return "item";
    }

    private WallHost hostOf(Pipe p) {
        return WALL_HOSTS.getOrDefault(p.type(), DEF_HOST);
    }

    /** 指定方向に腕を出すべきか（同ファミリのパイプ or ファミリ別の接続先） */
    private boolean connects(Pipe self, Block adj) {
        Pipe ap = at(adj);
        if (ap != null) return familyOf(ap).equals(familyOf(self));
        return switch (familyOf(self)) {
            case "fluid" -> adj.getType().name().contains("CAULDRON")
                    || plugin.machines().at(adj) != null;
            case "energy" -> plugin.machines().at(adj) != null;
            default -> adj.getState() instanceof InventoryHolder;
        };
    }

    /** ブロックを種別用の壁ホストにし、隣接状況から接続高さを更新する */
    private void applyVisual(Pipe p) {
        Block b = blockOf(p.key());
        if (b == null) return;
        WallHost host = hostOf(p);
        if (b.getType() != host.mat()) b.setType(host.mat(), false);
        if (!(b.getBlockData() instanceof Wall w)) return;
        boolean changed = false;
        for (BlockFace f : FACES) {
            boolean want = connects(p, b.getRelative(f));
            if (f == BlockFace.UP || f == BlockFace.DOWN) continue; // up/downは非表示
            Wall.Height tgt = want ? Wall.Height.LOW : Wall.Height.NONE;
            if (w.getHeight(f) != tgt) { w.setHeight(f, tgt); changed = true; }
        }
        if (w.isWaterlogged() != host.wl()) { w.setWaterlogged(host.wl()); changed = true; }
        if (changed) b.setBlockData(w, false);
    }

    /** 周囲のパイプの見た目を再計算（非パイプの設置/破壊時にも呼ぶ） */
    private void refreshAround(Block b) {
        for (BlockFace f : FACES) {
            Pipe np = at(b.getRelative(f));
            if (np != null) applyVisual(np);
        }
    }

    /** テレポートパイプの周波数設定・リクエストパイプのGUI */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return; // 両手分の二重発火を防止
        if (e.getClickedBlock() == null || !e.getAction().isRightClick()) return;
        Pipe p = at(e.getClickedBlock());
        if (p == null) return;
        switch (p.type()) {
            case "pipe_teleport" -> {
                e.setCancelled(true);
                p.frequency((p.frequency() + 1) % 10);
                dirty = true;
                e.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(
                        "テレポート周波数: " + p.frequency()));
            }
            case "pipe_request" -> {
                e.setCancelled(true);
                openRequestGui(e.getPlayer(), p);
            }
        }
    }

    // --- ティック処理 ---

    private void tick() {
        long deadline = System.nanoTime()
                + plugin.getConfig().getInt("pipes.tick-budget-ms", 5) * 1_000_000L;
        int perPipe = plugin.getConfig().getInt("pipes.items-per-tick-per-pipe", 1);

        // 抽出パイプ（ラウンドロビン）
        int size;
        synchronized (extracts) { size = extracts.size(); }
        int processed = 0;
        for (int i = 0; i < size && System.nanoTime() < deadline; i++) {
            Pipe p;
            synchronized (extracts) {
                if (extracts.isEmpty()) break;
                cursor = (cursor + 1) % extracts.size();
                p = extracts.get(cursor);
            }
            if (p.cooldown() > 0) { p.cooldown(p.cooldown() - 1); continue; }
            if (processExtract(p, perPipe)) { p.cooldown(4); }
            processed++;
        }
        // 流体パイプ（ラウンドロビン）
        int fsize;
        synchronized (fluids) { fsize = fluids.size(); }
        for (int i = 0; i < fsize && System.nanoTime() < deadline; i++) {
            Pipe p;
            synchronized (fluids) {
                if (fluids.isEmpty()) break;
                fluidCursor = (fluidCursor + 1) % fluids.size();
                p = fluids.get(fluidCursor);
            }
            if (p.cooldown() > 0) { p.cooldown(p.cooldown() - 1); continue; }
            fluidManager.processPipe(p);
            p.cooldown(10); // 333mB / 0.5s 程度の流速
        }
        // 黒曜石パイプ（吸引）は時間があれば処理
        if (System.nanoTime() < deadline) processObsidian();
    }

    /** 抽出パイプ: 隣接インベントリから1アイテム取り出し、目的地へ送る */
    private boolean processExtract(Pipe pipe, int maxItems) {
        Block b = blockOf(pipe.key());
        if (b == null) return false;
        // 抽出元: 隣接インベントリ
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block src = b.getRelative(f);
            if (!(src.getState() instanceof InventoryHolder ih)) continue;
            Inventory inv = ih.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s == null || s.getType().isAir()) continue;
                ItemStack take = s.clone();
                take.setAmount(Math.min(maxItems, s.getAmount()));
                if (route(pipe, take)) {
                    s.setAmount(s.getAmount() - take.getAmount());
                    inv.setItem(i, s.getAmount() > 0 ? s : null);
                    return true;
                }
            }
        }
        return false;
    }

    /** BFS でパイプ網を探索し、最寄りの投入先（インベントリ/テレポート管）へ届ける */
    private boolean route(Pipe from, ItemStack stack) {
        String start = from.key();
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.add(start);
        enqueueAdjPipes(start, visited, queue);
        while (!queue.isEmpty() && visited.size() < MAX_BFS_NODES) {
            String cur = queue.poll();
            Pipe cp = pipes.get(cur);
            if (cp == null) continue;
            // テレポートパイプなら無線転送
            if ("pipe_teleport".equals(cp.type()) && deliverTeleport(cp, stack)) return true;
            // 隣接インベントリへ挿入
            if (deliverAdjacent(cur, stack)) return true;
            enqueueAdjPipes(cur, visited, queue);
        }
        return false;
    }

    private void enqueueAdjPipes(String key, Set<String> visited, Deque<String> queue) {
        Block b = blockOf(key);
        if (b == null) return;
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            String k = Blocks.key(b.getRelative(f));
            if (!visited.contains(k) && pipes.containsKey(k)) {
                Pipe pp = pipes.get(k);
                if (pp.isTransport()) { visited.add(k); queue.add(k); }
            }
        }
    }

    /** パイプに隣接するインベントリへ挿入（抽出元の反対方向チェスト等） */
    private boolean deliverAdjacent(String pipeKey, ItemStack stack) {
        Block b = blockOf(pipeKey);
        if (b == null) return false;
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = b.getRelative(f);
            if (pipes.containsKey(Blocks.key(adj))) continue;
            if (!(adj.getState() instanceof InventoryHolder ih)) continue;
            var rest = ih.getInventory().addItem(stack);
            if (rest.isEmpty()) return true;
            // 部分的に入った場合は残量で次のインベントリへ
            stack = rest.values().iterator().next();
        }
        return false;
    }

    /** テレポート転送: 同一周波数のパイプの隣接インベントリへ */
    private boolean deliverTeleport(Pipe tp, ItemStack stack) {
        for (Pipe other : pipes.values()) {
            if (!"pipe_teleport".equals(other.type())) continue;
            if (other == tp || other.frequency() != tp.frequency()) continue;
            if (deliverAdjacent(other.key(), stack)) return true;
        }
        return false;
    }

    /** 黒曜石パイプ: 周囲のドロップアイテムを吸収して網へ送る */
    private void processObsidian() {
        List<Pipe> copy;
        synchronized (obsidians) { copy = new ArrayList<>(obsidians); }
        for (Pipe p : copy) {
            if (p.cooldown() > 0) { p.cooldown(p.cooldown() - 1); continue; }
            Block b = blockOf(p.key());
            if (b == null) continue;
            var items = b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5),
                    3, 3, 3, e -> e instanceof Item);
            boolean moved = false;
            for (var ent : items) {
                Item it = (Item) ent;
                if (it.getPickupDelay() > 0) continue;
                ItemStack s = it.getItemStack();
                if (route(p, s.clone())) { it.remove(); moved = true; break; }
            }
            if (moved) p.cooldown(8);
        }
    }

    // --- キネシス（エネルギー輸送） ---

    /**
     * エンジンからキネシスパイプ網を通じて消費機器へMJを届ける.
     * 戻り値: 送れなかった残量.
     */
    public double injectEnergy(Block engineBlock, double amount) {
        Set<String> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        double remaining = amount;
        for (BlockFace f : BlockFace.values()) {
            if (f.isCartesian() && isKinesisPipe(engineBlock.getRelative(f))) {
                queue.add(engineBlock.getRelative(f));
            }
        }
        while (!queue.isEmpty() && visited.size() < MAX_BFS_NODES && remaining > 0) {
            Block cur = queue.poll();
            String k = Blocks.key(cur);
            if (!visited.add(k)) continue;
            for (BlockFace f : BlockFace.values()) {
                if (!f.isCartesian()) continue;
                Block adj = cur.getRelative(f);
                Pipe pp = at(adj);
                if (pp != null && pp.isKinesis()) {
                    queue.add(adj);
                } else if (pp == null) {
                    var m = plugin.machines().at(adj);
                    if (m != null) remaining -= m.receiveEnergy(remaining);
                }
            }
        }
        return remaining;
    }

    private boolean isKinesisPipe(Block b) {
        Pipe p = at(b);
        return p != null && p.isKinesis();
    }

    // --- ロジスティクス ---

    /** ネットワーク索引を構築（provider → 隣接インベントリ） */
    public synchronized Map<String, List<LogisticsEntry>> logisticsIndex() {
        long now = Bukkit.getCurrentTick();
        long ttl = plugin.getConfig().getInt("logistics.cache-ttl-ticks", 100);
        if (logisticsBuiltTick >= 0 && now - logisticsBuiltTick < ttl) return logisticsIndex;
        Map<String, List<LogisticsEntry>> idx = new HashMap<>();
        // 各物流管ネットワークを連結成分ごとにまとめる簡易実装:
        // provider/request/logistics 管同士の連結でネットワークを識別
        for (Pipe p : pipes.values()) {
            if (!"pipe_provider".equals(p.type())) continue;
            Block b = blockOf(p.key());
            if (b == null) continue;
            for (BlockFace f : BlockFace.values()) {
                if (!f.isCartesian()) continue;
                Block adj = b.getRelative(f);
                if (adj.getState() instanceof InventoryHolder) {
                    idx.computeIfAbsent(p.key(), k -> new ArrayList<>())
                       .add(new LogisticsEntry(p, Blocks.key(adj)));
                }
            }
        }
        logisticsIndex = idx;
        logisticsBuiltTick = now;
        return idx;
    }

    /** リクエストGUIを開く: ネットワーク内の提供アイテムを列挙 */
    private void openRequestGui(org.bukkit.entity.Player player, Pipe requestPipe) {
        var idx = logisticsIndex();
        if (idx.isEmpty()) {
            player.sendMessage("§e物流網にプロバイダーが見つかりません");
            return;
        }
        plugin.gui().open(player, new RequestGui(plugin, requestPipe, idx));
    }

    /** プロバイダのインベントリから指定アイテムを取り出しリクエスト元へ届ける */
    public boolean fulfillRequest(Pipe requestPipe, ItemStack want, int amount) {
        var idx = logisticsIndex();
        for (var list : idx.values()) {
            for (var e : list) {
                Block inv = blockOf(e.invKey());
                if (inv == null || !(inv.getState() instanceof InventoryHolder ih)) continue;
                var inventory = ih.getInventory();
                ItemStack taken = takeItems(inventory, want, amount);
                if (taken != null) {
                    if (deliverAdjacent(requestPipe.key(), taken)) return true;
                    // 届け先がなければリクエストプレイヤーへ直接
                    inventory.addItem(taken); // 戻す
                }
            }
        }
        return false;
    }

    private ItemStack takeItems(Inventory inv, ItemStack want, int amount) {
        ItemStack got = null;
        for (int i = 0; i < inv.getSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || !s.isSimilar(want)) continue;
            int take = Math.min(amount, s.getAmount());
            if (got == null) { got = s.clone(); got.setAmount(0); }
            got.setAmount(got.getAmount() + take);
            s.setAmount(s.getAmount() - take);
            inv.setItem(i, s.getAmount() > 0 ? s : null);
            amount -= take;
        }
        return got;
    }

    // --- ユーティリティ ---

    private Block blockOf(String key) {
        String[] p = Blocks.parse(key);
        World w = plugin.getServer().getWorld(p[0]);
        if (w == null) return null;
        return Blocks.getIfLoaded(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    public Pipe pipeAt(String key) { return pipes.get(key); }

    // --- 永続化 ---

    private void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Pipe p : pipes.values()) out.add(p.serialize());
        plugin.dataStore().saveAsync("pipes", out);
    }

    @SuppressWarnings("unchecked")
    private void load() {
        var list = plugin.dataStore().load("pipes",
                new com.google.gson.reflect.TypeToken<List<Map<String, Object>>>() {}.getType(),
                Collections.<Map<String, Object>>emptyList());
        for (var m : list) {
            try { addPipe(Pipe.deserialize(m)); } catch (Exception ignored) {}
        }
    }
}
