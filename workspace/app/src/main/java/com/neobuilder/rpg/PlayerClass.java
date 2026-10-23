package com.neobuilder.rpg;

import org.bukkit.Material;

/**
 * 職業定義.
 */
public enum PlayerClass {
    NONE("未就職", Material.BARRIER),
    WARRIOR("Warrior", Material.IRON_SWORD),
    ASSASSIN("Assassin", Material.IRON_AXE),
    GUARDIAN("Guardian", Material.SHIELD),
    HEALER("Healer", Material.GOLDEN_APPLE),
    MAGE("Mage", Material.BOOK),
    ARCHER("Archer", Material.BOW);

    private final String display;
    private final Material icon;

    PlayerClass(String display, Material icon) {
        this.display = display;
        this.icon = icon;
    }

    public String display() { return display; }
    public Material icon() { return icon; }

    public static PlayerClass of(String s) {
        if (s == null) return NONE;
        try { return valueOf(s.toUpperCase()); } catch (Exception e) { return NONE; }
    }
}
