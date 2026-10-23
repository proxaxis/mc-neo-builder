package nb;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.FriendlyByteBuf;

/**
 * decoder前の生ByteBufを覗いて neobuilder:blocks のペイロードを拾う。
 * （未登録カスタムペイロードはデコード時にidのみ残してデータが捨てられるため）
 */
public class NbHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf b && b.readableBytes() > 8) {
            ByteBuf d = b.duplicate();
            try {
                skipVarInt(d); // packet id
                FriendlyByteBuf f = new FriendlyByteBuf(d);
                String ch = f.readString(32767, java.nio.charset.StandardCharsets.UTF_8);
                if ("neobuilder:blocks".equals(ch)) {
                    NbPipeMod.handlePayload(f);
                }
            } catch (Exception ignored) {}
        }
        ctx.fireChannelRead(msg);
    }

    private static void skipVarInt(ByteBuf b) {
        while (b.readableBytes() > 0) {
            if ((b.readByte() & 0x80) == 0) return;
        }
    }
}
