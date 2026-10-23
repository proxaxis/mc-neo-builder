package com.neobuilder.rpg;

import com.neobuilder.NeoBuilderPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPG職業・ステータス・スキル管理.
 * - パッシブ: ダメージイベントにて職業特性を適用
 * - アクティブスキル: /skill <id> または各種トリガで発動、クールダウン管理
 * - MP: Mage向けリソース、時間経過で回復
 * - 戦士スキルツリー: スキルポイント割り振り
 */
public final class RpgManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    // バフ/デバフ状態（一時的なスキル効果）
    private final Map<UUID, Map<String, Long>> buffs = new ConcurrentHashMap<>();
    private final Map<UUID, Double> pendingCrit = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    // 戦士スキルツリー定義: id -> {maxLv, 表示名}
    private static final Map<String, int[]> WARRIOR_TREE = Map.of(
            "heavyslash", new int[]{5},
            "armbreaker", new int[]{3},
            "execution", new int[]{3},
            "bloodlust", new int[]{3},
            "unchained", new int[]{2},
            "battleharden", new int[]{5});

    private static final Set<String> WARRIOR_ACTIVES = Set.of("leapassault", "berserkmode");

    public RpgManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Map<String, Object> stored = plugin.dataStore().loadMap("rpg");
        stored.forEach((k, v) -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) v;
                data.put(UUID.fromString(k), PlayerData.deserialize(m));
            } catch (Exception ignored) {}
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // MP 自動回復 (2秒ごと)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                PlayerData d = data(p.getUniqueId());
                if (d.clazz() == PlayerClass.MAGE) {
                    d.regenMp(2.0);
                    p.sendActionBar(Component.text(
                            String.format("MP %.0f/%.0f", d.mp(), d.maxMp()),
                            NamedTextColor.DARK_AQUA));
                } else {
                    d.regenMp(0.5);
                }
                applyPassiveAttributes(p, d);
            }
        }, 40L, 40L);
        // 定期保存
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) { dirty = false; save(); }
        }, 2400L, 2400L);
        // Sanctuary等のエリア効果
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAreas, 10L, 10L);
        // 戦士: バトルハードンのスタック減衰管理
        Bukkit.getScheduler().runTaskTimer(plugin, this::decayBuffs, 20L, 20L);
    }

    public void disable() { save(); }

    private void save() {
        Map<String, Object> out = new ConcurrentHashMap<>();
        data.forEach((k, v) -> out.put(k.toString(), v.serialize()));
        plugin.dataStore().saveAsync("rpg", out);
    }

    public PlayerData data(UUID id) { return data.computeIfAbsent(id, k -> new PlayerData()); }

    public void openClassGui(Player p) { plugin.gui().open(p, new ClassSelectGui(plugin, this)); }

    public boolean selectClass(Player p, PlayerClass c) {
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() != PlayerClass.NONE) {
            p.sendMessage(Component.text("既に「" + d.clazz().display() + "」に就いています。リセットは有償です", NamedTextColor.YELLOW));
            return false;
        }
        d.clazz(c);
        dirty = true;
        p.sendMessage(Component.text("職業「" + c.display() + "」に就きました！", NamedTextColor.GREEN));
        return true;
    }

    public boolean resetClass(Player p, double cost) {
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() == PlayerClass.NONE) return false;
        if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
            p.sendMessage(Component.text("DLが不足しています", NamedTextColor.RED));
            return false;
        }
        d.clazz(PlayerClass.NONE);
        d.skillLevels().clear();
        dirty = true;
        return true;
    }

    // --- パッシブ属性（移動速度など） ---

    private void applyPassiveAttributes(Player p, PlayerData d) {
        var attr = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;
        double base = 0.1;
        double speed = switch (d.clazz()) {
            case ASSASSIN -> base * 1.30;
            case GUARDIAN -> base * 0.92;
            default -> base;
        };
        if (Math.abs(attr.getBaseValue() - speed) > 0.0001) {
            attr.setBaseValue(speed);
        }
    }

    // --- ダメージイベント: パッシブ適用 ---

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker)) return;
        PlayerData d = data(attacker.getUniqueId());
        double dmg = e.getDamage();

        switch (d.clazz()) {
            case WARRIOR -> {
                // 近接 +20%
                if (isMelee(e)) dmg *= 1.20;
                // スキルツリー
                int arm = d.skillLevel("armbreaker");
                if (arm > 0 && e.getEntity() instanceof LivingEntity le && hasArmor(le)) {
                    dmg *= 1 + (0.15 + 0.10 * (arm - 1));
                }
                int exec = d.skillLevel("execution");
                if (exec > 0 && e.getEntity() instanceof LivingEntity le
                        && le.getHealth() < le.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.3) {
                    dmg *= 1 + (0.50 + 0.25 * (exec - 1));
                }
                int hs = d.skillLevel("heavyslash");
                if (hs > 0 && isMelee(e) && Math.random() < 0.15 + 0.05 * hs) {
                    splashDamage(attacker, e.getEntity(), dmg * (0.30 + 0.075 * hs), 3);
                }
            }
            case ASSASSIN -> {
                // 背後からの攻撃 2.5倍（簡易: 視線方向の内積が負）
                if (e.getEntity() instanceof LivingEntity le) {
                    Vector facing = le.getLocation().getDirection();
                    Vector toAttacker = attacker.getLocation().toVector()
                            .subtract(le.getLocation().toVector()).normalize();
                    if (facing.dot(toAttacker) < -0.3) dmg *= 2.5;
                }
                // シャドウステップ確定クリティカル
                Double pc = pendingCrit.remove(attacker.getUniqueId());
                if (pc != null) dmg *= pc;
            }
            case MAGE -> { /* 武器攻撃は通常 */ }
            case ARCHER -> { /* 弓ダメージは射撃イベントで距離補正 */ }
            default -> {}
        }
        e.setDamage(dmg);
    }

    /** 被ダメージ: Guardian軽減、Mage属性耐性、バフ反映 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        PlayerData d = data(victim.getUniqueId());
        double dmg = e.getDamage();
        switch (d.clazz()) {
            case GUARDIAN -> {
                dmg *= 0.75;
                // フォートレス: 極限軽減
                if (hasBuff(victim.getUniqueId(), "fortress")) dmg *= 0.15;
            }
            case MAGE -> {
                if (e.getCause() == EntityDamageEvent.DamageCause.MAGIC
                        || e.getCause() == EntityDamageEvent.DamageCause.POISON
                        || e.getCause() == EntityDamageEvent.DamageCause.DRAGON_BREATH) {
                    dmg *= 0.80;
                }
            }
            case WARRIOR -> {
                int bh = d.skillLevel("battleharden");
                if (bh > 0) {
                    int stacks = buffStacks(victim.getUniqueId(), "battleharden");
                    double reduce = Math.min(0.04 * bh * stacks, 0.20);
                    dmg *= (1 - reduce);
                    addBuffStack(victim.getUniqueId(), "battleharden", 60L); // 3s
                }
                if (hasBuff(victim.getUniqueId(), "berserkmode")) dmg *= 1.20;
            }
            case ASSASSIN -> {
                // スモーク中は被弾を下げる
                if (hasBuff(victim.getUniqueId(), "stealth")) dmg *= 0.5;
            }
            default -> {}
        }
        e.setDamage(dmg);
    }

    /** 弓制限: Archer以外は射撃不可 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() != PlayerClass.ARCHER) {
            e.setCancelled(true);
            p.sendMessage(Component.text("弓はArcherのみが使用できます", NamedTextColor.RED));
            return;
        }
        // 矢消費50%軽減
        if (Math.random() < 0.5) {
            e.setConsumeItem(false);
        }
        // ピアシングショット中なら貫通フラグ
        if (hasBuff(p.getUniqueId(), "piercing")) {
            if (e.getProjectile() instanceof Arrow a) {
                a.setPierceLevel(4);
                a.setDamage(a.getDamage() * 1.5);
            }
            removeBuff(p.getUniqueId(), "piercing");
        }
    }

    /** 弓ダメージ: 距離補正 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow a)) return;
        if (!(a.getShooter() instanceof Player p)) return;
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() != PlayerClass.ARCHER) return;
        double dist = p.getLocation().distance(e.getEntity().getLocation());
        e.setDamage(e.getDamage() * (1 + Math.min(dist / 30.0, 0.8)));
        addXp(p, 2.0);
    }

    /** 撃破: XP + Warrior ブラッドラスト */
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        PlayerData d = data(killer.getUniqueId());
        double xp = e.getEntity() instanceof Monster ? 10.0 : 3.0;
        addXp(killer, xp);
        int bl = d.skillLevel("bloodlust");
        if (d.clazz() == PlayerClass.WARRIOR && bl > 0) {
            double heal = 1.0 * bl; // ハート0.5 x lv
            var attr = killer.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                killer.setHealth(Math.min(attr.getValue(), killer.getHealth() + heal));
                killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0));
            }
        }
    }

    private void addXp(Player p, double amount) {
        PlayerData d = data(p.getUniqueId());
        int before = d.level();
        d.addXp(amount);
        dirty = true;
        if (d.level() > before) {
            p.sendMessage(Component.text("レベルアップ！ Lv." + d.level()
                    + " (SP+" + d.skillPoints() + ")", NamedTextColor.GOLD));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private boolean isMelee(EntityDamageByEntityEvent e) {
        return e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
    }

    private boolean hasArmor(LivingEntity le) {
        var eq = le.getEquipment();
        if (eq == null) return false;
        for (var i : eq.getArmorContents()) if (i != null && !i.getType().isAir()) return true;
        return false;
    }

    private void splashDamage(Player src, Entity center, double dmg, double radius) {
        for (var ent : center.getNearbyEntities(radius, radius, radius)) {
            if (ent instanceof LivingEntity le && ent != src && ent != center) {
                le.damage(dmg, src);
            }
        }
    }

    // --- バフ管理 ---
    public void addBuff(UUID id, String buff, long ticks) {
        buffs.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .put(buff, System.currentTimeMillis() + ticks * 50L);
    }
    public boolean hasBuff(UUID id, String buff) {
        Map<String, Long> m = buffs.get(id);
        if (m == null) return false;
        Long until = m.get(buff);
        return until != null && until > System.currentTimeMillis();
    }
    public void removeBuff(UUID id, String buff) {
        Map<String, Long> m = buffs.get(id);
        if (m != null) m.remove(buff);
    }
    private int buffStacks(UUID id, String buff) {
        // 簡易実装: バフ残り時間をスタック数に変換
        Map<String, Long> m = buffs.get(id);
        if (m == null) return 0;
        Long until = m.get(buff);
        if (until == null || until <= System.currentTimeMillis()) return 0;
        return (int) Math.min(5, (until - System.currentTimeMillis()) / 600 + 1);
    }
    private void addBuffStack(UUID id, String buff, long ticks) {
        Map<String, Long> m = buffs.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        Long cur = m.get(buff);
        long now = System.currentTimeMillis();
        // スタック: 有効期限内なら延長
        m.put(buff, (cur != null && cur > now ? cur : now) + ticks * 50L);
    }
    private void decayBuffs() {
        long now = System.currentTimeMillis();
        buffs.values().forEach(m -> m.entrySet().removeIf(e -> e.getValue() <= now));
    }

    // --- エリア効果（サンクチュアリ等） ---
    private final Map<Location, AreaEffect> areas = new ConcurrentHashMap<>();
    private record AreaEffect(String type, UUID owner, long untilTick) {}

    public void addAreaEffect(Location loc, String type, UUID owner, long durationTicks) {
        areas.put(loc.getBlock().getLocation(), new AreaEffect(type, owner,
                Bukkit.getCurrentTick() + durationTicks));
    }

    private void tickAreas() {
        long now = Bukkit.getCurrentTick();
        var it = areas.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            AreaEffect a = e.getValue();
            if (a.untilTick() < now) { it.remove(); continue; }
            if ("sanctuary".equals(a.type())) {
                Location l = e.getKey().clone().add(0.5, 0.5, 0.5);
                for (var ent : l.getWorld().getNearbyEntities(l, 5, 3, 5)) {
                    if (ent instanceof Player p) {
                        var attr = p.getAttribute(Attribute.MAX_HEALTH);
                        if (attr != null && p.getHealth() < attr.getValue()) {
                            p.setHealth(Math.min(attr.getValue(), p.getHealth() + 1.0));
                        }
                    } else if (ent instanceof LivingEntity le && isUndead(le)) {
                        le.damage(2.0);
                    }
                }
                l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 8, 3, 1, 3);
            }
        }
    }

    private boolean isUndead(LivingEntity le) {
        return switch (le.getType()) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, SKELETON, STRAY, WITHER_SKELETON,
                 ZOMBIFIED_PIGLIN, PHANTOM, WITHER, ZOGLIN -> true;
            default -> false;
        };
    }

    // --- スキル ---

    public void listSkills(Player p) {
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() == PlayerClass.NONE) { p.sendMessage("まず /job で職業を選択してください"); return; }
        p.sendMessage(Component.text("--- " + d.clazz().display() + " スキル ---", NamedTextColor.GOLD));
        for (String s : skillsOf(d.clazz())) {
            int lv = d.skillLevel(s);
            p.sendMessage("  " + s + (lv > 0 ? " Lv." + lv : ""));
        }
        if (d.clazz() == PlayerClass.WARRIOR) {
            p.sendMessage("戦士スキルツリー: /skill learn <id> でSP割り振り (SP=" + d.skillPoints() + ")");
            p.sendMessage("  tree: " + String.join(", ", WARRIOR_TREE.keySet()));
        }
    }

    private List<String> skillsOf(PlayerClass c) {
        return switch (c) {
            case WARRIOR -> List.of("warcry", "cleaverush", "leapassault", "berserkmode");
            case ASSASSIN -> List.of("shadowstep", "smokescreen");
            case GUARDIAN -> List.of("taunt", "fortress");
            case HEALER -> List.of("sanctuary", "resurrection");
            case MAGE -> List.of("meteorstrike", "frostnova");
            case ARCHER -> List.of("rainofarrows", "piercingshot");
            default -> List.of();
        };
    }

    public void castSkill(Player p, String id) {
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() == PlayerClass.NONE) { p.sendMessage("まず /job で職業を選択してください"); return; }
        if (!skillsOf(d.clazz()).contains(id)) {
            p.sendMessage(Component.text("そのスキルは習得していません: " + id, NamedTextColor.RED));
            return;
        }
        if (d.isCoolingDown(id)) {
            p.sendMessage(Component.text("クールダウン中: " + (d.cooldownRemaining(id) / 1000) + "秒", NamedTextColor.YELLOW));
            return;
        }
        boolean ok = switch (id) {
            // Warrior
            case "warcry" -> skillWarcry(p, d);
            case "cleaverush" -> skillCleaveRush(p, d);
            case "leapassault" -> skillLeapAssault(p, d);
            case "berserkmode" -> skillBerserk(p, d);
            // Assassin
            case "shadowstep" -> skillShadowstep(p, d);
            case "smokescreen" -> skillSmokescreen(p, d);
            // Guardian
            case "taunt" -> skillTaunt(p, d);
            case "fortress" -> skillFortress(p, d);
            // Healer
            case "sanctuary" -> skillSanctuary(p, d);
            case "resurrection" -> skillResurrection(p, d);
            // Mage (MP消費)
            case "meteorstrike" -> skillMeteor(p, d);
            case "frostnova" -> skillFrostNova(p, d);
            // Archer
            case "rainofarrows" -> skillRainOfArrows(p, d);
            case "piercingshot" -> skillPiercingShot(p, d);
            default -> false;
        };
        if (!ok) p.sendMessage(Component.text("スキル発動に失敗しました", NamedTextColor.RED));
    }

    private void cooldown(PlayerData d, String id, long seconds) { d.setCooldown(id, seconds * 1000L); }
    private boolean mp(PlayerData d, double cost) {
        if (!d.consumeMp(cost)) return false;
        dirty = true;
        return true;
    }

    // --- Warrior ---
    private boolean skillWarcry(Player p, PlayerData d) {
        for (var ent : p.getNearbyEntities(10, 5, 10)) {
            if (ent instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 0));
            }
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        cooldown(d, "warcry", 30);
        return true;
    }

    private boolean skillCleaveRush(Player p, PlayerData d) {
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location eye = p.getEyeLocation();
        boolean hit = false;
        for (var ent : p.getNearbyEntities(5, 3, 5)) {
            if (!(ent instanceof LivingEntity le) || ent == p) continue;
            Vector to = le.getLocation().toVector().subtract(eye.toVector()).normalize();
            if (dir.dot(to) > 0.5) { // 前方扇形 ~60度
                le.damage(12.0, p);
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                hit = true;
            }
        }
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, eye.add(dir.multiply(2)), 10, 1, 0.5, 1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        cooldown(d, "cleaverush", 12);
        return true;
    }

    private boolean skillLeapAssault(Player p, PlayerData d) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        p.setVelocity(dir.multiply(1.5).setY(0.8));
        addBuff(p.getUniqueId(), "leaping", 40);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.getWorld().createExplosion(p.getLocation(), 0f, false, false);
            for (var ent : p.getNearbyEntities(4, 2, 4)) {
                if (ent instanceof LivingEntity le && ent != p) {
                    le.damage(8.0, p);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
                }
            }
        }, 15L);
        cooldown(d, "leapassault", 15);
        return true;
    }

    private boolean skillBerserk(Player p, PlayerData d) {
        addBuff(p.getUniqueId(), "berserkmode", 300);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 2));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        cooldown(d, "berserkmode", 90);
        return true;
    }

    // --- Assassin ---
    private boolean skillShadowstep(Player p, PlayerData d) {
        var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(),
                p.getEyeLocation().getDirection(), 20, e -> e != p && e instanceof LivingEntity);
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity le)) {
            p.sendMessage(Component.text("対象が見つかりません", NamedTextColor.RED));
            return false;
        }
        // 対象の背後を計算
        Vector back = le.getLocation().getDirection().setY(0).normalize().multiply(-1.5);
        Location dest = le.getLocation().add(back);
        // 壁衝突チェック: 足元と頭が通過可能か
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            dest = le.getLocation();
        }
        p.teleport(dest);
        pendingCrit.put(p.getUniqueId(), 2.0); // 次攻撃確定クリティカル
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation(), 20);
        cooldown(d, "shadowstep", 20);
        return true;
    }

    private boolean skillSmokescreen(Player p, PlayerData d) {
        addBuff(p.getUniqueId(), "stealth", 100);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
        // 周囲モブのターゲット解除
        for (var ent : p.getNearbyEntities(15, 8, 15)) {
            if (ent instanceof Mob mob && p.equals(mob.getTarget())) mob.setTarget(null);
        }
        p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getLocation(), 50, 2, 1, 2);
        cooldown(d, "smokescreen", 40);
        return true;
    }

    // --- Guardian ---
    private boolean skillTaunt(Player p, PlayerData d) {
        for (var ent : p.getNearbyEntities(15, 8, 15)) {
            if (ent instanceof Mob mob) mob.setTarget(p);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_HURT, 1f, 0.7f);
        cooldown(d, "taunt", 15);
        return true;
    }

    private boolean skillFortress(Player p, PlayerData d) {
        addBuff(p.getUniqueId(), "fortress", 100);
        // 周囲味方へのダメージ肩代わり: 簡易的に防御バフを配る
        for (var ent : p.getNearbyEntities(8, 4, 8)) {
            if (ent instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1));
            }
        }
        cooldown(d, "fortress", 45);
        return true;
    }

    // --- Healer ---
    private boolean skillSanctuary(Player p, PlayerData d) {
        addAreaEffect(p.getLocation(), "sanctuary", p.getUniqueId(), 200);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
        cooldown(d, "sanctuary", 40);
        return true;
    }

    private boolean skillResurrection(Player p, PlayerData d) {
        // 倒れた仲間を探す: 簡易実装では周囲プレイヤーの体力を大回復
        boolean healed = false;
        for (var ent : p.getNearbyEntities(12, 6, 12)) {
            if (ent instanceof Player ally) {
                var attr = ally.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) {
                    ally.setHealth(Math.min(attr.getValue(), ally.getHealth() + 10.0));
                    ally.removePotionEffect(PotionEffectType.WITHER);
                    ally.removePotionEffect(PotionEffectType.POISON);
                    healed = true;
                }
            }
        }
        if (!healed) {
            p.sendMessage(Component.text("周囲に回復対象がいません", NamedTextColor.YELLOW));
            return false;
        }
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation(), 40, 3, 2, 3);
        cooldown(d, "resurrection", 180);
        return true;
    }

    // --- Mage ---
    private boolean skillMeteor(Player p, PlayerData d) {
        if (!mp(d, 40)) { p.sendMessage(Component.text("MP不足", NamedTextColor.RED)); return false; }
        Location target = p.getTargetBlock(40).getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            target.getWorld().createExplosion(target, 3.0f, true, false);
            for (var ent : target.getWorld().getNearbyEntities(target, 8, 6, 8)) {
                if (ent instanceof LivingEntity le && ent != p) le.damage(25.0, p);
            }
        }, 30L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        cooldown(d, "meteorstrike", 45);
        return true;
    }

    private boolean skillFrostNova(Player p, PlayerData d) {
        if (!mp(d, 25)) { p.sendMessage(Component.text("MP不足", NamedTextColor.RED)); return false; }
        for (var ent : p.getNearbyEntities(6, 4, 6)) {
            if (ent instanceof LivingEntity le && ent != p) {
                le.setFreezeTicks(le.getMaxFreezeTicks() + 160);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 3));
                le.damage(6.0, p);
            }
        }
        p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 60, 4, 1, 4);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
        cooldown(d, "frostnova", 30);
        return true;
    }

    // --- Archer ---
    private boolean skillRainOfArrows(Player p, PlayerData d) {
        Location target = p.getTargetBlock(30).getLocation().add(0, 8, 0);
        for (int i = 0; i < 20; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location spawn = target.clone().add(
                        (Math.random() - 0.5) * 6, Math.random() * 2, (Math.random() - 0.5) * 6);
                Arrow arrow = target.getWorld().spawnArrow(spawn, new Vector(0, -1, 0), 2.5f, 0);
                arrow.setShooter(p);
                arrow.setDamage(4.0);
            }, i * 2L);
        }
        cooldown(d, "rainofarrows", 25);
        return true;
    }

    private boolean skillPiercingShot(Player p, PlayerData d) {
        addBuff(p.getUniqueId(), "piercing", 100); // 次の射撃に適用
        p.sendMessage(Component.text("ピアシングショット: 5秒以内に弓を放て", NamedTextColor.AQUA));
        cooldown(d, "piercingshot", 30);
        return true;
    }

    // --- 戦士スキルツリー ---

    public Map<String, int[]> warriorTree() { return WARRIOR_TREE; }

    public boolean learnWarriorSkill(Player p, String id) {
        PlayerData d = data(p.getUniqueId());
        if (d.clazz() != PlayerClass.WARRIOR) return false;
        int[] def = WARRIOR_TREE.get(id);
        if (def == null) return false;
        int cur = d.skillLevel(id);
        if (cur >= def[0]) { p.sendMessage("既に最大Lvです"); return false; }
        if (!d.spendSkillPoint()) { p.sendMessage("SPが不足しています"); return false; }
        d.levelSkill(id);
        dirty = true;
        p.sendMessage(Component.text(id + " Lv." + d.skillLevel(id) + " を習得", NamedTextColor.GREEN));
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = data(e.getPlayer().getUniqueId());
        if (d.clazz() == PlayerClass.NONE) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 陣営未選択なら技術選択GUIが優先（そちらで誘導）
                if (!e.getPlayer().isOnline()) return;
                if (plugin.allegiance().get(e.getPlayer().getUniqueId())
                        != com.neobuilder.tech.AllegianceManager.Allegiance.NONE) {
                    openClassGui(e.getPlayer());
                }
            }, 100L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        buffs.remove(e.getPlayer().getUniqueId());
        pendingCrit.remove(e.getPlayer().getUniqueId());
    }
}
