package com.neobuilder.magic;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.gui.NbGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * 詠唱パズルGUI.
 * 4種のルーンを正しい順序でクリックすると呪文が発動する.
 * 表示: 上半分に目標シーケンス、下半分に入力ボタン.
 */
public final class ChantGui extends NbGui {

    private static final List<Rune> RUNES = List.of(
            new Rune("炎", Material.BLAZE_POWDER, NamedTextColor.RED),
            new Rune("氷", Material.ICE, NamedTextColor.AQUA),
            new Rune("雷", Material.GLOWSTONE_DUST, NamedTextColor.YELLOW),
            new Rune("地", Material.DIRT, NamedTextColor.GREEN));

    private record Rune(String name, Material mat, NamedTextColor color) {}

    private static final int[] INPUT_SLOTS = {29, 31, 33, 39}; // 下段の4ボタン

    private final NeoBuilderPlugin plugin;
    private final ChantManager manager;
    private final ChantSession session;

    public ChantGui(NeoBuilderPlugin plugin, ChantManager manager, ChantSession session) {
        this.plugin = plugin;
        this.manager = manager;
        this.session = session;
    }

    @Override protected Component title() {
        return Component.text("詠唱: " + session.spell(), NamedTextColor.DARK_PURPLE);
    }

    @Override protected int rows() { return 5; }

    @Override protected void decorate(Player p) {
        inventory.clear();
        // 目標シーケンスを上段に表示（現在位置をハイライト）
        for (int i = 0; i < session.sequence().size(); i++) {
            Rune r = RUNES.get(session.sequence().get(i));
            ItemStack s = runeItem(r, i < session.cursor() ? "済" : (i == session.cursor() ? "▶" : " "));
            set(11 + i * 2, s);
        }
        // 入力ボタン
        for (int i = 0; i < RUNES.size(); i++) {
            set(INPUT_SLOTS[i], runeItem(RUNES.get(i), "クリック"));
        }
        // 説明
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta m = info.getItemMeta();
        m.displayName(Component.text("ルーンを順番どおりに唱えよ", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("間違えると最初からやり直し", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(m);
        set(4, info);
    }

    private ItemStack runeItem(Rune r, String label) {
        ItemStack s = new ItemStack(r.mat());
        ItemMeta m = s.getItemMeta();
        m.displayName(Component.text(r.name() + "のルーン " + label, r.color())
                .decoration(TextDecoration.ITALIC, false));
        s.setItemMeta(m);
        return s;
    }

    @Override protected void onClick(Player p, int slot, ClickType click) {
        int rune = -1;
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            if (INPUT_SLOTS[i] == slot) rune = i;
        }
        if (rune < 0) return;
        boolean ok = session.input(rune);
        if (!ok) {
            p.sendActionBar(Component.text("詠唱ミス！ 最初から " + session.progressBar(), NamedTextColor.RED));
        } else {
            p.sendActionBar(Component.text("詠唱 " + session.progressBar(), NamedTextColor.LIGHT_PURPLE));
        }
        if (session.complete()) {
            manager.end(p.getUniqueId());
            plugin.gui().close(p);
            p.sendMessage(Component.text("詠唱完了！", NamedTextColor.GOLD));
            // スキル発動（RPGシステム経由でMP・CDを管理）
            plugin.rpg().castSkill(p, session.spell());
            return;
        }
        decorate(p);
    }

    /** GUIを閉じたら詠唱中断 */
    @Override protected void onClose(Player p) {
        manager.end(p.getUniqueId());
    }
}
