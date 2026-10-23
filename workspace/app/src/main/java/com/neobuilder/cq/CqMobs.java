package com.neobuilder.cq;

import com.neobuilder.NeoBuilderPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Chocolate Quest 系ダンジョンMob.
 * 種族(Race) x 兵科(Role) で武装Mobを生成し、兵科別の特殊行動と
 * 「瀕死で後退→回復ポーション」を再現する.
 */
public final class CqMobs implements Listener {

    public enum Race {
        SKELETON(EntityType.SKELETON, "武装スケルトン", 20.0, Material.BONE),
        ZOMBIE(EntityType.ZOMBIE, "武装ゾンビ", 25.0, Material.ROTTEN_FLESH),
        PIRATE(EntityType.VINDICATOR, "海賊", 30.0, Material.GUNPOWDER),
        WALKER(EntityType.ZOMBIE, "ウォーカー", 35.0, Material.ENDER_PEARL),
        SPECTER(EntityType.ZOMBIE, "亡霊", 35.0, Material.GUNPOWDER),
        MUMMY(EntityType.HUSK, "ミイラ", 60.0, Material.ROTTEN_FLESH),
        PIGMAN(EntityType.ZOMBIFIED_PIGLIN, "武装ピッグマン", 35.0, Material.GOLD_NUGGET),
        GREMLIN(EntityType.SILVERFISH, "グレムリン", 40.0, Material.COAL),
        MINOTAUR(EntityType.HOGLIN, "ミノタウロス", 80.0, Material.LEATHER);

        public final EntityType type; public final String label; public final double hp;
        public final Material drop;
        Race(EntityType t, String l, double h, Material d) { type = t; label = l; hp = h; drop = d; }
        public static Race of(String s) {
            try { return valueOf(s.toUpperCase()); } catch (Exception e) { return ZOMBIE; }
        }
    }

    public enum Role {
        WARRIOR, ARCHER, HEALER, DEFENDER, SPEARMAN, BERSERKER, NINJA, BANNERMAN, MAGE, HOOKMAN;
        public static Role of(String s) {
            try { return valueOf(s.toUpperCase()); } catch (Exception e) { return WARRIOR; }
        }
    }

    private final NeoBuilderPlugin plugin;
    private NamespacedKey raceKey, roleKey, healKey;
    private final Random rng = new Random();

    public CqMobs(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        raceKey = plugin.key("cq_race");
        roleKey = plugin.key("cq_role");
        healKey = plugin.key("cq_healed");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // 兵科の定期行動（ヒーラー回復・バナーマンバフ）: 5秒周期
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickRoles, 100L, 100L);
    }

    public LivingEntity spawn(Location loc, Race race, Role role) {
        LivingEntity mob = (LivingEntity) loc.getWorld().spawnEntity(loc, race.type);
        mob.customName(Component.text(race.label + "・" + role.name(), NamedTextColor.GRAY));
        mob.setCustomNameVisible(false);
        var hp = mob.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) { hp.setBaseValue(race.hp); mob.setHealth(race.hp); }
        mob.setRemoveWhenFarAway(false);
        mob.getPersistentDataContainer().set(raceKey, PersistentDataType.STRING, race.name());
        mob.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role.name());
        equip(mob, race, role);
        // ヒューマノイド系はItemDisplay着ぐるみに差替（透明化+武器はリグへ移管）
        String rigBody = switch (race) {
            case SKELETON -> "rig_skeleton";
            case ZOMBIE -> "rig_zombie";
            case PIRATE -> "rig_pirate";
            case WALKER -> "rig_walker";
            case MUMMY -> "rig_mummy";
            case PIGMAN -> "rig_pigman";
            default -> null; // SPECTER(常時透明)/GREMLIN/MINOTAUR はバニラのまま
        };
        if (rigBody != null) plugin.cqrig().attach(mob, rigBody, 1.35);
        if (race == Race.SPECTER) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        if (race == Race.PIGMAN) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                    PotionEffect.INFINITE_DURATION, 0, false, false));
        }
        return mob;
    }

    /** 兵科別の装備（武器はカスタムアイテムを使用） */
    private void equip(LivingEntity mob, Race race, Role role) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;
        // 防具: 革/鉄/ダイヤからランダム
        Material[][] sets = {
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
            {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS},
        };
        var set = sets[rng.nextInt(sets.length)];
        eq.setHelmet(new ItemStack(set[0]));
        eq.setChestplate(new ItemStack(set[1]));
        eq.setLeggings(new ItemStack(set[2]));
        eq.setBoots(new ItemStack(set[3]));
        eq.setHelmetDropChance(0.15f); eq.setChestplateDropChance(0.15f);
        eq.setLeggingsDropChance(0.15f); eq.setBootsDropChance(0.15f);
        ItemStack weap = switch (role) {
            case ARCHER -> race == Race.PIRATE ? plugin.items().create("revolver")
                    : new ItemStack(Material.BOW);
            case HEALER -> plugin.items().create("staff_healing");
            case DEFENDER -> plugin.items().create("dagger_iron");
            case SPEARMAN -> plugin.items().create("spear_iron");
            case BERSERKER -> plugin.items().create("greatsword_iron");
            case NINJA -> plugin.items().create("dagger_ninja");
            case BANNERMAN -> new ItemStack(Material.WHITE_BANNER);
            case MAGE -> plugin.items().create("staff_fire");
            case HOOKMAN -> plugin.items().create("hookshot");
            default -> new ItemStack(Material.IRON_SWORD);
        };
        if (weap != null) { eq.setItemInMainHand(weap); eq.setItemInMainHandDropChance(0.1f); }
        if (role == Role.DEFENDER) eq.setItemInOffHand(new ItemStack(Material.SHIELD));
    }

    public boolean isCq(LivingEntity e) {
        return e.getPersistentDataContainer().has(raceKey);
    }

    /** 瀕死時: 高速後退 + 回復ポーション（+10回復・一度きり）、兵科別被弾補正 */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity mob) || !isCq(mob)) return;
        String role = mob.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        // 衛兵: 常時半減（盾防御の再現）
        if ("DEFENDER".equals(role)) e.setDamage(e.getDamage() * 0.5);
        // ニンジャ: 背後からの被弾2倍
        if ("NINJA".equals(role) && e instanceof EntityDamageByEntityEvent de
                && de.getDamager() instanceof Player p) {
            Vector facing = mob.getLocation().getDirection();
            Vector toAtk = p.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize();
            if (facing.dot(toAtk) < -0.3) e.setDamage(e.getDamage() * 2.0);
        }
        // 瀕死で逃走+回復（1回のみ）
        var attr = mob.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        if (mob.getHealth() - e.getFinalDamage() > attr.getValue() * 0.25) return;
        if (mob.getPersistentDataContainer().has(healKey)) return;
        mob.getPersistentDataContainer().set(healKey, PersistentDataType.BYTE, (byte) 1);
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mob.isValid()) return;
            mob.setHealth(Math.min(attr.getValue(), mob.getHealth() + 10.0));
            mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.8f);
            mob.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, mob.getLocation().add(0, 1, 0), 10);
        }, 30L);
    }

    /** ヒーラー/バナーマンの定期サポート行動 */
    private void tickRoles() {
        for (World w : Bukkit.getWorlds()) {
            for (var ent : w.getLivingEntities()) {
                if (!(ent instanceof LivingEntity mob) || !isCq(mob)) continue;
                String role = mob.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
                if ("HEALER".equals(role)) {
                    for (var near : mob.getNearbyEntities(8, 4, 8)) {
                        if (near instanceof LivingEntity ally && isCq(ally) && ally != mob) {
                            var a = ally.getAttribute(Attribute.MAX_HEALTH);
                            if (a != null && ally.getHealth() < a.getValue()) {
                                ally.setHealth(Math.min(a.getValue(), ally.getHealth() + 4.0));
                                ally.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                                        ally.getLocation().add(0, 1, 0), 5);
                            }
                        }
                    }
                } else if ("BANNERMAN".equals(role)) {
                    for (var near : mob.getNearbyEntities(10, 5, 10)) {
                        if (near instanceof LivingEntity ally && isCq(ally) && ally != mob) {
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
                            ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                        }
                    }
                }
            }
        }
    }

    /** ドロップ: 種族ドロップ + 鉱石系レアドロップ */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (!isCq(mob)) return;
        Race race = Race.of(mob.getPersistentDataContainer().get(raceKey, PersistentDataType.STRING));
        e.getDrops().add(new ItemStack(race.drop, 1 + rng.nextInt(2)));
        if (rng.nextDouble() < 0.15) e.getDrops().add(new ItemStack(Material.EMERALD));
        if (rng.nextDouble() < 0.15) e.getDrops().add(new ItemStack(Material.LAPIS_LAZULI, 1 + rng.nextInt(3)));
        if (rng.nextDouble() < 0.10) e.getDrops().add(new ItemStack(Material.GOLD_NUGGET, 1 + rng.nextInt(4)));
    }
}
