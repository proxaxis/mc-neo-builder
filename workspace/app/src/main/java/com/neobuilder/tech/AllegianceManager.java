package com.neobuilder.tech;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.item.ItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技術陣営（科学/魔法）の排他制御マネージャ.
 * プレイヤーが未選択の場合 /tech で選択GUIを開く.
 * 選択済み陣営と矛盾するアイテムの所持・取得・搬入を監視し拒否する.
 */
public final class AllegianceManager implements Listener {

    public enum Allegiance {
        NONE, SCIENCE, MAGIC;
        public static Allegiance of(String s) {
            if (s == null) return NONE;
            return switch (s) { case "science" -> SCIENCE; case "magic" -> MAGIC; default -> NONE; };
        }
    }

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, Allegiance> allegiances = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public AllegianceManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Map<String, Object> data = plugin.dataStore().loadMap("allegiance");
        data.forEach((k, v) -> allegiances.put(UUID.fromString(k), Allegiance.of(String.valueOf(v))));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) { dirty = false; save(); }
        }, 1200L, 1200L);
    }

    public void disable() { save(); }

    private void save() {
        Map<String, Object> out = new ConcurrentHashMap<>();
        allegiances.forEach((k, v) -> out.put(k.toString(), v.name().toLowerCase()));
        plugin.dataStore().saveAsync("allegiance", out);
    }

    public Allegiance get(UUID id) { return allegiances.getOrDefault(id, Allegiance.NONE); }

    /** 陣営を設定（一度きりの選択。リセットは別途有償） */
    public boolean select(Player p, Allegiance a) {
        if (a == Allegiance.NONE) return false;
        Allegiance cur = get(p.getUniqueId());
        if (cur != Allegiance.NONE) return false;
        allegiances.put(p.getUniqueId(), a);
        dirty = true;
        // 選択確定直後に反陣営アイテムを掃除
        sanitize(p);
        return true;
    }

    /** 陣営リセット（有償） */
    public boolean reset(Player p, double cost) {
        if (get(p.getUniqueId()) == Allegiance.NONE) return false;
        if (!plugin.economy().withdraw(p.getUniqueId(), cost)) return false;
        allegiances.put(p.getUniqueId(), Allegiance.NONE);
        dirty = true;
        return true;
    }

    public void openSelectionGui(Player p) {
        if (get(p.getUniqueId()) != Allegiance.NONE) {
            p.sendMessage(Component.text("あなたは既に「" + label(get(p.getUniqueId())) + "」を選択しています", NamedTextColor.YELLOW));
            return;
        }
        plugin.gui().open(p, new TechSelectGui(plugin, this));
    }

    public static String label(Allegiance a) {
        return switch (a) { case SCIENCE -> "科学技術"; case MAGIC -> "魔法技術"; default -> "未選択"; };
    }

    /** アイテムが当該プレイヤーに禁止されているか */
    public boolean isForbidden(Player p, ItemStack item) {
        Allegiance a = get(p.getUniqueId());
        if (a == Allegiance.NONE) return false;
        ItemRegistry.Tech t = plugin.items().techOf(item);
        if (t == ItemRegistry.Tech.NONE) return false;
        return (a == Allegiance.SCIENCE && t == ItemRegistry.Tech.MAGIC)
                || (a == Allegiance.MAGIC && t == ItemRegistry.Tech.SCIENCE);
    }

    /** インベントリ内の禁止アイテムを除去してドロップ（保持バイパス防止の掃除） */
    public void sanitize(Player p) {
        Allegiance a = get(p.getUniqueId());
        if (a == Allegiance.NONE) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && isForbidden(p, s)) {
                inv.setItem(i, null);
                p.getWorld().dropItemNaturally(p.getLocation(), s);
            }
        }
    }

    // --- 監視リスナー ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (isForbidden(p, e.getItem().getItemStack())) {
            e.setCancelled(true);
            p.sendMessage(Component.text("選択した技術と矛盾するアイテムです", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // 禁止アイテムを自分のインベントリに移す操作を拒否
        ItemStack cur = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (isForbidden(p, cursor) || (cur != null && isForbidden(p, cur)
                && e.getClickedInventory() != null
                && e.getClickedInventory().equals(p.getInventory()))) {
            e.setCancelled(true);
            p.sendMessage(Component.text("選択した技術と矛盾するアイテムです", NamedTextColor.RED));
        }
    }

    /** ホッパー等による自動搬入も監視 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent e) {
        // コンテナオーナーがプレイヤーの場合のみ排他チェック（他のmod整理機構には干渉しない）
        if (e.getDestination().getHolder() instanceof Player p) {
            if (isForbidden(p, e.getItem())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        // ドロップ自体は許可（他人が拾えば取引経路になりうるが、拾得時に排他チェックが走る）
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (get(p.getUniqueId()) == Allegiance.NONE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) openSelectionGui(p);
            }, 60L);
        }
    }
}
