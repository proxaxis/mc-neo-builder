package nb.mixin;

import io.netty.channel.ChannelHandlerContext;
import nb.NbHandler;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 接続確立時にdecoder前へ生ByteBuf読み取りハンドラを挿入 */
@Mixin(Connection.class)
public class ConnectionMixin {

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void nb$inject(ChannelHandlerContext ctx, CallbackInfo ci) {
        var p = ctx.pipeline();
        if (p.get("decoder") != null) {
            p.addBefore("decoder", "nb_pipe_handler", new NbHandler());
        }
    }
}
