package com.neobuilder.world;

import com.neobuilder.NeoBuilderPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ボスのアーマースタンド演出リグ.
 * 不可視アーマースタンドに武器を持たせ、ボス周囲を周回させる
 * 「ブレードリング」視覚エフェクト.
 * - 各スタンドはスロット i に応じた角度でボスを周回
 * - 腕のポーズをtick毎に回転させて刃の回転を表現
 * - ボスHP低下（フェーズ2/3）で周回速度と挙動が変化
 * TPS保護: 4体のみ、2tick毎の軽量テレポートで処理する.
 */
public final class BossRig {

    private static final int BLADES = 4;
    private static final double RADIUS = 2.2;

    private final NeoBuilderPlugin plugin;
    private final UUID bossId;
    private final List<ArmorStand> blades = new ArrayList<>();
    private double angle = 0;
    private boolean alive = true;

    public BossRig(NeoBuilderPlugin plugin, LivingEntity boss) {
        this.plugin = plugin;
        this.bossId = boss.getUniqueId();
        Location base = boss.getLocation();
        for (int i = 0; i < BLADES; i++) {
            ArmorStand as = base.getWorld().spawn(base, ArmorStand.class);
            as.setInvisible(true);
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setMarker(false);
            as.setBasePlate(false);
            as.setArms(true);
            // 刃: 剣を持たせる
            var eq = as.getEquipment();
            if (eq != null) {
                eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                eq.setHelmet(new ItemStack(Material.IRON_HELMET));
            }
            as.setPersistent(false);
            blades.add(as);
        }
    }

    public UUID bossId() { return bossId; }
    public boolean alive() { return alive; }

    /** 2tick毎に呼ばれる. 周回＋腕ポーズアニメーション */
    public void tick(LivingEntity boss, double healthRatio) {
        if (!alive || boss == null || !boss.isValid()) { destroy(); return; }
        // フェーズに応じて速度変化（HP低下で加速）
        double speed = healthRatio < 0.25 ? 0.35 : healthRatio < 0.5 ? 0.22 : 0.12;
        angle += speed;
        Location c = boss.getLocation().add(0, 1.2, 0);
        for (int i = 0; i < blades.size(); i++) {
            ArmorStand as = blades.get(i);
            double a = angle + (Math.PI * 2 / BLADES) * i;
            Location pos = c.clone().add(Math.cos(a) * RADIUS, Math.sin(angle * 2) * 0.3, Math.sin(a) * RADIUS);
            // 刃を進行方向に向ける
            pos.setYaw((float) Math.toDegrees(-a));
            as.teleport(pos);
            // 腕ポーズ回転（剣を回転させる演出）
            double spin = (angle * 3 + i) % (Math.PI * 2);
            as.setRightArmPose(new EulerAngle(spin, 0, Math.PI / 2));
            as.setHeadPose(new EulerAngle(Math.sin(angle + i) * 0.3, 0, 0));
        }
    }

    /** 全スタンド除去 */
    public void destroy() {
        alive = false;
        for (ArmorStand as : blades) {
            if (as.isValid()) as.remove();
        }
        blades.clear();
    }
}
