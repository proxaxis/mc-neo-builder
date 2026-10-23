package com.neobuilder.pipe;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import com.neobuilder.gui.NbGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * リクエストパイプGUI.
 * ネットワーク内プロバイダーが公開するアイテムを一覧し、クリックで取り寄せる.
 */
public final class RequestGui extends NbGui {

    private final NeoBuilderPlugin plugin;
    private final Pipe requestPipe;
    private final Map<String, List<PipeManager.LogisticsEntry>> index;
    private final List<ItemStack> offers = new ArrayList<>();

    public RequestGui(NeoBuilderPlugin plugin, Pipe requestPipe,
                      Map<String, List<PipeManager.LogisticsEntry>> index) {
        this.plugin = plugin;
        this.requestPipe = requestPipe;
        this.index = index;
    }

    @Override protected Component title() {
        return Component.text("物流リクエスト", NamedTextColor.AQUA);
    }

    @Override protected int rows() { return 6; }

    @Override protected void decorate(Player p) {
        inventory.clear();
        offers.clear();
        for (var list : index.values()) {
            for (var e : list) {
                Block inv = blockOf(e.invKey());
                if (inv == null || !(inv.getState() instanceof InventoryHolder ih)) continue;
                for (ItemStack s : ih.getInventory().getContents()) {
                    if (s == null || s.getType().isAir()) continue;
                    offers.add(s.clone());
                    if (offers.size() >= 54) break;
                }
                if (offers.size() >= 54) break;
            }
            if (offers.size() >= 54) break;
        }
        for (int i = 0; i < Math.min(54, offers.size()); i++) {
            set(i, offers.get(i));
        }
    }

    private Block blockOf(String key) {
        String[] p = Blocks.parse(key);
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return Blocks.getIfLoaded(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    @Override protected void onClick(Player p, int slot, ClickType click) {
        if (slot < 0 || slot >= offers.size()) return;
        ItemStack want = offers.get(slot);
        int amount = click.isShiftClick() ? 64 : click.isRightClick() ? 16 : 1;
        amount = Math.min(amount, want.getAmount());
        if (plugin.pipes().fulfillRequest(requestPipe, want, amount)) {
            p.sendMessage(Component.text("アイテムを取り寄せました", NamedTextColor.GREEN));
            decorate(p); // 在庫更新
        } else {
            p.sendMessage(Component.text("取り寄せに失敗しました（届け先がありません）", NamedTextColor.RED));
        }
    }
}
