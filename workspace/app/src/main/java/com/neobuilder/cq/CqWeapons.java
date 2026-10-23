package com.neobuilder.cq;

import com.neobuilder.NeoBuilderPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chocolate Quest 系武器の使用挙動.
 * ダガー(背撃/ガード/瞬移)・スピア(長射程/溜め薙ぎ払い/弾発射)・
 * グレートソード(薙ぎ払い)・フックショット・リボルバー・杖・消費アイテム.
 */
public final class CqWeapons implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public CqWeapons(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean cool(Player p, String id, long ms) {
        String k = p.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(k);
        if (until != null && until > now) {
            p.sendActionBar(Component.text("リロード中…", NamedTextColor.GRAY));
            return false;
        }
        cooldowns.put(k, now + ms);
        return true;
    }

    // --- 近接: ダガー背撃 / グレード武器の追加効果 ---
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_ATTACK
                && e.getCause() != EntityDamageByEntityEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        String id = plugin.items().itemId(p.getInventory().getItemInMainHand());
        if (id == null) return;
        if (id.startsWith("dagger_") && e.getEntity() instanceof LivingEntity le) {
            // 背撃: 対象の視線と攻撃者方向の内積が負なら2倍
            Vector facing = le.getLocation().getDirection();
            Vector toAtk = p.getLocation().toVector().subtract(le.getLocation().toVector()).normalize();
            if (facing.dot(toAtk) < -0.3) e.setDamage(e.getDamage() * 2.0);
        }
        if ("sword_spider".equals(id) && e.getEntity() instanceof LivingEntity le) {
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
        }
        if ("battle_axe_bull".equals(id) && e.getEntity() instanceof LivingEntity le) {
            Vector dir = p.getLocation().getDirection().setY(0).normalize();
            le.setVelocity(le.getVelocity().add(dir.multiply(1.4).setY(0.5)));
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null) return;
        String id = plugin.items().itemId(it);
        if (id == null) return;
        Player p = e.getPlayer();
        boolean sneak = p.isSneaking();
        switch (id) {
            // --- ダガー ---
            case "dagger_iron", "dagger_diamond" -> {
                if (!cool(p, id, 4000)) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
                p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.2f);
            }
            case "dagger_ninja" -> {
                if (sneak) { blink(p, 10); cool(p, id, 5000); }
                else if (cool(p, id, 20000)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
                }
            }
            case "dagger_trickster" -> {
                if (sneak) { blink(p, 8); cool(p, id, 8000); }
                else if (cool(p, id, 15000)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20, 0));
                }
            }
            // --- スピア ---
            case "spear_iron", "spear_diamond" -> {
                if (!cool(p, id, 3000)) return;
                sweepLine(p, 5.0, 8.0, 0.8);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            }
            case "spear_fire" -> {
                if (!cool(p, id, 4000)) return;
                for (var ent : lineTargets(p, 8.0)) {
                    ent.damage(4.0, p);
                    ent.setFireTicks(120);
                }
                p.getWorld().spawnParticle(Particle.FLAME, p.getEyeLocation().add(
                        p.getLocation().getDirection().multiply(3)), 40, 1.5, 0.5, 1.5);
                p.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
            }
            case "spear_gun" -> {
                if (!cool(p, id, 3000)) return;
                Arrow a = p.launchProjectile(Arrow.class, p.getLocation().getDirection().multiply(3.5));
                a.setDamage(5.0);
                a.setPierceLevel(3);
                p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.6f);
            }
            // --- グレートソード ---
            case "greatsword_iron", "greatsword_diamond", "greatsword_bull", "greatsword_monking" -> {
                if (!cool(p, id, 5000)) return;
                double dmg = switch (id) {
                    case "greatsword_diamond" -> 9.0;
                    case "greatsword_bull" -> 9.0;
                    case "greatsword_monking" -> 10.0;
                    default -> 8.0;
                };
                sweepArc(p, 4.5, dmg, 1.2);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
            }
            // --- ガジェット ---
            case "hookshot" -> {
                if (!cool(p, id, 5000)) return;
                var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(),
                        p.getEyeLocation().getDirection(), 20,
                        ent -> ent != p && ent instanceof LivingEntity);
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity le)) {
                    p.sendActionBar(Component.text("届かない", NamedTextColor.GRAY));
                    return;
                }
                Vector pull = p.getLocation().toVector().subtract(le.getLocation().toVector())
                        .normalize().multiply(1.8).setY(0.4);
                le.setVelocity(pull);
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.8f);
            }
            case "revolver" -> {
                if (!cool(p, id, 3000)) return;
                Arrow a = p.launchProjectile(Arrow.class, p.getLocation().getDirection().multiply(4.0));
                a.setDamage(3.0);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
            }
            // --- 杖 ---
            case "staff_healing" -> {
                if (!cool(p, id, 2000)) return;
                LivingEntity target = lookTarget(p, 10);
                if (target == null) target = p;
                var attr = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null)
                    target.setHealth(Math.min(attr.getValue(), target.getHealth() + 6.0));
                target.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        target.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 2f);
            }
            case "staff_fire" -> {
                if (!cool(p, id, 2000)) return;
                Fireball f = p.launchProjectile(SmallFireball.class,
                        p.getLocation().getDirection().multiply(1.8));
                f.setYield(0);
            }
            case "staff_thunder" -> {
                if (!cool(p, id, 5000)) return;
                Location t = p.getTargetBlock(30).getLocation();
                t.getWorld().strikeLightning(t);
            }
            case "staff_wind" -> {
                if (!cool(p, id, 3000)) return;
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                for (var ent : p.getNearbyEntities(6, 3, 6)) {
                    if (ent instanceof LivingEntity le && ent != p) {
                        le.setVelocity(dir.clone().multiply(2.0).setY(0.6));
                        le.damage(1.0, p);
                    }
                }
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 30, 3, 1, 3);
            }
            case "staff_vampiric" -> {
                if (!cool(p, id, 2500)) return;
                LivingEntity t = lookTarget(p, 15);
                if (t == null) { p.sendActionBar(Component.text("対象なし", NamedTextColor.GRAY)); return; }
                t.damage(3.0, p);
                var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) p.setHealth(Math.min(attr.getValue(), p.getHealth() + 3.0));
                p.getWorld().spawnParticle(Particle.HEART, t.getLocation().add(0, 1, 0), 5);
            }
            case "staff_poison" -> {
                if (!cool(p, id, 4000)) return;
                for (var ent : p.getNearbyEntities(6, 3, 6)) {
                    if (ent instanceof LivingEntity le && ent != p && ent instanceof Monster) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1));
                    }
                }
                p.getWorld().spawnParticle(Particle.ITEM_SLIME, p.getLocation(), 30, 3, 1, 3);
            }
            case "staff_spider" -> {
                if (!cool(p, id, 4000)) return;
                LivingEntity t = lookTarget(p, 15);
                if (t == null) { p.sendActionBar(Component.text("対象なし", NamedTextColor.GRAY)); return; }
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
                t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 128));
                t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 30, 0.5, 1, 0.5);
            }
            // --- 消費アイテム ---
            case "potion_healing" -> {
                var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr == null || p.getHealth() >= attr.getValue()) return;
                p.setHealth(Math.min(attr.getValue(), p.getHealth() + 4.0));
                it.setAmount(it.getAmount() - 1);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
            }
            case "cursed_bone" -> {
                LivingEntity target = lookTarget(p, 20);
                it.setAmount(it.getAmount() - 1);
                for (int i = 0; i < 2; i++) {
                    Skeleton s = (Skeleton) p.getWorld().spawnEntity(
                            p.getLocation().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1),
                            EntityType.SKELETON);
                    s.customName(Component.text("吸血スケルトン", NamedTextColor.DARK_PURPLE));
                    if (target != null) s.setTarget(target);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1f, 0.6f);
            }
            default -> {}
        }
    }

    private LivingEntity lookTarget(Player p, double range) {
        var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(),
                p.getEyeLocation().getDirection(), range, e -> e != p && e instanceof LivingEntity);
        return hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    private void blink(Player p, double dist) {
        Location dest = p.getLocation().add(p.getLocation().getDirection().setY(0)
                .normalize().multiply(dist));
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            var r = p.getWorld().rayTraceBlocks(p.getEyeLocation(),
                    p.getLocation().getDirection(), dist);
            if (r != null && r.getHitBlock() != null) return;
        }
        p.teleport(dest);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation(), 20);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
    }

    /** 前方直線上の敵へダメージ+ノックバック */
    private void sweepLine(Player p, double range, double dmg, double kb) {
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        for (var ent : lineTargets(p, range)) {
            ent.damage(dmg, p);
            ent.setVelocity(dir.clone().multiply(kb).setY(0.3));
        }
    }

    private java.util.List<LivingEntity> lineTargets(Player p, double range) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        for (var ent : p.getNearbyEntities(range, 3, range)) {
            if (!(ent instanceof LivingEntity le) || ent == p) continue;
            Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (to.length() < 0.1) continue;
            if (to.normalize().dot(dir) > 0.6) out.add(le);
        }
        return out;
    }

    private void sweepArc(Player p, double range, double dmg, double kb) {
        sweepLine(p, range, dmg, kb);
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                p.getEyeLocation().add(p.getLocation().getDirection().multiply(2)), 8, 1, 0.5, 1);
    }
}
