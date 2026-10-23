package com.neobuilder.tech;

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

/**
 * 技術陣営選択GUI.
 * 科学技術か魔法技術かを一度だけ選択する（排他）.
 */
public final class TechSelectGui extends NbGui {

    private static final int SLOT_SCIENCE = 11;
    private static final int SLOT_MAGIC = 15;

    private final NeoBuilderPlugin plugin;
    private final AllegianceManager manager;

    public TechSelectGui(NeoBuilderPlugin plugin, AllegianceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override protected Component title() {
        return Component.text("技術の選択", NamedTextColor.DARK_AQUA);
    }

    @Override protected int rows() { return 3; }

    @Override protected void decorate(Player p) {
        set(SLOT_SCIENCE, button(Material.PISTON, "科学技術", NamedTextColor.RED,
                "エンジン・パイプ・マシンによる自動化", "産業で世界を変えろ"));
        set(SLOT_MAGIC, button(Material.ENCHANTED_BOOK, "魔法技術", NamedTextColor.LIGHT_PURPLE,
                "詠唱と呪文による超常の力", "魔力で世界を変えろ"));
        set(13, button(Material.BARRIER, "決定は一度きり！", NamedTextColor.YELLOW,
                "リセットにはDLが必要です"));
    }

    private ItemStack button(Material m, String name, NamedTextColor color, String... lore) {
        ItemStack s = new ItemStack(m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(lore).stream()
                .map(t -> Component.text(t, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList());
        s.setItemMeta(meta);
        return s;
    }

    @Override protected void onClick(Player p, int slot, ClickType click) {
        AllegianceManager.Allegiance a = switch (slot) {
            case SLOT_SCIENCE -> AllegianceManager.Allegiance.SCIENCE;
            case SLOT_MAGIC -> AllegianceManager.Allegiance.MAGIC;
            default -> null;
        };
        if (a == null) return;
        if (manager.select(p, a)) {
            p.sendMessage(Component.text("「" + AllegianceManager.label(a) + "」を選択しました！",
                    NamedTextColor.GREEN));
            plugin.gui().close(p);
        }
    }
}
