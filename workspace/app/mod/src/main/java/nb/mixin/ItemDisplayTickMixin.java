package nb.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** パイプ表示用ItemDisplay（cmd 31100-31499）はMODブロックが代替するので非表示化 */
@Mixin(Entity.class)
public class ItemDisplayTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void nb$hidePipeDisp(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Display.ItemDisplay d) || self.isInvisible()) return;
        var item = d.getSlot(0).get();
        var cmd = item.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null && !cmd.floats().isEmpty()) {
            int v = cmd.floats().get(0).intValue();
            if (v >= 31100 && v <= 31499) self.setInvisible(true);
        }
    }
}
