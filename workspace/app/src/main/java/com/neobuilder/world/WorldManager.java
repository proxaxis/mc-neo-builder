package com.neobuilder.world;

import com.neobuilder.NeoBuilderPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ワールドイベント管理.
 * - EFP（環境負荷指数）: 工業活動で蓄積、閾値超過で災害を誘発
 * - 災害: 強化モブ襲撃イベント（オーバーワールド）
 * - カタクリズムボス: マルチフェーズのレイドボス（/nb boss で召喚）
 */
public final class WorldManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private volatile double efp = 0;
    private final Set<UUID> bossIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossRig> bossRigs = new ConcurrentHashMap<>();
    private NamespacedKey bossKey;

    public WorldManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.bossKey = plugin.key("boss");
        efp = plugin.dataStore().load("efp", Double.class, 0.0);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // 災害チェック + EFP減衰
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisaster,
                plugin.getConfig().getInt("disaster.check-interval-ticks", 6000),
                plugin.getConfig().getInt("disaster.check-interval-ticks", 6000));
        // EFP減衰 (1分ごと)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double decay = plugin.getConfig().getDouble("efp.decay-per-minute", 5.0);
            efp = Math.max(0, efp - decay);
        }, 1200L, 1200L);
        // EFP定期保存
        Bukkit.getScheduler().runTaskTimer(plugin,
                () -> plugin.dataStore().saveAsync("efp", efp), 2400L, 2400L);
        // ボスAI tick
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBosses, 20L, 20L);
        // ボス演出リグ（2tick毎の滑らかなアニメーション）
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRigs, 4L, 2L);
    }

    public void disable() {
        bossRigs.values().forEach(BossRig::destroy);
        bossRigs.clear();
        plugin.dataStore().saveAsync("efp", efp);
    }

    public double getEfp() { return efp; }
    public void addEfp(double amount) { efp += amount; }

    // --- 災害 ---

    private void tickDisaster() {
        double threshold = plugin.getConfig().getDouble("efp.disaster-threshold", 10000.0);
        double chance = plugin.getConfig().getDouble("disaster.chance", 0.3);
        if (efp < threshold) return;
        if (Math.random() > chance) return;
        triggerDisaster();
    }

    /** 災害発生: オーバーワールドのランダムなプレイヤー周辺に強化モブ襲撃 */
    public void triggerDisaster() {
        List<? extends Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.isLifeWorld(p.getWorld()))
                .toList();
        if (candidates.isEmpty()) return;
        Player target = candidates.get(new Random().nextInt(candidates.size()));
        Location center = target.getLocation();

        Bukkit.broadcast(Component.text(
                "§4§l【災害】§c 環境負荷が限界を超え、災厄が降臨した！", NamedTextColor.DARK_RED));
        target.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
        target.getWorld().strikeLightningEffect(center);

        // 強化モブウェーブ
        EntityType[] wave = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITCH};
        Random r = new Random();
        for (int i = 0; i < wave.length; i++) {
            Location spawn = center.clone().add((r.nextDouble() - 0.5) * 20, 0, (r.nextDouble() - 0.5) * 20);
            spawn.setY(spawn.getWorld().getHighestBlockYAt(spawn) + 1);
            LivingEntity mob = (LivingEntity) target.getWorld().spawnEntity(spawn, wave[i]);
            buffMob(mob);
            if (mob instanceof Mob m) m.setTarget(target);
        }
        efp = 0; // 消費
    }

    private void buffMob(LivingEntity mob) {
        var hp = mob.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(hp.getBaseValue() * 2); mob.setHealth(hp.getValue()); }
        var dmg = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 1.5);
        var spd = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(spd.getBaseValue() * 1.2);
    }

    // --- カタクリズムボス（簡易マルチフェーズ） ---

    /** レイドボスを召喚（異世界想定だがどこでも可） */
    public LivingEntity spawnBoss(Location loc) {
        // ベース: ラヴァジャーを強化
        Ravager boss = (Ravager) loc.getWorld().spawnEntity(loc, EntityType.RAVAGER);
        boss.customName(Component.text("カタクリズム・ガーディアン", NamedTextColor.DARK_RED));
        boss.setCustomNameVisible(true);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        var hp = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(400.0); boss.setHealth(400.0); }
        var dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(15.0);
        bossIds.add(boss.getUniqueId());
        bossRigs.put(boss.getUniqueId(), new BossRig(plugin, boss));
        Bukkit.broadcast(Component.text("§5§lカタクリズム・ボスが現れた！", NamedTextColor.DARK_PURPLE));
        return boss;
    }

    /** アーマースタンド演出リグのtick */
    private void tickRigs() {
        var it = bossRigs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity boss = Bukkit.getEntity(e.getKey());
            if (!(boss instanceof LivingEntity le) || boss == null || !boss.isValid()) {
                e.getValue().destroy();
                it.remove();
                continue;
            }
            var attr = le.getAttribute(Attribute.MAX_HEALTH);
            double ratio = attr != null ? le.getHealth() / attr.getValue() : 1.0;
            e.getValue().tick(le, ratio);
        }
    }

    /** ボスAI: HPに応じてフェーズ移行、範囲攻撃を行う */
    private void tickBosses() {
        var it = bossIds.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof LivingEntity boss) || e == null || !e.isValid()) {
                it.remove();
                continue;
            }
            var attr = boss.getAttribute(Attribute.MAX_HEALTH);
            if (attr == null) continue;
            double ratio = boss.getHealth() / attr.getValue();
            // フェーズ: HP50%以下で雷撃、25%以下で爆発
            if (ratio < 0.5 && Math.random() < 0.3) {
                for (var ent : boss.getNearbyEntities(8, 4, 8)) {
                    if (ent instanceof Player p) {
                        boss.getWorld().strikeLightning(p.getLocation());
                    }
                }
            }
            if (ratio < 0.25 && Math.random() < 0.2) {
                boss.getWorld().createExplosion(boss.getLocation(), 2f, false, false);
                for (var ent : boss.getNearbyEntities(6, 3, 6)) {
                    if (ent instanceof Player p) p.damage(8.0, boss);
                }
            }
            // レーザー風: 前方プレイヤーへダメージ
            if (Math.random() < 0.25) {
                org.bukkit.util.Vector dir = boss.getLocation().getDirection().normalize();
                for (var ent : boss.getNearbyEntities(15, 5, 15)) {
                    if (!(ent instanceof Player p)) continue;
                    org.bukkit.util.Vector to = p.getLocation().toVector()
                            .subtract(boss.getLocation().toVector()).normalize();
                    if (dir.dot(to) > 0.8) {
                        p.damage(6.0, boss);
                        boss.getWorld().spawnParticle(Particle.END_ROD,
                                boss.getEyeLocation(), 30, 0.5, 0.5, 0.5, 0.3);
                    }
                }
            }
        }
    }

    /** ボス撃破: 固有ドロップ */
    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (!bossIds.remove(e.getEntity().getUniqueId())) return;
        var rig = bossRigs.remove(e.getEntity().getUniqueId());
        if (rig != null) rig.destroy();
        Location l = e.getEntity().getLocation();
        // ヴォイドコア + 追加報酬
        var core = plugin.items().create("void_core");
        if (core != null) l.getWorld().dropItemNaturally(l, core);
        // ドロップDL
        if (e.getEntity().getKiller() != null) {
            plugin.economy().deposit(e.getEntity().getKiller().getUniqueId(), 5000);
        }
        Bukkit.broadcast(Component.text("§d§lカタクリズム・ボスを討伐した！", NamedTextColor.LIGHT_PURPLE));
    }
}
