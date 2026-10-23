package com.neobuilder.pipe;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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

    /** 表示用ディスプレイのアイテムcmd: core=31100+i, arm=31300+i（gen_pack.py の PIPE_HOSTS 順と一致） */
    private static final String[] PIPE_TYPES = {
            "pipe_transport", "pipe_stone", "pipe_sandstone", "pipe_iron", "pipe_gold",
            "pipe_diamond", "pipe_obsidian", "pipe_emerald", "pipe_void", "pipe_extract",
            "pipe_distribution", "pipe_teleport", "pipe_teleport_structure",
            "pipe_logistics", "pipe_provider", "pipe_request", "pipe_supplier",
            "pipe_crafting", "pipe_satellite", "pipe_fluid", "pipe_fluid_cobble",
            "pipe_fluid_stone", "pipe_fluid_sand", "pipe_fluid_iron", "pipe_fluid_gold",
            "pipe_fluid_emerald", "pipe_fluid_void", "pipe_teleport_fluid", "pipe_kinesis",
            "cable_copper", "cable_gold", "cable_gold_ins2", "cable_hv",
            "cable_hv_ins2", "cable_hv_ins4", "cable_glass",
            "transformer_lv", "transformer_mv", "transformer_hv"};

    /** パイプの設置ベース: 非クラフト・破壊可能なカルサイト（見た目はDisplayで上書き） */
    private static final Material BASE = Material.CALCITE;

    private final NeoBuilderPlugin plugin;
    private org.bukkit.NamespacedKey visKey;
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
        this.visKey = plugin.key("pipe_vis");
        this.fluidManager = new FluidManager(plugin, this);
        load();
        // 既存パイプを新ホストブロック描画へ移行
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Pipe p : pipes.values()) applyVisual(p);
        }, 40L);
        // MODクライアント向けカスタムブロック配信チャネル
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "neobuilder:blocks");
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
        encCache.remove(key);
        broadcastPipe(key, -1); // op=2 除去
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
        e.getBlockPlaced().setType(BASE, false);
        applyVisual(p);
        refreshAround(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Pipe p = at(b);
        if (p == null) { refreshAround(b); return; }
        removePipe(p.key());
        clearVisual(b);
        e.setDropItems(false);
        ItemStack drop = plugin.items().create(p.type());
        if (drop != null) b.getWorld().dropItemNaturally(b.getLocation(), drop);
        refreshAround(b);
    }

    /** 爆発でベースブロックが消えても表示エンティティが残らないよう除去 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { explodePipes(e.blockList()); }
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { explodePipes(e.blockList()); }

    private void explodePipes(List<Block> blocks) {
        for (Block b : blocks) {
            Pipe p = at(b);
            if (p == null) continue;
            removePipe(p.key());
            clearVisual(b);
        }
    }

    /** チャンク読み込み時にパイプ見た目を再適用（起動時に未ロードだった分の補正） */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        List<Pipe> l = byChunk.get(e.getWorld().getName() + ":" + e.getChunk().getX() + "," + e.getChunk().getZ());
        if (l == null || l.isEmpty()) return;
        for (Pipe p : l) applyVisual(p);
    }

    /**
     * パイプホストの物理更新を凍結する。
     * 壁/ペインは隣接更新で接続プロパティ(up/高さ/面)を自動再計算してしまうため、
     * 登録済みパイプではバニラの再計算をキャンセルし、表示状態をサーバ管理値で固定する。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() != BASE) return;
        if (at(e.getBlock()) != null) e.setCancelled(true);
    }

    // --- パイプ見た目（ItemDisplay描画。ベース=カルサイトでバニラの見た目を汚染しない） ---

    /** パイプ種別 → 見た目ファミリ (item/fluid/energy) — 接続判定用 */
    private String familyOf(Pipe p) {
        if (p.isFluid()) return "fluid";
        if (p.isKinesis()) return "energy";
        return "item";
    }

    private static int typeIndex(String type) {
        for (int i = 0; i < PIPE_TYPES.length; i++)
            if (PIPE_TYPES[i].equals(type)) return i;
        return 0;
    }

    /** 腕モデルはnorth(-Z)向き。他方向は回転で表現 */
    private static Quaternionf armRotation(BlockFace f) {
        return switch (f) {
            case SOUTH -> new Quaternionf().rotateY((float) Math.PI);
            case EAST  -> new Quaternionf().rotateY((float) -Math.PI / 2);
            case WEST  -> new Quaternionf().rotateY((float) Math.PI / 2);
            case UP    -> new Quaternionf().rotateX((float) Math.PI / 2);
            case DOWN  -> new Quaternionf().rotateX((float) -Math.PI / 2);
            default -> null;
        };
    }

    private ItemStack dispItem(int cmd) {
        ItemStack s = new ItemStack(Material.STICK);
        var m = s.getItemMeta();
        m.setCustomModelData(cmd);
        s.setItemMeta(m);
        return s;
    }

    /** ベースブロック内の既存表示を除去（重複/残骸対策） */
    private void clearVisual(Block b) {
        for (var e : b.getWorld().getNearbyEntities(b.getLocation().add(0.5, 0.5, 0.5),
                0.6, 0.6, 0.6, ent -> ent instanceof ItemDisplay))
            if (e.getPersistentDataContainer().has(visKey)) e.remove();
    }

    /** ベースブロックをカルサイトにし、中心+接続方向の腕をItemDisplayで描画する */
    private void applyVisual(Pipe p) {
        Block b = blockOf(p.key());
        if (b == null) return;
        if (b.getType() != BASE) b.setType(BASE, false);
        clearVisual(b);
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        int idx = typeIndex(p.type());
        spawnDisp(c, dispItem(31100 + idx), null);
        int mask = 0;
        for (BlockFace f : FACES) {
            if (!connects(p, b.getRelative(f))) continue;
            mask |= maskBit(f);
            spawnDisp(c, dispItem(31300 + idx), armRotation(f));
        }
        syncPipe(p.key(), idx << 6 | mask);
    }

    private static int maskBit(BlockFace f) {
        return switch (f) {
            case NORTH -> 1; case EAST -> 2; case SOUTH -> 4;
            case WEST -> 8; case UP -> 16; default -> 32;
        };
    }

    // --- MODクライアントへのカスタムブロック配信 ---
    // payload: op(varint: 0=全消去 1=登録 2=除去) + 件数 + [x,y,z,state]*n
    private final Map<String, Integer> encCache = new ConcurrentHashMap<>();

    private static void writeVarInt(java.io.DataOutputStream o, int v) throws java.io.IOException {
        while ((v & ~0x7F) != 0) { o.writeByte(v & 0x7F | 0x80); v >>>= 7; }
        o.writeByte(v);
    }

    private void broadcastPipe(String key, int enc) {
        String[] pr = Blocks.parse(key);
        int x = Integer.parseInt(pr[1]), y = Integer.parseInt(pr[2]), z = Integer.parseInt(pr[3]);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream o = new java.io.DataOutputStream(bo);
            writeVarInt(o, enc < 0 ? 2 : 1);
            writeVarInt(o, 1);
            o.writeInt(x); o.writeInt(y); o.writeInt(z);
            writeVarInt(o, Math.max(enc, 0));
            byte[] data = bo.toByteArray();
            for (var pl : Bukkit.getOnlinePlayers())
                pl.sendPluginMessage(plugin, "neobuilder:blocks", data);
        } catch (java.io.IOException ignored) {}
    }

    private void syncPipe(String key, int enc) {
        encCache.put(key, enc);
        broadcastPipe(key, enc);
    }

    /** ログイン時に全パイプ位置マップを配信 */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        if (encCache.isEmpty()) return;
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream o = new java.io.DataOutputStream(bo);
            writeVarInt(o, 1);
            writeVarInt(o, encCache.size());
            for (var en : encCache.entrySet()) {
                String[] pr = Blocks.parse(en.getKey());
                o.writeInt(Integer.parseInt(pr[1]));
                o.writeInt(Integer.parseInt(pr[2]));
                o.writeInt(Integer.parseInt(pr[3]));
                writeVarInt(o, en.getValue());
            }
            e.getPlayer().sendPluginMessage(plugin, "neobuilder:blocks", bo.toByteArray());
        } catch (java.io.IOException ignored) {}
    }

    private void spawnDisp(Location c, ItemStack item, Quaternionf rot) {
        c.getWorld().spawn(c, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.getPersistentDataContainer().set(visKey,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            d.setTransformation(new Transformation(new Vector3f(),
                    rot == null ? new Quaternionf() : rot,
                    new Vector3f(1.001f), new Quaternionf()));
        });
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
            // ヴォイドパイプ: アイテムを廃棄（終端。中継もしない）
            if ("pipe_void".equals(cp.type())) return true;
            // テレポートパイプなら無線転送
            if ("pipe_teleport".equals(cp.type()) && deliverTeleport(cp, stack)) return true;
            // 分配パイプ: 隣接インベントリへラウンドロビン投入
            if ("pipe_distribution".equals(cp.type()) && deliverRoundRobin(cp, stack)) return true;
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

    /** 分配パイプ: 隣接インベントリへラウンドロビン投入 */
    private final Map<String, Integer> rrCursor = new HashMap<>();

    private boolean deliverRoundRobin(Pipe dp, ItemStack stack) {
        Block b = blockOf(dp.key());
        if (b == null) return false;
        List<Block> invs = new ArrayList<>();
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = b.getRelative(f);
            if (pipes.containsKey(Blocks.key(adj))) continue;
            if (adj.getState() instanceof InventoryHolder) invs.add(adj);
        }
        if (invs.isEmpty()) return false;
        int start = rrCursor.getOrDefault(dp.key(), 0) % invs.size();
        for (int i = 0; i < invs.size(); i++) {
            int idx = (start + i) % invs.size();
            var ih = (InventoryHolder) invs.get(idx).getState();
            if (ih.getInventory().addItem(stack.clone()).isEmpty()) {
                rrCursor.put(dp.key(), idx + 1);
                return true;
            }
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
