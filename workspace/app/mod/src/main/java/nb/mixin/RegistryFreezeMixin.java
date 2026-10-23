package nb.mixin;

import nb.NbPipeMod;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BLOCKレジストリのfreeze直前にカスタムブロックを登録（intrusive holder生成可能な最終窓口） */
@Mixin(MappedRegistry.class)
public class RegistryFreezeMixin {

    @Inject(method = "freeze", at = @At("HEAD"))
    private void nb$preFreeze(CallbackInfoReturnable<?> ci) {
        if ((Object) this == BuiltInRegistries.BLOCK) {
            NbPipeMod.registerBlock();
        }
    }
}
