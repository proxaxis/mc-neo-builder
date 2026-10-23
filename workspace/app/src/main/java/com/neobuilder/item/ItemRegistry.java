package com.neobuilder.item;

import com.neobuilder.NeoBuilderPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * カスタムアイテム定義・生成.
 * PDC "nb_item" にアイテムID、"nb_tech" に技術陣営タグを保持する.
 */
public final class ItemRegistry {

    public enum Tech { NONE, SCIENCE, MAGIC }

    /** カスタムアイテム定義 */
    public record ItemDef(String id, Material base, int modelData, String displayName,
                          NamedTextColor color, Tech tech, List<String> lore) {}

    private final NeoBuilderPlugin plugin;
    private final Map<String, ItemDef> defs = new HashMap<>();
    private NamespacedKey itemKey;
    private NamespacedKey techKey;

    public ItemRegistry(NeoBuilderPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = plugin.key("item");
        this.techKey = plugin.key("tech");
        registerAll();
    }

    private void reg(String id, Material base, int cmd, String name, NamedTextColor color,
                     Tech tech, String... lore) {
        defs.put(id, new ItemDef(id, base, cmd, name, color, tech, List.of(lore)));
    }

    private void registerAll() {
        // --- ツール ---
        reg("wrench", Material.AMETHYST_SHARD, 10001, "レンチ", NamedTextColor.GRAY, Tech.NONE,
                "マシン・パイプの回収ツール");

        // --- エンジン（設置ブロック）---
        reg("engine_redstone", Material.REDSTONE_LAMP, 10010, "レッドストーンエンジン",
                NamedTextColor.RED, Tech.SCIENCE, "出力: 1 MJ/t", "レッドストーン信号で稼働");
        reg("engine_stirling", Material.SMOKER, 10011, "スターリングエンジン",
                NamedTextColor.GOLD, Tech.SCIENCE, "出力: 5 MJ/t", "燃料: 石炭・木炭");
        reg("engine_combustion", Material.BLAST_FURNACE, 10012, "燃焼エンジン",
                NamedTextColor.DARK_RED, Tech.SCIENCE, "出力: 20 MJ/t", "燃料: 精製油");
        reg("engine_nuclear", Material.CRYING_OBSIDIAN, 10013, "原子力エンジン",
                NamedTextColor.DARK_PURPLE, Tech.SCIENCE, "出力: 100 MJ/t", "取扱注意");

        // --- マシン ---
        reg("quarry", Material.LODESTONE, 10020, "クァーリー", NamedTextColor.AQUA, Tech.SCIENCE,
                "指定範囲を自動採掘", "要: ランドマーク");
        reg("filler", Material.RESPAWN_ANCHOR, 10021, "フィラー", NamedTextColor.GREEN, Tech.SCIENCE,
                "整地・充填マシン");
        reg("macerator", Material.GRINDSTONE, 10022, "粉砕機", NamedTextColor.YELLOW, Tech.SCIENCE,
                "鉱石を粉に変換");
        reg("landmark", Material.TORCH, 10023, "ランドマーク", NamedTextColor.WHITE, Tech.SCIENCE,
                "範囲指定用マーカー");

        // --- パイプ ---
        reg("pipe_transport", Material.GLASS, 10030, "輸送パイプ", NamedTextColor.WHITE, Tech.SCIENCE,
                "アイテムを搬送");
        reg("pipe_extract", Material.OAK_FENCE, 10031, "抽出パイプ", NamedTextColor.GOLD, Tech.SCIENCE,
                "インベントリから抽出");
        reg("pipe_gold", Material.GLOWSTONE, 10032, "金パイプ", NamedTextColor.YELLOW, Tech.SCIENCE,
                "アイテムを加速");
        reg("pipe_diamond", Material.SEA_LANTERN, 10033, "ダイヤパイプ", NamedTextColor.AQUA, Tech.SCIENCE,
                "分岐ルーティング");
        reg("pipe_obsidian", Material.OBSIDIAN, 10034, "黒曜石パイプ", NamedTextColor.DARK_GRAY, Tech.SCIENCE,
                "地上アイテムを吸引");
        reg("pipe_teleport", Material.ENDER_CHEST, 10035, "テレポートパイプ", NamedTextColor.LIGHT_PURPLE,
                Tech.SCIENCE, "周波数で無線転送", "右クリックで周波数設定");
        reg("pipe_fluid", Material.BLUE_STAINED_GLASS, 10036, "流体パイプ", NamedTextColor.BLUE, Tech.SCIENCE,
                "液体を搬送");
        reg("pipe_kinesis", Material.YELLOW_STAINED_GLASS, 10037, "キネシスパイプ", NamedTextColor.YELLOW,
                Tech.SCIENCE, "エネルギー(MJ)を伝送");

        // --- ロジスティクス ---
        reg("pipe_logistics", Material.LIME_STAINED_GLASS, 10040, "ロジスティクスパイプ",
                NamedTextColor.GREEN, Tech.SCIENCE, "物流網の基本パイプ");
        reg("pipe_provider", Material.RED_STAINED_GLASS, 10041, "プロバイダーパイプ",
                NamedTextColor.RED, Tech.SCIENCE, "接続先の在庫を公開");
        reg("pipe_request", Material.LIGHT_BLUE_STAINED_GLASS, 10042, "リクエストパイプ",
                NamedTextColor.AQUA, Tech.SCIENCE, "右クリックで在庫検索・取り寄せ");
        reg("remote_orderer", Material.COMPASS, 10043, "リモートオーダー", NamedTextColor.AQUA,
                Tech.SCIENCE, "物流網へリモートアクセス");
        reg("pipe_supplier", Material.ORANGE_STAINED_GLASS, 10044, "サプライヤーパイプ",
                NamedTextColor.GOLD, Tech.SCIENCE, "指定アイテムを継続供給");
        reg("pipe_crafting", Material.CYAN_STAINED_GLASS, 10045, "クラフティングパイプ",
                NamedTextColor.BLUE, Tech.SCIENCE, "オンデマンド自動クラフト");
        reg("pipe_satellite", Material.MAGENTA_STAINED_GLASS, 10046, "サテライトパイプ",
                NamedTextColor.LIGHT_PURPLE, Tech.SCIENCE, "物流網の中継ポイント");

        // --- 追加輸送パイプ ---
        reg("pipe_stone", Material.STONE, 10100, "石材輸送パイプ", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("pipe_sandstone", Material.SANDSTONE, 10101, "砂岩輸送パイプ", NamedTextColor.YELLOW, Tech.SCIENCE, "");
        reg("pipe_iron", Material.IRON_BLOCK, 10102, "鉄輸送パイプ", NamedTextColor.WHITE, Tech.SCIENCE, "一方通行制御");
        reg("pipe_emerald", Material.EMERALD_BLOCK, 10103, "エメラルド輸送パイプ", NamedTextColor.GREEN, Tech.SCIENCE, "フィルタ抽出");
        reg("pipe_void", Material.BLACK_STAINED_GLASS, 10104, "ヴォイド輸送パイプ", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "アイテム廃棄");
        reg("pipe_distribution", Material.BROWN_STAINED_GLASS, 10105, "分配輸送パイプ", NamedTextColor.GOLD, Tech.SCIENCE, "ラウンドロビン分配");
        reg("pipe_teleport_fluid", Material.PURPLE_STAINED_GLASS, 10106, "防水テレポートパイプ", NamedTextColor.DARK_PURPLE, Tech.SCIENCE, "液体を無線転送");
        reg("pipe_teleport_structure", Material.GRAY_STAINED_GLASS, 10107, "構造テレポートパイプ", NamedTextColor.GRAY, Tech.SCIENCE, "電力を無線転送");
        // 流体パイプ材質別
        reg("pipe_fluid_cobble", Material.PRISMARINE_BRICKS, 10108, "丸石流体パイプ", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("pipe_fluid_stone", Material.ANDESITE, 10109, "石材流体パイプ", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("pipe_fluid_sand", Material.CUT_SANDSTONE, 10110, "砂岩流体パイプ", NamedTextColor.YELLOW, Tech.SCIENCE, "");
        reg("pipe_fluid_iron", Material.QUARTZ_BLOCK, 10111, "鉄流体パイプ", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("pipe_fluid_gold", Material.SHROOMLIGHT, 10112, "金流体パイプ", NamedTextColor.YELLOW, Tech.SCIENCE, "");
        reg("pipe_fluid_emerald", Material.PRISMARINE, 10113, "エメラルド流体パイプ", NamedTextColor.GREEN, Tech.SCIENCE, "");
        reg("pipe_fluid_void", Material.BLACK_CONCRETE, 10114, "ヴォイド流体パイプ", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("pipe_fluid_diamond", Material.DIAMOND_BLOCK, 10115, "ダイヤ流体パイプ", NamedTextColor.AQUA, Tech.SCIENCE, "");

        // --- BC 部品・資材 ---
        reg("gear_wood", Material.STICK, 10120, "木製ギア", NamedTextColor.GOLD, Tech.SCIENCE, "");
        reg("gear_stone", Material.FLINT, 10121, "石製ギア", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("gear_iron", Material.IRON_NUGGET, 10122, "鉄製ギア", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("gear_gold", Material.GOLD_NUGGET, 10123, "金製ギア", NamedTextColor.YELLOW, Tech.SCIENCE, "");
        reg("gear_diamond", Material.DIAMOND, 10124, "ダイヤ製ギア", NamedTextColor.AQUA, Tech.SCIENCE, "");
        reg("pipe_sealant", Material.SLIME_BALL, 10125, "パイプシーラント", NamedTextColor.GREEN, Tech.SCIENCE, "パイプを防水化");
        reg("pipe_plug", Material.IRON_BARS, 10126, "パイププラグ", NamedTextColor.GRAY, Tech.SCIENCE, "パイプ接続を遮断");
        reg("oil_bucket", Material.LAVA_BUCKET, 10127, "原油バケツ", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "原油の入ったバケツ");
        reg("fuel_bucket", Material.BUCKET, 10128, "燃料バケツ", NamedTextColor.GOLD, Tech.SCIENCE, "精製済み燃料");
        reg("teleport_tether", Material.END_ROD, 10129, "テレポートテザー", NamedTextColor.LIGHT_PURPLE, Tech.SCIENCE, "");

        // --- BC 追加マシン ---
        reg("auto_workbench", Material.CRAFTING_TABLE, 10140, "自動作業台", NamedTextColor.GOLD, Tech.SCIENCE, "自動クラフト");
        reg("tank", Material.HAY_BLOCK, 10141, "タンク", NamedTextColor.GRAY, Tech.SCIENCE, "液体貯蔵 16000mB");
        reg("pump", Material.MAGMA_BLOCK, 10142, "ポンプ", NamedTextColor.RED, Tech.SCIENCE, "液体汲み上げ");
        reg("refinery", Material.SMITHING_TABLE, 10143, "精製所", NamedTextColor.DARK_RED, Tech.SCIENCE, "原油→燃料");

        // ================= IC2 系 =================
        // 発電
        reg("generator", Material.TARGET, 10200, "発電機", NamedTextColor.GRAY, Tech.SCIENCE, "固体燃料→MJ");
        reg("solar_panel", Material.DAYLIGHT_DETECTOR, 10201, "ソーラーパネル", NamedTextColor.BLUE, Tech.SCIENCE, "日照発電");
        reg("nuclear_reactor", Material.NETHERITE_BLOCK, 10202, "原子炉", NamedTextColor.DARK_GREEN, Tech.SCIENCE, "ウラン燃料で高出力");
        reg("reactor_chamber", Material.HONEYCOMB_BLOCK, 10203, "リアクターチェンバー", NamedTextColor.GRAY, Tech.SCIENCE, "");
        // ケーブル（キネシスとして動作）
        reg("cable_copper", Material.ORANGE_WOOL, 10210, "銅ケーブル", NamedTextColor.GOLD, Tech.SCIENCE, "低圧配線");
        reg("cable_gold", Material.YELLOW_WOOL, 10211, "金ケーブル", NamedTextColor.YELLOW, Tech.SCIENCE, "中圧配線");
        reg("cable_gold_ins2", Material.YELLOW_CARPET, 10212, "金ケーブル(絶縁x2)", NamedTextColor.YELLOW, Tech.SCIENCE, "");
        reg("cable_hv", Material.IRON_TRAPDOOR, 10213, "HVケーブル", NamedTextColor.WHITE, Tech.SCIENCE, "高圧配線");
        reg("cable_hv_ins2", Material.IRON_DOOR, 10214, "HVケーブル(絶縁x2)", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("cable_hv_ins4", Material.IRON_BARS, 10215, "HVケーブル(絶縁x4)", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("cable_glass", Material.GLASS_PANE, 10216, "グラスファイバー", NamedTextColor.AQUA, Tech.SCIENCE, "超低損失配線");
        // 変圧器（キネシスとして動作）
        reg("transformer_lv", Material.POLISHED_ANDESITE, 10220, "LV変圧器", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("transformer_mv", Material.POLISHED_DIORITE, 10221, "MV変圧器", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("transformer_hv", Material.POLISHED_GRANITE, 10222, "HV変圧器", NamedTextColor.GOLD, Tech.SCIENCE, "");
        // 蓄電（MJバッテリとして動作）
        reg("batbox", Material.STRIPPED_SPRUCE_LOG, 10230, "BatBox", NamedTextColor.GOLD, Tech.SCIENCE, "蓄電 40000 MJ");
        reg("mfe", Material.STRIPPED_OAK_LOG, 10231, "MFEユニット", NamedTextColor.YELLOW, Tech.SCIENCE, "蓄電 600000 MJ");
        reg("mfsu", Material.STRIPPED_DARK_OAK_LOG, 10232, "MFSユニット", NamedTextColor.DARK_PURPLE, Tech.SCIENCE, "蓄電 10000000 MJ");
        reg("re_battery", Material.REDSTONE, 10233, "REバッテリー", NamedTextColor.RED, Tech.SCIENCE, "");
        reg("energy_crystal", Material.DIAMOND, 10234, "エネルギークリスタル", NamedTextColor.AQUA, Tech.SCIENCE, "");
        reg("batpack", Material.LEATHER_CHESTPLATE, 10235, "バットパック", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("lappack", Material.IRON_CHESTPLATE, 10236, "ラップパック", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("lapotron_crystal", Material.EMERALD, 10237, "ラポトロンクリスタル", NamedTextColor.GREEN, Tech.SCIENCE, "");
        // 加工機械
        reg("iron_furnace", Material.FURNACE, 10240, "鉄かまど", NamedTextColor.WHITE, Tech.SCIENCE, "1.25倍速かまど");
        reg("electric_furnace", Material.FURNACE, 10241, "電気かまど", NamedTextColor.WHITE, Tech.SCIENCE, "電力で精錬");
        reg("induction_furnace", Material.FURNACE, 10242, "誘導かまど", NamedTextColor.GOLD, Tech.SCIENCE, "高速電気かまど");
        reg("compressor", Material.MOSS_BLOCK, 10243, "圧縮機", NamedTextColor.GRAY, Tech.SCIENCE, "素材を圧縮");
        reg("extractor", Material.DRIPSTONE_BLOCK, 10244, "抽出機", NamedTextColor.YELLOW, Tech.SCIENCE, "ゴム等を抽出");
        reg("mass_fabricator", Material.ANCIENT_DEBRIS, 10245, "物質生成機", NamedTextColor.DARK_PURPLE, Tech.SCIENCE, "莫大なMJで物質生成");
        reg("machine_block", Material.RAW_IRON_BLOCK, 10246, "マシンブロック", NamedTextColor.WHITE, Tech.SCIENCE, "機械の筐体");
        reg("advanced_machine", Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 10247, "高度マシン", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("tesla_coil", Material.LIGHTNING_ROD, 10248, "テスラコイル", NamedTextColor.AQUA, Tech.SCIENCE, "周囲の敵に放電");
        reg("reinforced_glass", Material.GLASS, 10249, "強化ガラス", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("reinforced_stone", Material.STONE, 10250, "強化石材", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("reinforced_door", Material.IRON_DOOR, 10251, "強化ドア", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("teleporter", Material.BEACON, 10252, "テレポーター", NamedTextColor.LIGHT_PURPLE, Tech.SCIENCE, "2点間を瞬間移動");
        // 資材・素材
        reg("refined_iron", Material.IRON_INGOT, 10260, "精錬鉄", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("uranium", Material.EMERALD_ORE, 10261, "ウラン鉱石", NamedTextColor.GREEN, Tech.SCIENCE, "地下に生成");
        reg("refined_uranium", Material.EMERALD, 10262, "精製ウラン", NamedTextColor.GREEN, Tech.SCIENCE, "");
        reg("advanced_alloy", Material.IRON_INGOT, 10263, "高度合金", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("dense_copper_plate", Material.COPPER_INGOT, 10264, "高密度銅板", NamedTextColor.GOLD, Tech.SCIENCE, "");
        reg("tin_ingot", Material.IRON_INGOT, 10265, "錫", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("dust_tin", Material.SUGAR, 10266, "錫粉", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("bronze_ingot", Material.COPPER_INGOT, 10267, "青銅", NamedTextColor.GOLD, Tech.SCIENCE, "");
        reg("dust_bronze", Material.GLOWSTONE_DUST, 10268, "青銅粉", NamedTextColor.GOLD, Tech.SCIENCE, "");
        reg("mixed_metal_ingot", Material.NETHERITE_INGOT, 10269, "混合金属インゴット", NamedTextColor.GRAY, Tech.SCIENCE, "");
        reg("iridium_plate", Material.NETHERITE_SCRAP, 10270, "イリジウム板", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("iridium_ore", Material.DIAMOND_ORE, 10271, "イリジウム鉱石", NamedTextColor.AQUA, Tech.SCIENCE, "極めて稀に生成");
        reg("raw_carbon_fibre", Material.STRING, 10272, "生炭素繊維", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("raw_carbon_mesh", Material.COBWEB, 10273, "生炭素メッシュ", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("carbon_plate", Material.NETHERITE_SCRAP, 10274, "カーボンプレート", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("coal_ball", Material.COAL, 10275, "石炭ボール", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("compressed_coal_ball", Material.COAL_BLOCK, 10276, "圧縮石炭ボール", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("coal_chunk", Material.FLINT, 10277, "石炭チャンク", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("rubber_wood", Material.JUNGLE_LOG, 10278, "ゴムの原木", NamedTextColor.GREEN, Tech.SCIENCE, "ゴムの木の原木");
        reg("rubber", Material.SLIME_BALL, 10279, "ゴム", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("circuit", Material.REPEATER, 10280, "電子回路", NamedTextColor.GREEN, Tech.SCIENCE, "");
        reg("advanced_circuit", Material.COMPARATOR, 10281, "高度電子回路", NamedTextColor.RED, Tech.SCIENCE, "");
        reg("matter", Material.PURPLE_DYE, 10282, "UUマター", NamedTextColor.LIGHT_PURPLE, Tech.SCIENCE, "物質生成機の産物");
        reg("empty_cell", Material.GLASS_BOTTLE, 10283, "空のセル", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("uranium_cell", Material.SPLASH_POTION, 10284, "ウランセル", NamedTextColor.GREEN, Tech.SCIENCE, "");
        reg("cooling_cell", Material.POTION, 10285, "冷却セル", NamedTextColor.AQUA, Tech.SCIENCE, "");
        // アップグレード・ツール・装備
        reg("upgrade_overclocker", Material.REDSTONE, 10290, "オーバークロッカー", NamedTextColor.RED, Tech.SCIENCE, "稼働速度向上");
        reg("upgrade_transformer", Material.REDSTONE_TORCH, 10291, "変圧アップグレード", NamedTextColor.RED, Tech.SCIENCE, "");
        reg("upgrade_storage", Material.CHEST, 10292, "蓄電アップグレード", NamedTextColor.RED, Tech.SCIENCE, "");
        reg("jetpack", Material.ELYTRA, 10293, "ジェットパック", NamedTextColor.WHITE, Tech.SCIENCE, "");
        reg("electric_jetpack", Material.ELYTRA, 10294, "電気ジェットパック", NamedTextColor.AQUA, Tech.SCIENCE, "");
        reg("nano_suit", Material.CHAINMAIL_CHESTPLATE, 10295, "ナノスーツ", NamedTextColor.DARK_GRAY, Tech.SCIENCE, "");
        reg("quantum_suit", Material.NETHERITE_CHESTPLATE, 10296, "量子スーツ", NamedTextColor.DARK_PURPLE, Tech.SCIENCE, "最上位防具");
        reg("chain_saw", Material.IRON_AXE, 10297, "チェーンソー", NamedTextColor.GRAY, Tech.SCIENCE, "高速伐採");
        reg("mining_drill", Material.IRON_PICKAXE, 10298, "採掘ドリル", NamedTextColor.GRAY, Tech.SCIENCE, "高速採掘");
        reg("diamond_drill", Material.DIAMOND_PICKAXE, 10299, "ダイヤドリル", NamedTextColor.AQUA, Tech.SCIENCE, "最速採掘");
        reg("treetap", Material.STICK, 10300, "樹液採取具", NamedTextColor.GOLD, Tech.SCIENCE, "ゴムの木から樹液採取");
        reg("electric_treetap", Material.BLAZE_ROD, 10301, "電動樹液採取具", NamedTextColor.YELLOW, Tech.SCIENCE, "");
        reg("nano_saber", Material.DIAMOND_SWORD, 10302, "ナノセイバー", NamedTextColor.AQUA, Tech.SCIENCE, "高出力光刃");

        // --- 魔法系 ---
        reg("spellbook", Material.BOOK, 10050, "呪文書", NamedTextColor.DARK_PURPLE, Tech.MAGIC,
                "使用で詠唱開始", "Mage 専用");
        reg("wand", Material.BLAZE_ROD, 10051, "ワンド", NamedTextColor.LIGHT_PURPLE, Tech.MAGIC,
                "詠唱補助杖");
        reg("mana_crystal", Material.PRISMARINE_SHARD, 10052, "マナクリスタル",
                NamedTextColor.DARK_AQUA, Tech.MAGIC, "MP回復アイテム");
        reg("grimoire_meteor", Material.BOOK, 10053, "魔導書: メテオ", NamedTextColor.RED, Tech.MAGIC,
                "メテオストライクを解放");
        reg("grimoire_frost", Material.BOOK, 10054, "魔導書: フロスト", NamedTextColor.AQUA, Tech.MAGIC,
                "フロストノヴァを解放");

        // --- 粉砕機の粉 ---
        reg("dust_iron", Material.GUNPOWDER, 10070, "鉄粉", NamedTextColor.GRAY, Tech.SCIENCE,
                "かまどで精錬 → 鉄インゴット");
        reg("dust_gold", Material.GLOWSTONE_DUST, 10071, "金粉", NamedTextColor.GOLD, Tech.SCIENCE,
                "かまどで精錬 → 金インゴット");
        reg("dust_copper", Material.REDSTONE, 10072, "銅粉", NamedTextColor.GOLD, Tech.SCIENCE,
                "かまどで精錬 → 銅インゴット");

        // --- その他 ---
        reg("void_core", Material.HEART_OF_THE_SEA, 10060, "ヴォイドコア", NamedTextColor.DARK_PURPLE,
                Tech.NONE, "ボスドロップ素材");
        reg("coin", Material.GOLD_NUGGET, 10061, "DLコイン", NamedTextColor.GOLD, Tech.NONE,
                "通貨アイテム");
    }

    /** 粉 → インゴットの精錬レシピを登録（ExactChoice でカスタムアイテムを識別） */
    public void registerRecipes() {
        smelt("dust_iron", Material.IRON_INGOT);
        smelt("dust_gold", Material.GOLD_INGOT);
        smelt("dust_copper", Material.COPPER_INGOT);
    }

    private void smelt(String dustId, Material ingot) {
        ItemStack dust = create(dustId);
        if (dust == null) return;
        var key = plugin.key("smelt_" + dustId);
        var recipe = new org.bukkit.inventory.FurnaceRecipe(key,
                new ItemStack(ingot),
                new org.bukkit.inventory.RecipeChoice.ExactChoice(dust), 0.7f, 100);
        plugin.getServer().addRecipe(recipe);
    }

    /** アイテムIDからItemStack生成 */
    public ItemStack create(String id) {
        return create(id, 1);
    }

    public ItemStack create(String id, int amount) {
        ItemDef def = defs.get(id);
        if (def == null) return null;
        ItemStack stack = new ItemStack(def.base(), amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(def.displayName(), def.color())
                .decoration(TextDecoration.ITALIC, false));
        meta.setCustomModelData(def.modelData());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (!def.lore().isEmpty()) {
            meta.lore(def.lore().stream()
                    .map(s -> Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList());
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, def.id());
        if (def.tech() != Tech.NONE) {
            meta.getPersistentDataContainer().set(techKey, PersistentDataType.STRING,
                    def.tech().name().toLowerCase());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** ItemStack のカスタムアイテムID (なければ null) */
    public String itemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    /** 技術陣営タグ (NONE/SCIENCE/MAGIC) */
    public Tech techOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Tech.NONE;
        String t = stack.getItemMeta().getPersistentDataContainer().get(techKey, PersistentDataType.STRING);
        if (t == null) return Tech.NONE;
        return "science".equals(t) ? Tech.SCIENCE : "magic".equals(t) ? Tech.MAGIC : Tech.NONE;
    }

    public ItemDef def(String id) { return defs.get(id); }
    public Map<String, ItemDef> defs() { return defs; }
}
