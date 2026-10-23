package com.neobuilder.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * インベントリGUIの基底.
 * スロット配置は build() で行い、クリックは onClick() で処理する.
 */
public abstract class NbGui {

    protected Inventory inventory;

    protected abstract Component title();
    protected abstract int rows();
    protected abstract void decorate(Player p);
    protected abstract void onClick(Player p, int slot, ClickType click);

    /** 閉じた時の処理（必要なら override） */
    protected void onClose(Player p) {}

    public Inventory buildInventory(Player p) {
        this.inventory = Bukkit.createInventory(null, rows() * 9, title());
        decorate(p);
        return inventory;
    }

    public Inventory inventory() { return inventory; }

    protected void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }
}
