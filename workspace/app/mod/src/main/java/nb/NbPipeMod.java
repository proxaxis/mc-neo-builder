package nb;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * サーバ駆動カスタムブロックMOD.
 * neobuilder:pipe を nb_type(0..37) + 6面bool で登録し、
 * プラグインが送る pos->状態 マップに従ってクライアント側のブロック状態を差替える。
 */
public class NbPipeMod implements ModInitializer, ClientModInitializer {

    public static final Identifier CHANNEL =
            Identifier.fromNamespaceAndPath("neobuilder", "blocks");

    public static Block PIPE_BLOCK;
    public static IntegerProperty NB_TYPE;
    public static BooleanProperty NORTH, EAST, SOUTH, WEST, UP, DOWN;

    /** "x,y,z" -> encoded state (typeIdx<<6 | mask) */
    public static final Map<String, Integer> CUSTOM = new ConcurrentHashMap<>();

    /** BLOCKレジストリfreeze直前にRegistryFreezeMixinから呼ばれる */
    public static synchronized void registerBlock() {
        if (PIPE_BLOCK != null) return;
        NB_TYPE = IntegerProperty.create("nb_type", 0, 37);
        NORTH = BooleanProperty.create("north");
        EAST = BooleanProperty.create("east");
        SOUTH = BooleanProperty.create("south");
        WEST = BooleanProperty.create("west");
        UP = BooleanProperty.create("up");
        DOWN = BooleanProperty.create("down");
        var id = Identifier.fromNamespaceAndPath("neobuilder", "pipe");
        PIPE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, id,
                new PipeBlock(BlockBehaviour.Properties.of()
                        .setId(net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.BLOCK, id))
                        .overrideLootTable(java.util.Optional.empty())
                        .strength(0.75f).sound(SoundType.STONE)));
    }

    @Override
    public void onInitialize() {
    }

    @Override
    public void onInitializeClient() {
    }

    /** プラグインからのpayload: op(varint: 0=全消去 1=登録 2=除去) + 件数 + [x,y,z,state] */
    public static void handlePayload(FriendlyByteBuf buf) {
        int op = buf.readVarInt();
        if (op == 0) { CUSTOM.clear(); return; }
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            int st = buf.readVarInt();
            String k = x + "," + y + "," + z;
            if (op == 1) CUSTOM.put(k, st); else CUSTOM.remove(k);
        }
    }

    public static BlockState fakeState(int enc) {
        int t = enc >> 6, m = enc & 63;
        return PIPE_BLOCK.defaultBlockState()
                .setValue(NB_TYPE, t)
                .setValue(NORTH, (m & 1) != 0)
                .setValue(EAST, (m & 2) != 0)
                .setValue(SOUTH, (m & 4) != 0)
                .setValue(WEST, (m & 8) != 0)
                .setValue(UP, (m & 16) != 0)
                .setValue(DOWN, (m & 32) != 0);
    }

    static class PipeBlock extends Block {
        PipeBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any()
                    .setValue(NB_TYPE, 0).setValue(NORTH, false)
                    .setValue(EAST, false).setValue(SOUTH, false)
                    .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
        }
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(NB_TYPE, NORTH, EAST, SOUTH, WEST, UP, DOWN);
        }
    }
}
