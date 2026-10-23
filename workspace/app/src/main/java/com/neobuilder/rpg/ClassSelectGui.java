package com.neobuilder.rpg;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.gui.NbGui;
import com.neobuilder.tech.AllegianceManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * 職業選択GUI. 6職業をアイコン表示しクリックで就職する.
 * 選択は一度きり（リセットは有償）.
 */
public final class ClassSelectGui extends NbGui {

    private static final Map<PlayerClass, Integer> SLOTS = Map.of(
            PlayerClass.WARRIOR, 10,
            PlayerClass.ASSASSIN, 12,
            PlayerClass.GUARDIAN, 14,
            PlayerClass.HEALER, 16,
            PlayerClass.MAGE, 28,
            PlayerClass.ARCHER, 30,
            PlayerClass.SCIENTIST, 32);

    private static final Map<PlayerClass, List<String>> LORE = Map.of(
            PlayerClass.WARRIOR, List.of("近接 +20% / ノックバック耐性", "ウォークライ・クリーブラッシュ"),
            PlayerClass.ASSASSIN, List.of("移動 +30% / 背後攻撃 2.5倍", "シャドウステップ・煙幕"),
            PlayerClass.GUARDIAN, List.of("被ダメ -25% / ノックバック無効", "タウント・フォートレス"),
            PlayerClass.HEALER, List.of("味方回復支援", "サンクチュアリ・リザレクション"),
            PlayerClass.MAGE, List.of("属性耐性 / MP増加", "メテオ・フロストノヴァ"),
            PlayerClass.ARCHER, List.of("唯一の弓使用可能職", "矢の雨・貫通射撃"),
            PlayerClass.SCIENTIST, List.of("工業・研究に特化した技術者", "剣・弓・槍は使用不可（ツールのみ）"));

    private final NeoBuilderPlugin plugin;
    private final RpgManager manager;

    public ClassSelectGui(NeoBuilderPlugin plugin, RpgManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override protected Component title() {
        return Component.text("職業選択", NamedTextColor.DARK_AQUA);
    }

    @Override protected int rows() { return 5; }

    /** 陣営による職業制限: 科学はScientist固定、Scientistは科学専用 */
    private boolean allowed(Player p, PlayerClass c) {
        var a = plugin.allegiance().get(p.getUniqueId());
        if (a == AllegianceManager.Allegiance.SCIENCE) return c == PlayerClass.SCIENTIST;
        return c != PlayerClass.SCIENTIST;
    }

    @Override protected void decorate(Player p) {
        for (var e : SLOTS.entrySet()) {
            PlayerClass c = e.getKey();
            if (!allowed(p, c)) continue;
            ItemStack s = new ItemStack(c.icon());
            ItemMeta meta = s.getItemMeta();
            meta.displayName(Component.text(c.display(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            meta.lore(LORE.get(c).stream()
                    .map(t -> Component.text(t, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
            s.setItemMeta(meta);
            set(e.getValue(), s);
        }
    }

    @Override protected void onClick(Player p, int slot, ClickType click) {
        for (var e : SLOTS.entrySet()) {
            if (e.getValue() == slot) {
                if (manager.selectClass(p, e.getKey())) plugin.gui().close(p);
                return;
            }
        }
    }
}
