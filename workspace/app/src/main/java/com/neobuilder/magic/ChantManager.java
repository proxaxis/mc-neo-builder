package com.neobuilder.magic;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.rpg.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 詠唱（チャント）パズルシステム.
 * 呪文書を右クリックで詠唱セッション開始 → GUIでルーンシーケンスを入力.
 * 成功すれば対応スキルを発動. アクションバーでリアルタイムフィードバック.
 */
public final class ChantManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, ChantSession> sessions = new ConcurrentHashMap<>();

    public ChantManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // セッションタイムアウト管理
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = Bukkit.getCurrentTick();
            sessions.values().removeIf(s -> {
                if (s.expireTick() < now) {
                    Player p = Bukkit.getPlayer(s.playerId());
                    if (p != null) {
                        p.sendActionBar(Component.text("詠唱が途切れた…", NamedTextColor.GRAY));
                        plugin.gui().close(p);
                    }
                    return true;
                }
                return false;
            });
        }, 20L, 20L);
    }

    public void disable() { sessions.clear(); }

    public ChantSession session(UUID id) { return sessions.get(id); }

    public void end(UUID id) { sessions.remove(id); }

    /** 呪文書使用で詠唱開始 */
    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var item = e.getItem();
        if (item == null) return;
        String id = plugin.items().itemId(item);
        if (id == null) return;
        String spell = switch (id) {
            case "grimoire_meteor" -> "meteorstrike";
            case "grimoire_frost" -> "frostnova";
            case "spellbook" -> "frostnova"; // 汎用書は基本呪文
            default -> null;
        };
        if (spell == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        // Mage専用
        if (plugin.rpg().data(p.getUniqueId()).clazz() != PlayerClass.MAGE) {
            p.sendMessage(Component.text("呪文書はMageのみが使用できます", NamedTextColor.RED));
            return;
        }
        if (sessions.containsKey(p.getUniqueId())) return;
        ChantSession session = new ChantSession(p.getUniqueId(), spell,
                Bukkit.getCurrentTick() + plugin.getConfig().getInt("chant.session-timeout-ticks", 200));
        sessions.put(p.getUniqueId(), session);
        plugin.gui().open(p, new ChantGui(plugin, this, session));
    }
}
