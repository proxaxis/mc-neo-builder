package com.neobuilder.pipe;

import com.neobuilder.NeoBuilderPlugin;
import com.neobuilder.core.util.Blocks;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;

import java.util.*;

/**
 * 流体パイプ網による液体輸送.
 * 大釜（cauldron）を流体コンテナとして扱い、水・溶岩を輸送する.
 * 1 level = 333 mB. 輸送は tick バジェット内で BFS 経路探索して行う.
 */
public final class FluidManager {

    public enum Fluid { WATER, LAVA, NONE;
        public static Fluid of(Material m) {
            return switch (m) {
                case WATER_CAULDRON -> WATER;
                case LAVA_CAULDRON -> LAVA;
                default -> NONE;
            };
        }
        public Material cauldron() {
            return this == WATER ? Material.WATER_CAULDRON : Material.LAVA_CAULDRON;
        }
    }

    private static final int MAX_BFS = 256;

    private final NeoBuilderPlugin plugin;
    private final PipeManager pipes;

    public FluidManager(NeoBuilderPlugin plugin, PipeManager pipes) {
        this.plugin = plugin;
        this.pipes = pipes;
    }

    /** 流体パイプの1tick処理: 隣接コンテナから1レベル分を引き、網の先のコンテナへ届ける */
    public void processPipe(Pipe pipe) {
        Block b = blockOf(pipe.key());
        if (b == null) return;
        // 抽出: 隣接する流体コンテナから333mB引く
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block src = b.getRelative(f);
            // 直近の送液先は抽出元にしない（流体の往復振動を防止）
            if (Blocks.key(src).equals(pipe.lastDest()) && Fluid.of(src.getType()) != Fluid.NONE) continue;
            Fluid fluid = extractOne(src);
            if (fluid == Fluid.NONE) continue;
            // 経路探索して最寄りの適合コンテナへ（抽出元は挿入先から除外）
            String dest = route(pipe, fluid, Blocks.key(src));
            if (dest == null) {
                // 届け先なし → 引いた分を戻す
                insert(src, fluid);
                continue;
            }
            pipe.lastDest(dest);
            return; // 1tick 1輸送
        }
    }

    /** コンテナから1レベル分の流体を取り出す（空ならNONE） */
    private Fluid extractOne(Block b) {
        Material t = b.getType();
        Fluid f = Fluid.of(t);
        if (f == Fluid.NONE) return Fluid.NONE;
        if (b.getBlockData() instanceof Levelled lv) {
            if (lv.getLevel() > 1) {
                lv.setLevel(lv.getLevel() - 1);
                b.setBlockData(lv);
            } else {
                b.setType(Material.CAULDRON);
            }
            return f;
        }
        return Fluid.NONE;
    }

    /** コンテナへ1レベル分挿入。入らなければ false */
    private boolean insert(Block b, Fluid f) {
        Material t = b.getType();
        if (t == Material.CAULDRON) {
            b.setType(f.cauldron());
            return true;
        }
        if (Fluid.of(t) == f && b.getBlockData() instanceof Levelled lv) {
            if (lv.getLevel() < lv.getMaximumLevel()) {
                lv.setLevel(lv.getLevel() + 1);
                b.setBlockData(lv);
                return true;
            }
        }
        return false;
    }

    /** BFSで流体パイプ網を辿り、最初に見つかった適合コンテナへ輸送。成功時は送液先キーを返す */
    private String route(Pipe from, Fluid fluid, String exclude) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        String start = from.key();
        visited.add(start);
        queue.add(start);
        enqueue(start, visited, queue);
        while (!queue.isEmpty() && visited.size() < MAX_BFS) {
            String cur = queue.poll();
            Block cb = blockOf(cur);
            if (cb == null) continue;
            // 隣接コンテナへ挿入試行
            for (BlockFace f : BlockFace.values()) {
                if (!f.isCartesian()) continue;
                Block adj = cb.getRelative(f);
                if (isFluidPipe(adj)) continue;
                if (Blocks.key(adj).equals(exclude)) continue;
                Material mt = adj.getType();
                if (mt == Material.CAULDRON || Fluid.of(mt) != Fluid.NONE) {
                    if (insert(adj, fluid)) return Blocks.key(adj);
                }
            }
            enqueue(cur, visited, queue);
        }
        return null;
    }

    private void enqueue(String key, Set<String> visited, Deque<String> queue) {
        Block b = blockOf(key);
        if (b == null) return;
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = b.getRelative(f);
            String k = Blocks.key(adj);
            if (!visited.contains(k) && isFluidPipe(adj)) {
                visited.add(k);
                queue.add(k);
            }
        }
    }

    private boolean isFluidPipe(Block b) {
        Pipe p = pipes.at(b);
        return p != null && p.isFluid();
    }

    /** 指定ブロックの隣接大釜から溶岩を1レベル消費（燃焼エンジンの燃料用） */
    public boolean drainLavaAdjacent(Block machine) {
        for (BlockFace f : BlockFace.values()) {
            if (!f.isCartesian()) continue;
            Block adj = machine.getRelative(f);
            if (adj.getType() == Material.LAVA_CAULDRON) {
                extractOne(adj);
                return true;
            }
        }
        return false;
    }

    private Block blockOf(String key) {
        String[] p = Blocks.parse(key);
        World w = plugin.getServer().getWorld(p[0]);
        if (w == null) return null;
        return Blocks.getIfLoaded(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }
}
