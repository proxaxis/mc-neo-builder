package com.neobuilder.gui;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * カスタムGUI基盤.
 * 開いている Inventory と NbGui ハンドラを紐付け、クリックを振り分ける.
 * 仮想UI内のアイテム持ち出しはすべてキャンセルする.
 */
public final class GuiManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, NbGui> open = new ConcurrentHashMap<>();

    public GuiManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** GUI を開く */
    public void open(Player p, NbGui gui) {
        Inventory inv = gui.buildInventory(p);
        open.put(p.getUniqueId(), gui);
        p.openInventory(inv);
    }

    public void close(Player p) {
        open.remove(p.getUniqueId());
        p.closeInventory();
    }

    public NbGui current(Player p) { return open.get(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        NbGui gui = open.get(p.getUniqueId());
        if (gui == null) return;
        // 自分のインベントリ側シフトクリック等もキャンセルして持ち出し/入れ込みを防ぐ
        e.setCancelled(true);
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(gui.inventory())) {
            try {
                gui.onClick(p, e.getSlot(), e.getClick());
            } catch (Exception ex) {
                plugin.getLogger().warning("[NeoBuilder] GUI click error: " + ex.getMessage());
                close(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        NbGui gui = open.get(p.getUniqueId());
        if (gui == null) return;
        // GUI領域へのドラッグは全キャンセル
        for (int slot : e.getRawSlots()) {
            if (slot < gui.inventory().getSize()) { e.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        NbGui gui = open.remove(e.getPlayer().getUniqueId());
        if (gui != null) gui.onClose((Player) e.getPlayer());
    }
}
