package com.neobuilder.pipe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * パイプブロックのデータ.
 * type はアイテムID（pipe_transport 等）と同一.
 */
public final class Pipe {

    private final String type;
    private final String key;      // "world:x,y,z"
    private int frequency;         // テレポートパイプ用
    private int cooldown;
    private String lastDest;       // 流体パイプ: 直近の送液先（抽出元として再採用しない）

    public Pipe(String type, String key) {
        this.type = type;
        this.key = key;
    }

    public String type() { return type; }
    public String key() { return key; }
    public int frequency() { return frequency; }
    public void frequency(int f) { frequency = f; }
    public int cooldown() { return cooldown; }
    public void cooldown(int c) { cooldown = c; }
    public String lastDest() { return lastDest; }
    public void lastDest(String k) { lastDest = k; }

    /** 輸送系パイプ（アイテム経路として使える）か */
    public boolean isTransport() {
        return switch (type) {
            case "pipe_transport", "pipe_extract", "pipe_gold", "pipe_diamond",
                 "pipe_obsidian", "pipe_teleport", "pipe_logistics",
                 "pipe_provider", "pipe_request", "pipe_stone", "pipe_sandstone",
                 "pipe_iron", "pipe_emerald", "pipe_distribution",
                 "pipe_supplier", "pipe_crafting", "pipe_satellite",
                 "pipe_void" -> true;
            default -> false;
        };
    }

    /** エネルギー伝送系（キネシス+IC2ケーブル/変圧器）か */
    public boolean isKinesis() {
        return "pipe_kinesis".equals(type) || "pipe_teleport_structure".equals(type)
                || type.startsWith("cable_") || type.startsWith("transformer_");
    }

    /** 流体輸送系か */
    public boolean isFluid() {
        return type.equals("pipe_fluid") || type.startsWith("pipe_fluid_")
                || "pipe_teleport_fluid".equals(type);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("key", key);
        m.put("freq", frequency);
        return m;
    }

    public static Pipe deserialize(Map<String, Object> m) {
        Pipe p = new Pipe(String.valueOf(m.get("type")), String.valueOf(m.get("key")));
        if (m.get("freq") instanceof Number n) p.frequency(n.intValue());
        return p;
    }
}
