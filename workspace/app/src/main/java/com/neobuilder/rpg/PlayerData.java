package com.neobuilder.rpg;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPGプレイヤーデータ. 職業・レベル・MP・スキル習熟度を保持.
 */
public final class PlayerData {

    private PlayerClass clazz = PlayerClass.NONE;
    private int level = 1;
    private double xp = 0;
    private int skillPoints = 0;
    private double mp = 100;
    private double maxMp = 100;
    private final Map<String, Integer> skillLevels = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public PlayerClass clazz() { return clazz; }
    public void clazz(PlayerClass c) { this.clazz = c; }
    public int level() { return level; }
    public double xp() { return xp; }
    public int skillPoints() { return skillPoints; }
    public double mp() { return mp; }
    public double maxMp() { return maxMp; }
    public int skillLevel(String id) { return skillLevels.getOrDefault(id, 0); }
    public Map<String, Integer> skillLevels() { return skillLevels; }

    public void addXp(double amount) {
        xp += amount;
        while (xp >= xpToNext()) {
            xp -= xpToNext();
            level++;
            skillPoints += 2;
            maxMp += 5;
            mp = maxMp;
        }
    }

    public double xpToNext() { return 100.0 * level; }

    public boolean spendSkillPoint() {
        if (skillPoints <= 0) return false;
        skillPoints--;
        return true;
    }

    public boolean consumeMp(double cost) {
        if (mp < cost) return false;
        mp -= cost;
        return true;
    }

    public void regenMp(double amount) { mp = Math.min(maxMp, mp + amount); }

    public void levelSkill(String id) { skillLevels.merge(id, 1, Integer::sum); }

    // --- クールダウン ---
    public boolean isCoolingDown(String skillId) {
        Long until = cooldowns.get(skillId);
        return until != null && until > System.currentTimeMillis();
    }

    public long cooldownRemaining(String skillId) {
        Long until = cooldowns.get(skillId);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void setCooldown(String skillId, long ms) {
        cooldowns.put(skillId, System.currentTimeMillis() + ms);
    }

    // --- 永続化 ---
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("class", clazz.name());
        m.put("level", level);
        m.put("xp", xp);
        m.put("sp", skillPoints);
        m.put("mp", mp);
        m.put("maxMp", maxMp);
        m.put("skills", new LinkedHashMap<>(skillLevels));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static PlayerData deserialize(Map<String, Object> m) {
        PlayerData d = new PlayerData();
        d.clazz = PlayerClass.of(String.valueOf(m.get("class")));
        if (m.get("level") instanceof Number n) d.level = n.intValue();
        if (m.get("xp") instanceof Number n) d.xp = n.doubleValue();
        if (m.get("sp") instanceof Number n) d.skillPoints = n.intValue();
        if (m.get("mp") instanceof Number n) d.mp = n.doubleValue();
        if (m.get("maxMp") instanceof Number n) d.maxMp = n.doubleValue();
        if (m.get("skills") instanceof Map<?, ?> sm) {
            sm.forEach((k, v) -> {
                if (v instanceof Number n) d.skillLevels.put(String.valueOf(k), n.intValue());
            });
        }
        return d;
    }
}
