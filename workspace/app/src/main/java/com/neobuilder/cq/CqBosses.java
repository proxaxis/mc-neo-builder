package com.neobuilder.cq;

import com.neobuilder.NeoBuilderPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Chocolate Quest 系ボス. 種別ごとの固有行動をtickで実行する.
 */
public final class CqBosses implements Listener {

    public enum Kind {
        NECROMANCER(EntityType.WITHER_SKELETON, "ネクロマンサー", 300.0, "badge_necromancer"),
        LICHE(EntityType.WITHER_SKELETON, "リッチ", 300.0, "badge_liche"),
        WALKER_KING(EntityType.ZOMBIE, "ウォーカーキング", 500.0, "badge_walker"),
        PIRATE_CAPTAIN(EntityType.VINDICATOR, "海賊船長", 500.0, "badge_pirate"),
        PHARAOH(EntityType.HUSK, "ファラオ", 400.0, "void_core"),
        SPECTER_LORD(EntityType.ZOMBIE, "亡霊の君主", 400.0, "void_core"),
        GIANT_SPIDER(EntityType.SPIDER, "ジャイアントスパイダー", 450.0, "void_core"),
        MONKING(EntityType.HOGLIN, "モンキング", 760.0, "void_core");

        public final EntityType type; public final String label; public final double hp;
        public final String dropId;
        Kind(EntityType t, String l, double h, String d) { type = t; label = l; hp = h; dropId = d; }
        public static Kind of(String s) {
            try { return valueOf(s.toUpperCase()); } catch (Exception e) { return null; }
        }
    }

    private final NeoBuilderPlugin plugin;
    private NamespacedKey bossKey;
    private final Map<UUID, Long> lastAbility = new HashMap<>();
    private final Random rng = new Random();

    public CqBosses(NeoBuilderPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        bossKey = plugin.key("cq_boss");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public LivingEntity spawn(Location loc, Kind kind) {
        LivingEntity b = (LivingEntity) loc.getWorld().spawnEntity(loc, kind.type);
        b.customName(Component.text(kind.label, NamedTextColor.DARK_RED));
        b.setCustomNameVisible(true);
        var hp = b.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(kind.hp); b.setHealth(kind.hp); }
        var dmg = b.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(14.0);
        var scale = b.getAttribute(Attribute.SCALE);
        if (scale != null && (kind == Kind.GIANT_SPIDER || kind == Kind.MONKING))
            scale.setBaseValue(kind == Kind.GIANT_SPIDER ? 3.0 : 2.0);
        b.setRemoveWhenFarAway(false);
        b.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, kind.name());
        if (b.getEquipment() != null) {
            ItemStack weap = switch (kind) {
                case WALKER_KING -> plugin.items().create("sword_walker");
                case NECROMANCER, LICHE -> plugin.items().create("staff_vampiric");
                case PIRATE_CAPTAIN -> plugin.items().create("dagger_ninja");
                case PHARAOH -> plugin.items().create("battle_axe_bull");
                default -> null;
            };
            if (weap != null) b.getEquipment().setItemInMainHand(weap);
        }
        if (kind == Kind.WALKER_KING && b.getEquipment() != null) {
            var eq = b.getEquipment();
            eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            eq.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        }
        if (kind == Kind.SPECTER_LORD) {
            b.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        // ヒューマノイド系ボスはItemDisplay着ぐるみに差替
        String rigBody = switch (kind) {
            case NECROMANCER -> "rig_necromancer";
            case LICHE -> "rig_liche";
            case WALKER_KING -> "rig_walker_king";
            case PIRATE_CAPTAIN -> "rig_pirate_captain";
            case PHARAOH -> "rig_pharaoh";
            default -> null; // SPECTER_LORD(透明)/GIANT_SPIDER/MONKING はバニラのまま
        };
        if (rigBody != null) {
            plugin.cqrig().attach(b, rigBody, kind == Kind.WALKER_KING ? 1.9 : 1.7);
        }
        Bukkit.broadcast(Component.text("§5§l" + kind.label + " が現れた！", NamedTextColor.DARK_PURPLE));
        return b;
    }

    private Kind kindOf(LivingEntity e) {
        String s = e.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING);
        return s == null ? null : Kind.of(s);
    }

    public boolean isCqBoss(Entity e) {
        return e.getPersistentDataContainer().has(bossKey);
    }

    /** ボス固有行動（1秒周期） */
    private void tick() {
        long now = System.currentTimeMillis();
        for (World w : Bukkit.getWorlds()) {
            for (var ent : new ArrayList<>(w.getLivingEntities())) {
                if (!(ent instanceof LivingEntity boss) || !isCqBoss(boss)) continue;
                Kind k = kindOf(boss);
                if (k == null) continue;
                Player target = nearest(boss, 20);
                switch (k) {
                    case NECROMANCER, LICHE -> {
                        if (target != null && ready(boss, now, 3000)) {
                            // 吸血弾: 直撃+自回復
                            SmallFireball f = boss.launchProjectile(SmallFireball.class,
                                    dir(boss, target).multiply(1.5));
                            f.setGlowing(true);
                        }
                        if (ready(boss, now + 1, 15000) && countMinions(boss) < 4) {
                            plugin.cqm().spawn(boss.getLocation(), CqMobs.Race.SKELETON,
                                    CqMobs.Role.WARRIOR);
                        }
                        if (k == Kind.LICHE && target != null
                                && target.getLocation().distance(boss.getLocation()) < 4
                                && ready(boss, now + 2, 8000)) {
                            shockwave(boss, 5, 1.0);
                        }
                    }
                    case WALKER_KING -> {
                        if (target != null
                                && target.getLocation().distance(boss.getLocation()) < 5
                                && ready(boss, now, 8000)) {
                            shockwave(boss, 6, 1.5); // 突風で押し戻す
                        }
                        if (target != null && ready(boss, now + 1, 15000)) {
                            Vector back = target.getLocation().getDirection().setY(0)
                                    .normalize().multiply(-1.5);
                            boss.teleport(target.getLocation().add(back));
                            boss.getWorld().playSound(boss.getLocation(),
                                    Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                        }
                    }
                    case PIRATE_CAPTAIN -> {
                        if (target != null && ready(boss, now, 10000)) {
                            Vector back = target.getLocation().getDirection().setY(0)
                                    .normalize().multiply(-1.5);
                            boss.teleport(target.getLocation().add(back));
                            boss.addPotionEffect(new PotionEffect(
                                    PotionEffectType.INVISIBILITY, 40, 0));
                        }
                    }
                    case PHARAOH, MONKING -> {
                        if (ready(boss, now, 6000)) groundSlam(boss);
                    }
                    case GIANT_SPIDER -> {
                        if (target != null && ready(boss, now, 8000)) {
                            // 糸で引き寄せ+猛毒
                            Vector pull = boss.getLocation().toVector()
                                    .subtract(target.getLocation().toVector()).normalize()
                                    .multiply(2.0).setY(0.4);
                            target.setVelocity(pull);
                            target.addPotionEffect(new PotionEffect(
                                    PotionEffectType.POISON, 300, 2));
                        }
                    }
                    case SPECTER_LORD -> {
                        // 周期で実体化ウィンドウ（透明解除/再付与）
                        if (ready(boss, now, 20000)) {
                            boss.removePotionEffect(PotionEffectType.INVISIBILITY);
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (boss.isValid()) boss.addPotionEffect(new PotionEffect(
                                        PotionEffectType.INVISIBILITY,
                                        PotionEffect.INFINITE_DURATION, 0, false, false));
                            }, 100L);
                        }
                    }
                }
            }
        }
    }

    private boolean ready(LivingEntity b, long now, long cdMs) {
        Long last = lastAbility.get(b.getUniqueId());
        if (last != null && now - last < cdMs) return false;
        lastAbility.put(b.getUniqueId(), now);
        return true;
    }

    private Player nearest(LivingEntity b, double range) {
        Player best = null; double d = range;
        for (var ent : b.getNearbyEntities(range, 8, range)) {
            if (ent instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                double dist = p.getLocation().distance(b.getLocation());
                if (dist < d) { d = dist; best = p; }
            }
        }
        return best;
    }

    private Vector dir(LivingEntity from, Entity to) {
        return to.getLocation().toVector()
                .subtract(from.getEyeLocation().toVector()).normalize();
    }

    private int countMinions(LivingEntity boss) {
        int n = 0;
        for (var e : boss.getNearbyEntities(15, 8, 15))
            if (e instanceof LivingEntity le && plugin.cqm().isCq(le)) n++;
        return n;
    }

    private void shockwave(LivingEntity boss, double r, double kb) {
        for (var ent : boss.getNearbyEntities(r, 3, r)) {
            if (ent instanceof LivingEntity le && ent != boss) {
                Vector dir = le.getLocation().toVector()
                        .subtract(boss.getLocation().toVector()).normalize();
                le.setVelocity(dir.multiply(kb).setY(0.5));
                le.damage(1.0, boss);
            }
        }
        boss.getWorld().spawnParticle(Particle.EXPLOSION, boss.getLocation(), 10, r / 2, 1, r / 2);
    }

    private void groundSlam(LivingEntity boss) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.5f);
        for (var ent : boss.getNearbyEntities(8, 4, 8)) {
            if (ent instanceof Player p) {
                p.setVelocity(p.getVelocity().setY(0.8));
                p.damage(8.0, boss);
            }
        }
        boss.getWorld().spawnParticle(Particle.BLOCK, boss.getLocation(), 60,
                4, 0.5, 4, Material.DIRT.createBlockData());
    }

    /** ウォーカーキング: 盾防御（近接被弾を軽減+反射） */
    @EventHandler(ignoreCancelled = true)
    public void onBossHurt(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity boss) || !isCqBoss(boss)) return;
        Kind k = kindOf(boss);
        if (k == Kind.WALKER_KING && e.getDamager() instanceof LivingEntity atk
                && rng.nextDouble() < 0.5) {
            e.setDamage(e.getDamage() * 0.4);
            atk.damage(e.getDamage() * 0.5, boss); // ディフレクト反射
        }
        if (k == Kind.GIANT_SPIDER && e.getDamager() instanceof LivingEntity) {
            // 糸を飛ばす（被弾時に攻撃者を鈍足）
            ((LivingEntity) e.getDamager()).addPotionEffect(
                    new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
        }
    }

    /** ボス撃破: バッジ+固有ドロップ+DL報酬 */
    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        LivingEntity boss = e.getEntity();
        if (!isCqBoss(boss)) return;
        Kind k = kindOf(boss);
        Location l = boss.getLocation();
        ItemStack badge = plugin.items().create(k.dropId);
        if (badge != null) l.getWorld().dropItemNaturally(l, badge);
        l.getWorld().dropItemNaturally(l, new ItemStack(Material.DIAMOND, 2 + rng.nextInt(3)));
        if (k == Kind.NECROMANCER && rng.nextDouble() < 0.5) {
            ItemStack b = plugin.items().create("cursed_bone");
            if (b != null) l.getWorld().dropItemNaturally(l, b);
        }
        if (k == Kind.PIRATE_CAPTAIN && rng.nextDouble() < 0.5) {
            ItemStack d = plugin.items().create("dagger_ninja");
            if (d != null) l.getWorld().dropItemNaturally(l, d);
        }
        if (e.getEntity().getKiller() != null) {
            plugin.economy().deposit(e.getEntity().getKiller().getUniqueId(), 3000);
        }
        Bukkit.broadcast(Component.text("§d§l" + k.label + " を討伐した！", NamedTextColor.LIGHT_PURPLE));
    }
}
