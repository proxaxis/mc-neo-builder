package com.neobuilder.core.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * ブロック位置キーのユーティリティ.
 * キー形式: "world:x,y,z"
 */
public final class Blocks {

    private Blocks() {}

    /** Location → 一意キー */
    public static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public static String key(Block b) {
        return key(b.getLocation());
    }

    /** "world:x,y,z" → {worldName, x, y, z} */
    public static String[] parse(String key) {
        int ci = key.indexOf(':');
        String w = key.substring(0, ci);
        String[] p = key.substring(ci + 1).split(",");
        return new String[]{w, p[0], p[1], p[2]};
    }

    /** チャンクをロードせず、ロード済みならブロック取得 */
    public static Block getIfLoaded(World w, int x, int y, int z) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
        return w.getBlockAt(x, y, z);
    }
}
