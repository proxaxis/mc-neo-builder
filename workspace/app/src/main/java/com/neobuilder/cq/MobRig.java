package com.neobuilder.cq;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * ItemDisplay による「着ぐるみ」リグ.
 * 透明化したベースMobに、カスタムボディモデルを表示する
 * ItemDisplay を2tick毎に追従させる（teleportDuration で平滑化）.
 * - body: 種族別フルボディモデル（原点中心のキューボイド）
 * - weapon: 右手位置に配置した武器ItemDisplay（実物のカスタム武器）
 * ベースMobが無効/消滅したら destroy される.
 */
public final class MobRig {

    /** ボディモデルの高さ(20px)/16 * scale / 2 ≈ 0.625*scale */
    private final NeoBuilderPlugin plugin;
    private final LivingEntity mob;
    private final ItemStack bodyItem;
    private final ItemStack weaponItem;
    private final double scale;
    private final double lift;
    private ItemDisplay body;
    private ItemDisplay weapon;
    private double bob;
    private boolean alive = true;

    public MobRig(NeoBuilderPlugin plugin, LivingEntity mob,
                  ItemStack bodyItem, ItemStack weaponItem, double scale) {
        this.plugin = plugin;
        this.mob = mob;
        this.bodyItem = bodyItem;
        this.weaponItem = weaponItem;
        this.scale = scale;
        this.lift = 0.625 * scale;
        spawnDisplays();
    }

    private void spawnDisplays() {
        if (mob == null || !mob.isValid()) return;
        Location l = mob.getLocation().add(0, lift, 0);
        body = l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(bodyItem);
            d.setTeleportDuration(3);
            d.setPersistent(false);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f((float) scale), new Quaternionf()));
        });
        if (weaponItem != null && !weaponItem.getType().isAir()) {
            weapon = l.getWorld().spawn(l, ItemDisplay.class, d -> {
                d.setItemStack(weaponItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);
                d.setTeleportDuration(3);
                d.setPersistent(false);
            });
        }
    }

    /** 2tick毎に呼ぶ. ボス/モブ位置へ追従＋わずかな上下動 */
    public void tick(LivingEntity m) {
        if (!alive) return;
        if (body == null || !body.isValid()) {
            spawnDisplays();
            if (body == null) { destroy(); return; }
        }
        bob += 0.17;
        Location base = m.getLocation();
        Location l = base.clone().add(0, lift + Math.sin(bob) * 0.015, 0);
        body.teleport(l);
        if (weapon != null && weapon.isValid()) {
            // 右手 = 進行方向に対して左斜め前（yaw0=南向き時は -X）
            double yaw = Math.toRadians(base.getYaw());
            Location w = base.clone().add(
                    -Math.cos(yaw) * 0.55, 0.9, -Math.sin(yaw) * 0.55);
            w.setYaw(base.getYaw());
            weapon.teleport(w);
        }
    }

    /** 表示エンティティを全除去 */
    public void destroy() {
        alive = false;
        if (body != null && body.isValid()) body.remove();
        if (weapon != null && weapon.isValid()) weapon.remove();
        body = null;
        weapon = null;
    }
}
