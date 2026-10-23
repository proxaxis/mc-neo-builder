package nb.mixin;

import nb.NbPipeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** サーバpayload受信 + チャンク/ブロック更新後のカスタムブロック差替え */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void nb$chunk(ClientboundLevelChunkWithLightPacket p, CallbackInfo ci) {
        var lvl = Minecraft.getInstance().level;
        if (lvl == null) return;
        NbPipeMod.CUSTOM.forEach((k, enc) -> {
            String[] pr = k.split(",");
            int x = Integer.parseInt(pr[0]), y = Integer.parseInt(pr[1]), z = Integer.parseInt(pr[2]);
            if (x >> 4 != p.x() || z >> 4 != p.z()) return;
            lvl.setBlock(new BlockPos(x, y, z), NbPipeMod.fakeState(enc), 19);
        });
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void nb$upd(ClientboundBlockUpdatePacket p, CallbackInfo ci) {
        apply(p.getPos());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void nb$updMulti(ClientboundSectionBlocksUpdatePacket p, CallbackInfo ci) {
        p.runUpdates((pos, st) -> apply(pos));
    }

    private void apply(BlockPos pos) {
        Integer enc = NbPipeMod.CUSTOM.get(pos.getX() + "," + pos.getY() + "," + pos.getZ());
        var lvl = Minecraft.getInstance().level;
        if (enc != null && lvl != null) {
            lvl.setBlock(pos, NbPipeMod.fakeState(enc), 19);
        }
    }
}
