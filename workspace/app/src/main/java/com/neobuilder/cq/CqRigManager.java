package com.neobuilder.cq;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CQモブ/ボスのItemDisplay着ぐるみリグを管理する.
 * - attach(): モブの装備をリグに移し本体を透明化
 * - 2tick毎に全リグを追従（BossRigと同周期）
 * - 対象の死亡・消滅で自動除去
 * TPS保護: リグは表示エンティティのみ、AIなし.
 */
public final class CqRigManager implements Listener {

    private final NeoBuilderPlugin plugin;
    private final Map<UUID, MobRig> rigs = new ConcurrentHashMap<>();

    public CqRigManager(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 4L, 2L);
    }

    public void disable() {
        rigs.values().forEach(MobRig::destroy);
        rigs.clear();
    }

    /**
     * モブにリグを装着する.
     * 装備品は透明化時も描画されるため全て除去し、武器はリグ側の
     * ItemDisplay に移管する。防具分は ARMOR 属性で補正する。
     */
    public void attach(LivingEntity mob, String bodyItemId, double scale) {
        ItemStack body = plugin.items().create(bodyItemId);
        if (body == null) return;
        EntityEquipment eq = mob.getEquipment();
        ItemStack weap = eq != null ? eq.getItemInMainHand() : null;
        if (eq != null) eq.clear();
        var armor = mob.getAttribute(Attribute.ARMOR);
        if (armor != null && armor.getBaseValue() < 6) armor.setBaseValue(6);
        mob.setInvisible(true);
        rigs.put(mob.getUniqueId(), new MobRig(plugin, mob, body, weap, scale));
    }

    /** 全リグを2tick毎に追従 */
    private void tick() {
        var it = rigs.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity mob) || !mob.isValid()) {
                e.getValue().destroy();
                it.remove();
                continue;
            }
            e.getValue().tick(mob);
        }
    }

    /** 死亡時にリグ除去（ドロップ武器はリグ側に無いので考慮不要） */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        MobRig r = rigs.remove(e.getEntity().getUniqueId());
        if (r != null) r.destroy();
    }
}
