package com.neobuilder.magic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 詠唱セッション. 目標ルーンシーケンスと現在の入力位置を保持する.
 */
public final class ChantSession {

    private final UUID playerId;
    private final String spell;
    private final long expireTick;
    private final List<Integer> sequence = new ArrayList<>();
    private int cursor = 0;
    private int mistakes = 0;

    public ChantSession(UUID playerId, String spell, long expireTick) {
        this.playerId = playerId;
        this.spell = spell;
        this.expireTick = expireTick;
        // スペルごとに固定の詠唱パターンを持たせる（4音のシーケンス）
        int seed = spell.hashCode();
        var rng = new java.util.Random(seed);
        for (int i = 0; i < 4; i++) sequence.add(rng.nextInt(4));
    }

    public UUID playerId() { return playerId; }
    public String spell() { return spell; }
    public long expireTick() { return expireTick; }
    public List<Integer> sequence() { return sequence; }
    public int cursor() { return cursor; }
    public int mistakes() { return mistakes; }
    public boolean complete() { return cursor >= sequence.size(); }

    /** 入力: 正しければ true */
    public boolean input(int rune) {
        if (rune == sequence.get(cursor)) {
            cursor++;
            return true;
        }
        mistakes++;
        cursor = 0; // 間違えたら最初から
        return false;
    }

    /** 進行度をアクションバー用文字列で返す */
    public String progressBar() {
        StringBuilder sb = new StringBuilder("◆".repeat(cursor));
        sb.append("◇".repeat(sequence.size() - cursor));
        return sb.toString();
    }
}
