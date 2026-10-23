#!/usr/bin/env python3
"""
NeoBuilder リソースパック生成スクリプト.
- ItemRegistry に登録された全カスタムアイテムのモデル/テクスチャを生成
- マシン・パイプの設置ブロックは blockstates override でカスタムモデル化
- テクスチャは 16x16 PNG を手続き的に生成（stdlib のみ: zlib+struct）
出力: dist/NeoBuilderResourcePack.zip
使い方: python3 gen_pack.py <出力zipパス> [作業ディレクトリ]
"""
import json, os, shutil, struct, sys, zipfile, zlib

# ---------- テクスチャ定義（簡易パレット） ----------
# id -> (base_color, accent_color, パターン種別)
PALETTES = {
    # エンジン
    "engine_redstone":   ((180, 40, 40),  (240, 120, 120), "engine"),
    "engine_stirling":   ((150, 110, 60), (220, 180, 90),  "engine"),
    "engine_combustion": ((110, 40, 30),  (230, 80, 40),   "engine"),
    "engine_nuclear":    ((60, 40, 100),  (120, 255, 120), "engine"),
    # マシン
    "quarry":            ((60, 90, 140),  (255, 200, 60),  "machine"),
    "filler":            ((60, 140, 90),  (160, 255, 160), "machine"),
    "macerator":         ((140, 130, 60), (255, 255, 120), "machine"),
    "landmark":          ((200, 180, 60), (255, 255, 200), "torch"),
    # パイプ
    "pipe_transport":    ((200, 200, 210),(120, 120, 130), "pipe"),
    "pipe_extract":      ((150, 100, 50), (220, 170, 100), "pipe"),
    "pipe_gold":         ((230, 190, 60), (255, 240, 140), "pipe"),
    "pipe_diamond":      ((90, 210, 220), (200, 255, 255), "pipe"),
    "pipe_obsidian":     ((40, 30, 60),   (110, 80, 160),  "pipe"),
    "pipe_teleport":     ((140, 60, 180), (240, 160, 255), "pipe"),
    "pipe_fluid":        ((60, 120, 220), (140, 200, 255), "pipe"),
    "pipe_kinesis":      ((240, 220, 80), (255, 255, 180), "pipe"),
    "pipe_logistics":    ((90, 200, 90),  (180, 255, 180), "pipe"),
    "pipe_provider":     ((220, 80, 80),  (255, 160, 160), "pipe"),
    "pipe_request":      ((90, 170, 230), (180, 230, 255), "pipe"),
    "remote_orderer":    ((120, 160, 220),(200, 230, 255), "item"),
    # 魔法
    "spellbook":         ((110, 60, 160), (220, 170, 255), "book"),
    "wand":              ((200, 160, 90), (255, 230, 140), "wand"),
    "mana_crystal":      ((80, 200, 220), (200, 255, 255), "gem"),
    "grimoire_meteor":   ((160, 40, 30),  (255, 140, 60),  "book"),
    "grimoire_frost":    ((60, 140, 200), (200, 240, 255), "book"),
    # 粉・その他
    "dust_iron":         ((180, 180, 175),(230, 230, 225), "dust"),
    "dust_gold":         ((230, 200, 90), (255, 245, 170), "dust"),
    "dust_copper":       ((200, 120, 80), (255, 180, 140), "dust"),
    "void_core":         ((40, 20, 60),   (160, 80, 240),  "gem"),
    "coin":              ((230, 190, 70), (255, 245, 180), "coin"),
    "wrench":            ((140, 140, 150),(210, 210, 220), "tool"),
}

# アイテムID -> (ベース素材, custom_model_data)
# ItemRegistry の定義と一致させること
ITEMS = {
    "wrench":            ("amethyst_shard", 10001),
    "engine_redstone":   ("redstone_lamp", 10010),
    "engine_stirling":   ("smoker", 10011),
    "engine_combustion": ("blast_furnace", 10012),
    "engine_nuclear":    ("crying_obsidian", 10013),
    "quarry":            ("lodestone", 10020),
    "filler":            ("respawn_anchor", 10021),
    "macerator":         ("grindstone", 10022),
    "landmark":          ("torch", 10023),
    "pipe_transport":    ("glass", 10030),
    "pipe_extract":      ("oak_fence", 10031),
    "pipe_gold":         ("glowstone", 10032),
    "pipe_diamond":      ("sea_lantern", 10033),
    "pipe_obsidian":     ("obsidian", 10034),
    "pipe_teleport":     ("ender_chest", 10035),
    "pipe_fluid":        ("blue_stained_glass", 10036),
    "pipe_kinesis":      ("yellow_stained_glass", 10037),
    "pipe_logistics":    ("lime_stained_glass", 10040),
    "pipe_provider":     ("red_stained_glass", 10041),
    "pipe_request":      ("light_blue_stained_glass", 10042),
    "remote_orderer":    ("compass", 10043),
    "spellbook":         ("book", 10050),
    "wand":              ("blaze_rod", 10051),
    "mana_crystal":      ("prismarine_shard", 10052),
    "grimoire_meteor":   ("book", 10053),
    "grimoire_frost":    ("book", 10054),
    "void_core":         ("heart_of_the_sea", 10060),
    "coin":              ("gold_nugget", 10061),
    "dust_iron":         ("gunpowder", 10070),
    "dust_gold":         ("glowstone_dust", 10071),
    "dust_copper":       ("redstone", 10072),
    "pipe_supplier":     ("orange_stained_glass", 10044),
    "pipe_crafting":     ("cyan_stained_glass", 10045),
    "pipe_satellite":    ("magenta_stained_glass", 10046),
    "pipe_stone":        ("stone", 10100),
    "pipe_sandstone":    ("sandstone", 10101),
    "pipe_iron":         ("iron_block", 10102),
    "pipe_emerald":      ("emerald_block", 10103),
    "pipe_void":         ("black_stained_glass", 10104),
    "pipe_distribution": ("brown_stained_glass", 10105),
    "pipe_teleport_fluid": ("purple_stained_glass", 10106),
    "pipe_teleport_structure": ("gray_stained_glass", 10107),
    "pipe_fluid_cobble": ("prismarine_bricks", 10108),
    "pipe_fluid_stone":  ("andesite", 10109),
    "pipe_fluid_sand":   ("cut_sandstone", 10110),
    "pipe_fluid_iron":   ("quartz_block", 10111),
    "pipe_fluid_gold":   ("shroomlight", 10112),
    "pipe_fluid_emerald":("prismarine", 10113),
    "pipe_fluid_void":   ("black_concrete", 10114),
    "pipe_fluid_diamond":("diamond_block", 10115),
    "gear_wood":         ("stick", 10120),
    "gear_stone":        ("flint", 10121),
    "gear_iron":         ("iron_nugget", 10122),
    "gear_gold":         ("gold_nugget", 10123),
    "gear_diamond":      ("diamond", 10124),
    "pipe_sealant":      ("slime_ball", 10125),
    "pipe_plug":         ("iron_bars", 10126),
    "oil_bucket":        ("lava_bucket", 10127),
    "fuel_bucket":       ("bucket", 10128),
    "teleport_tether":   ("end_rod", 10129),
    "auto_workbench":    ("crafting_table", 10140),
    "tank":              ("hay_block", 10141),
    "pump":              ("piston", 10142),
    "refinery":          ("smithing_table", 10143),
    "generator":         ("furnace", 10200),
    "solar_panel":       ("daylight_detector", 10201),
    "nuclear_reactor":   ("netherite_block", 10202),
    "reactor_chamber":   ("iron_block", 10203),
    "cable_copper":      ("orange_wool", 10210),
    "cable_gold":        ("yellow_wool", 10211),
    "cable_gold_ins2":   ("yellow_carpet", 10212),
    "cable_hv":          ("iron_trapdoor", 10213),
    "cable_hv_ins2":     ("iron_door", 10214),
    "cable_hv_ins4":     ("iron_bars", 10215),
    "cable_glass":       ("glass_pane", 10216),
    "transformer_lv":    ("polished_andesite", 10220),
    "transformer_mv":    ("polished_diorite", 10221),
    "transformer_hv":    ("polished_granite", 10222),
    "batbox":            ("stripped_spruce_log", 10230),
    "mfe":               ("stripped_oak_log", 10231),
    "mfsu":              ("stripped_dark_oak_log", 10232),
    "re_battery":        ("redstone", 10233),
    "energy_crystal":    ("diamond", 10234),
    "batpack":           ("leather_chestplate", 10235),
    "lappack":           ("iron_chestplate", 10236),
    "lapotron_crystal":  ("emerald", 10237),
    "iron_furnace":      ("furnace", 10240),
    "electric_furnace":  ("furnace", 10241),
    "induction_furnace": ("furnace", 10242),
    "compressor":        ("piston", 10243),
    "extractor":         ("hopper", 10244),
    "mass_fabricator":   ("ancient_debris", 10245),
    "machine_block":     ("iron_block", 10246),
    "advanced_machine":  ("heavy_weighted_pressure_plate", 10247),
    "tesla_coil":        ("lightning_rod", 10248),
    "reinforced_glass":  ("glass", 10249),
    "reinforced_stone":  ("stone", 10250),
    "reinforced_door":   ("iron_door", 10251),
    "teleporter":        ("beacon", 10252),
    "refined_iron":      ("iron_ingot", 10260),
    "uranium":           ("emerald_ore", 10261),
    "refined_uranium":   ("emerald", 10262),
    "advanced_alloy":    ("iron_ingot", 10263),
    "dense_copper_plate":("copper_ingot", 10264),
    "tin_ingot":         ("iron_ingot", 10265),
    "dust_tin":          ("sugar", 10266),
    "bronze_ingot":      ("copper_ingot", 10267),
    "dust_bronze":       ("glowstone_dust", 10268),
    "mixed_metal_ingot": ("netherite_ingot", 10269),
    "iridium_plate":     ("netherite_scrap", 10270),
    "iridium_ore":       ("diamond_ore", 10271),
    "raw_carbon_fibre":  ("string", 10272),
    "raw_carbon_mesh":   ("cobweb", 10273),
    "carbon_plate":      ("netherite_scrap", 10274),
    "coal_ball":         ("coal", 10275),
    "compressed_coal_ball":("coal_block", 10276),
    "coal_chunk":        ("flint", 10277),
    "rubber_wood":       ("jungle_log", 10278),
    "rubber":            ("slime_ball", 10279),
    "circuit":           ("repeater", 10280),
    "advanced_circuit":  ("comparator", 10281),
    "matter":            ("purple_dye", 10282),
    "empty_cell":        ("glass_bottle", 10283),
    "uranium_cell":      ("splash_potion", 10284),
    "cooling_cell":      ("potion", 10285),
    "upgrade_overclocker":("redstone", 10290),
    "upgrade_transformer":("redstone_torch", 10291),
    "upgrade_storage":   ("chest", 10292),
    "jetpack":           ("elytra", 10293),
    "electric_jetpack":  ("elytra", 10294),
    "nano_suit":         ("chainmail_chestplate", 10295),
    "quantum_suit":      ("netherite_chestplate", 10296),
    "chain_saw":         ("iron_axe", 10297),
    "mining_drill":      ("iron_pickaxe", 10298),
    "diamond_drill":     ("diamond_pickaxe", 10299),
    "treetap":           ("stick", 10300),
    "electric_treetap":  ("blaze_rod", 10301),
    "nano_saber":        ("diamond_sword", 10302),
}

# ブロック設置アイテム -> オーバーライド対象ブロック + バリアントキー
# kind: ""=単一バリアント, "facing"=4向き, "lit_facing"=lit×facing, "multipart"=パーツ合成
BLOCKS = {
    "engine_redstone":   ("redstone_lamp", "lit"),
    "engine_stirling":   ("smoker", "lit_facing"),
    "engine_combustion": ("blast_furnace", "lit_facing"),
    "engine_nuclear":    ("crying_obsidian", ""),
    "quarry":            ("lodestone", ""),
    "filler":            ("respawn_anchor", "charges"),
    "macerator":         ("grindstone", "face_facing"),
    "landmark":          ("torch", ""),
    # IC2/BC系マシン: 一意なベースブロックへ割当済み（ItemRegistry側で材質変更）
    "tank":              ("hay_block", "multipart"),
    "refinery":          ("smithing_table", ""),
    "pump":              ("magma_block", ""),
    "auto_workbench":    ("crafting_table", ""),
    "generator":         ("target", ""),
    "compressor":        ("moss_block", ""),
    "extractor":         ("dripstone_block", ""),
    "machine_block":     ("raw_iron_block", ""),
    "reactor_chamber":   ("honeycomb_block", ""),
    "nuclear_reactor":   ("netherite_block", ""),
    "mass_fabricator":   ("ancient_debris", ""),
    "teleporter":        ("beacon", ""),
    "solar_panel":       ("daylight_detector", "multipart"),
    "advanced_machine":  ("heavy_weighted_pressure_plate", "multipart"),
    "tesla_coil":        ("lightning_rod", "multipart"),
    "batbox":            ("stripped_spruce_log", "multipart"),
    "mfe":               ("stripped_oak_log", "multipart"),
    "mfsu":              ("stripped_dark_oak_log", "multipart"),
}

# パイプの見た目: 6方向faceプロパティを持つ安定ブロックをホストにし、
# multipart blockstate で 中心キューブ+方向別腕 を条件適用する.
# サーバ側（PipeManager）が隣接状況からfaceフラグを計算してBlockDataを設定する.
# （PIPE_FAMILIES の実体は URL定数定義後にある）

# ---------- オリジナルMODテクスチャの取得元 ----------
# 個人使用前提: 各MODリポジトリからテクスチャをダウンロードして流用する
BC = "https://raw.githubusercontent.com/BuildCraft/BuildCraft/8.0.x-1.12.2/buildcraft_resources/assets"
LP = "https://raw.githubusercontent.com/rs485/LogisticsPipes/dev/resources/assets/logisticspipes/textures"
QP = "https://raw.githubusercontent.com/Kotori316/QuarryPlus/26.3/common/src/main/resources/assets/quarryplus/textures"

WZ = "https://raw.githubusercontent.com/ZsoltMolnarrr/Wizards/1.21.1/common/src/main/resources/assets/wizards/textures"
MEK = "https://raw.githubusercontent.com/mekanism/Mekanism/26.3/src/main/resources/assets/mekanism/textures"
BOT = "https://raw.githubusercontent.com/VazkiiMods/Botania/1.20.x/Xplat/src/main/resources/assets/botania/textures"
MW = f"{BC}/buildcraftfactory/textures/blocks/mining_well"
MCA = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.4/assets/minecraft/textures"
MEKT = "https://raw.githubusercontent.com/mekanism/Mekanism-Tools/main/src/main/resources/assets/mekanismtools/textures"

# パイプ種別 -> (壁ホストブロック, waterloggedフラグ, ベース色, アクセント色)
# wallブロックは N/E/S/W=none|low|tall + up + waterlogged の状態を持ち、
# サーバ側が隣接状況から設定する。waterloggedで1壁につき2種のパイプを保持。
PIPE_HOSTS = {
    "pipe_transport": ("cobblestone_wall", False, (165, 165, 170), (115, 115, 120)),
    "pipe_stone":     ("stone_brick_wall", False, (150, 150, 155), (105, 105, 110)),
    "pipe_sandstone": ("sandstone_wall", False, (222, 206, 160), (190, 175, 128)),
    "pipe_iron":      ("diorite_wall", False, (205, 205, 210), (170, 170, 178)),
    "pipe_gold":      ("granite_wall", False, (242, 212, 90), (205, 170, 60)),
    "pipe_diamond":   ("prismarine_wall", False, (120, 215, 225), (80, 180, 195)),
    "pipe_obsidian":  ("blackstone_wall", False, (55, 45, 70), (30, 25, 45)),
    "pipe_emerald":   ("mossy_cobblestone_wall", False, (70, 205, 125), (45, 160, 90)),
    "pipe_void":      ("polished_blackstone_wall", False, (35, 30, 45), (20, 18, 30)),
    "pipe_extract":   ("andesite_wall", False, (150, 105, 55), (115, 80, 40)),
    "pipe_distribution": ("mud_brick_wall", False, (205, 175, 115), (170, 140, 90)),
    "pipe_teleport":  ("end_stone_brick_wall", False, (175, 95, 225), (130, 60, 180)),
    "pipe_teleport_structure": ("deepslate_tile_wall", False, (120, 70, 170), (90, 50, 135)),
    "pipe_logistics": ("brick_wall", False, (150, 225, 150), (110, 190, 110)),
    "pipe_provider":  ("nether_brick_wall", False, (235, 160, 70), (195, 125, 45)),
    "pipe_request":   ("red_nether_brick_wall", False, (95, 175, 235), (60, 140, 195)),
    "pipe_supplier":  ("mossy_stone_brick_wall", False, (185, 125, 55), (150, 100, 40)),
    "pipe_crafting":  ("polished_deepslate_wall", False, (85, 200, 210), (55, 165, 175)),
    "pipe_satellite": ("deepslate_brick_wall", False, (85, 85, 95), (60, 60, 70)),
    "pipe_fluid":          ("cobblestone_wall", True, (95, 155, 235), (60, 115, 195)),
    "pipe_fluid_cobble":   ("stone_brick_wall", True, (110, 140, 190), (80, 105, 150)),
    "pipe_fluid_stone":    ("sandstone_wall", True, (225, 195, 140), (190, 160, 110)),
    "pipe_fluid_sand":     ("prismarine_wall", True, (235, 210, 165), (200, 175, 130)),
    "pipe_fluid_iron":     ("diorite_wall", True, (180, 195, 215), (145, 160, 180)),
    "pipe_fluid_gold":     ("granite_wall", True, (240, 195, 90), (205, 160, 55)),
    "pipe_fluid_emerald":  ("mossy_cobblestone_wall", True, (95, 200, 140), (65, 165, 105)),
    "pipe_fluid_void":     ("blackstone_wall", True, (45, 40, 60), (25, 20, 38)),
    "pipe_teleport_fluid": ("end_stone_brick_wall", True, (150, 110, 225), (115, 80, 185)),
    "pipe_kinesis":     ("tuff_wall", False, (250, 220, 90), (215, 180, 55)),
    "cable_copper":     ("brick_wall", True, (205, 120, 65), (170, 95, 45)),
    "cable_gold":       ("mud_brick_wall", True, (240, 205, 80), (200, 165, 50)),
    "cable_gold_ins2":  ("nether_brick_wall", True, (200, 170, 70), (165, 135, 45)),
    "cable_hv":         ("red_nether_brick_wall", True, (160, 160, 170), (125, 125, 135)),
    "cable_hv_ins2":    ("mossy_stone_brick_wall", True, (135, 135, 145), (105, 105, 115)),
    "cable_hv_ins4":    ("polished_deepslate_wall", True, (110, 110, 120), (85, 85, 95)),
    "cable_glass":      ("andesite_wall", True, (195, 230, 240), (160, 205, 220)),
    "transformer_lv":   ("red_sandstone_wall", False, (190, 160, 110), (155, 130, 85)),
    "transformer_mv":   ("cobbled_deepslate_wall", False, (145, 145, 155), (115, 115, 125)),
    "transformer_hv":   ("tuff_brick_wall", False, (120, 120, 130), (95, 95, 105)),
}

# パイプ種別 -> BuildCraftの実パイプテクスチャ名（透明+暗フレーム。色下地に合成して使う）
BC_PIPE_TEX = {
    "pipe_transport": "cobblestone_item", "pipe_stone": "stone_item",
    "pipe_sandstone": "sandstone_item", "pipe_iron": "iron_item_filled",
    "pipe_gold": "gold_item", "pipe_diamond": "diamond_item",
    "pipe_obsidian": "obsidian_item", "pipe_emerald": "emzuli_item_filled",
    "pipe_void": "void_item", "pipe_extract": "wood_item_filled",
    "pipe_distribution": "stripes_item", "pipe_teleport": "lapis_item_purple",
    "pipe_teleport_structure": "quartz_item",
    "pipe_logistics": "daizuli_item_filled", "pipe_provider": "lapis_item_orange",
    "pipe_request": "daizuli_item_filled", "pipe_supplier": "sandstone_item",
    "pipe_crafting": "quartz_item", "pipe_satellite": "void_item",
    "pipe_fluid": "diorite_fluid", "pipe_fluid_cobble": "cobblestone_fluid",
    "pipe_fluid_stone": "stone_fluid", "pipe_fluid_sand": "sandstone_fluid",
    "pipe_fluid_iron": "iron_fluid_filled", "pipe_fluid_gold": "gold_fluid",
    "pipe_fluid_emerald": "diamond_fluid", "pipe_fluid_void": "void_fluid",
    "pipe_teleport_fluid": "quartz_fluid",
    "pipe_kinesis": "gold_power",
    "cable_copper": "cobblestone_power", "cable_gold": "gold_power",
    "cable_gold_ins2": "gold_power", "cable_hv": "iron_power_filled",
    "cable_hv_ins2": "iron_power_filled", "cable_hv_ins4": "diamond_power",
    "cable_glass": "quartz_power",
    "transformer_lv": "sandstone_power", "transformer_mv": "stone_power",
    "transformer_hv": "diamond_power",
}

# item名 -> 取得元URL（item texturesに配置）
TEX_ITEMS = {
    "wrench":          f"{BC}/buildcraftcore/textures/items/wrench.png",
    "remote_orderer":  f"{LP}/blocks/pipes/new_texture/remote_orderer.png",
    # RPG Series (Wizards): 呪文書・杖
    "spellbook":       f"{WZ}/item/spell_book/arcane.png",
    "grimoire_meteor": f"{WZ}/item/spell_book/fire.png",
    "grimoire_frost":  f"{WZ}/item/spell_book/frost.png",
    "wand":            f"{WZ}/item/wand_arcane.png",
    # Mekanism: 粉
    "dust_iron":       f"{MEK}/item/dust_iron.png",
    "dust_gold":       f"{MEK}/item/dust_gold.png",
    "dust_copper":     f"{MEK}/item/dust_copper.png",
    # Botania: マナ
    "mana_crystal":    f"{BOT}/item/mana_diamond.png",
    # IC2系素材/部品: Mekanismの実テクスチャを流用
    "dust_tin":            f"{MEK}/item/dust_tin.png",
    "dust_bronze":         f"{MEK}/item/dust_bronze.png",
    "uranium":             f"{MEK}/item/ingot_uranium.png",
    "refined_uranium":     f"{MEK}/item/crystal_uranium.png",
    "uranium_cell":        f"{MEK}/item/dirty_dust_uranium.png",
    "tin_ingot":           f"{MEK}/item/ingot_tin.png",
    "bronze_ingot":        f"{MEK}/item/ingot_bronze.png",
    "refined_iron":        f"{MEK}/item/ingot_steel.png",
    "advanced_alloy":      f"{MEK}/item/alloy_reinforced.png",
    "mixed_metal_ingot":   f"{MEK}/item/ingot_refined_obsidian.png",
    "iridium_plate":       f"{MEK}/item/alloy_atomic.png",
    "dense_copper_plate":  f"{MEK}/item/alloy_infused.png",
    "raw_carbon_fibre":    f"{MEK}/item/dust_charcoal.png",
    "raw_carbon_mesh":     f"{MEK}/item/dust_obsidian.png",
    "carbon_plate":        f"{MEK}/item/ingot_refined_glowstone.png",
    "coal_ball":           f"{MEK}/item/dust_coal.png",
    "compressed_coal_ball":f"{MEK}/item/dust_charcoal.png",
    "coal_chunk":          f"{MEK}/item/dust_coal.png",
    "rubber":              f"{MEK}/item/hdpe_pellet.png",
    "circuit":             f"{MEK}/item/basic_control_circuit.png",
    "advanced_circuit":    f"{MEK}/item/elite_control_circuit.png",
    "re_battery":          f"{MEK}/item/energy_tablet.png",
    "energy_crystal":      f"{MEK}/item/crystal.png",
    "lapotron_crystal":    f"{MEK}/item/crystal_osmium.png",
    "cooling_cell":        f"{MEK}/item/crystal_lead.png",
    "matter":              f"{MEK}/item/crystal_gold.png",
    "jetpack":             f"{MEK}/item/jetpack.png",
    "electric_jetpack":    f"{MEK}/item/jetpack.png",
    "upgrade_overclocker": f"{MEK}/item/module_base.png",
    "upgrade_transformer": f"{MEK}/item/module_base.png",
    "upgrade_storage":     f"{MEK}/item/module_base.png",

    # 残り全アイテムを源流MOD/実在テクスチャへ（取得失敗時は生成テクスチャへFB）
    "chain_saw":         f"{MEK}/item/atomic_disassembler.png",
    "mining_drill":      f"{MEK}/item/atomic_disassembler.png",
    "diamond_drill":     f"{MEK}/item/atomic_disassembler.png",
    "nano_saber":        f"{MEK}/item/meka_tool.png",
    "nano_suit":         f"{MEK}/item/mekasuit_bodyarmor.png",
    "quantum_suit":      f"{MEK}/item/mekasuit_pants.png",
    "batpack":           f"{MEK}/item/energy_tablet.png",
    "lappack":           f"{MEK}/item/energy_tablet.png",
    "treetap":           f"{MEK}/item/hdpe_stick.png",
    "electric_treetap":  f"{MEK}/item/electric_bow.png",
    "empty_cell":        f"{MEK}/item/canteen.png",
    "fuel_bucket":       f"{MCA}/item/lava_bucket.png",
    "oil_bucket":        f"{MCA}/item/bucket.png",
    "iridium_ore":       f"{MEK}/block/block_raw_osmium.png",
    "rubber_wood":       f"{MCA}/block/spruce_log.png",
    "reinforced_glass":  f"{MCA}/block/glass.png",
    "reinforced_stone":  f"{MCA}/block/stone.png",
    "reinforced_door":   f"{MCA}/item/iron_door.png",
    "teleport_tether":   f"{MEK}/item/portable_teleporter.png",
    "remote_orderer":    f"{MEK}/item/configuration_card.png",
    "coin":              f"{MCA}/item/gold_nugget.png",
    "void_core":         f"{BOT}/item/black_lotus.png",
    "iron_furnace":      f"{MCA}/block/furnace.png",
    "electric_furnace":  f"{MCA}/block/blast_furnace.png",
    "induction_furnace": f"{MCA}/block/smoker.png",
    "transformer_lv":    f"{LP}/pipes/ls_power_electricity.png",
    "transformer_mv":    f"{LP}/pipes/ls_power_electricity.png",
    "transformer_hv":    f"{LP}/pipes/ls_power_electricity.png",
    # BC ギア
    "gear_wood":       f"{BC}/buildcraftcore/textures/items/gear_wood.png",
    "gear_stone":      f"{BC}/buildcraftcore/textures/items/gear_stone.png",
    "gear_iron":       f"{BC}/buildcraftcore/textures/items/gear_iron.png",
    "gear_gold":       f"{BC}/buildcraftcore/textures/items/gear_gold.png",
    "gear_diamond":    f"{BC}/buildcraftcore/textures/items/gear_diamond.png",
    "pipe_sealant":    f"{BC}/buildcrafttransport/textures/items/pipewaterproof.png",
    # BC 輸送パイプ系アイコン（pipes/*.png はアイテムアイコン兼用の公式テクスチャ）
    "pipe_transport":        f"{BC}/buildcrafttransport/textures/pipes/cobblestone_item.png",
    "pipe_extract":          f"{BC}/buildcrafttransport/textures/pipes/wood_item_filled.png",
    "pipe_gold":             f"{BC}/buildcrafttransport/textures/pipes/gold_item.png",
    "pipe_diamond":          f"{BC}/buildcrafttransport/textures/pipes/diamond_item.png",
    "pipe_obsidian":         f"{BC}/buildcrafttransport/textures/pipes/obsidian_item.png",
    "pipe_stone":            f"{BC}/buildcrafttransport/textures/pipes/stone_item.png",
    "pipe_sandstone":        f"{BC}/buildcrafttransport/textures/pipes/sandstone_item.png",
    "pipe_iron":             f"{BC}/buildcrafttransport/textures/pipes/iron_item_filled.png",
    "pipe_emerald":          f"{BC}/buildcrafttransport/textures/pipes/emzuli_item_filled.png",
    "pipe_void":             f"{BC}/buildcrafttransport/textures/pipes/void_item.png",
    "pipe_distribution":     f"{BC}/buildcrafttransport/textures/pipes/diamond_item.png",
    "pipe_teleport":         f"{BC}/buildcrafttransport/textures/pipes/quartz_item.png",
    "pipe_teleport_structure": f"{BC}/buildcrafttransport/textures/pipes/stripes_item.png",
    "pipe_teleport_fluid":   f"{BC}/buildcrafttransport/textures/pipes/quartz_fluid.png",
    # BC 流体パイプ系アイコン
    "pipe_fluid":            f"{BC}/buildcrafttransport/textures/pipes/cobblestone_fluid.png",
    "pipe_fluid_cobble":     f"{BC}/buildcrafttransport/textures/pipes/cobblestone_fluid.png",
    "pipe_fluid_stone":      f"{BC}/buildcrafttransport/textures/pipes/stone_fluid.png",
    "pipe_fluid_sand":       f"{BC}/buildcrafttransport/textures/pipes/sandstone_fluid.png",
    "pipe_fluid_iron":       f"{BC}/buildcrafttransport/textures/pipes/diorite_fluid.png",
    "pipe_fluid_gold":       f"{BC}/buildcrafttransport/textures/pipes/gold_fluid.png",
    "pipe_fluid_emerald":    f"{BC}/buildcrafttransport/textures/pipes/diamond_fluid.png",
    "pipe_fluid_void":       f"{BC}/buildcrafttransport/textures/pipes/void_fluid.png",
    # エネルギー系（BC power管テクスチャを流用）
    "pipe_kinesis":          f"{BC}/buildcrafttransport/textures/pipes/gold_power.png",
    "cable_copper":          f"{BC}/buildcrafttransport/textures/pipes/stone_power.png",
    "cable_gold":            f"{BC}/buildcrafttransport/textures/pipes/gold_power.png",
    "cable_gold_ins2":       f"{BC}/buildcrafttransport/textures/pipes/gold_power.png",
    "cable_hv":              f"{BC}/buildcrafttransport/textures/pipes/diamond_power.png",
    "cable_hv_ins2":         f"{BC}/buildcrafttransport/textures/pipes/diamond_power.png",
    "cable_hv_ins4":         f"{BC}/buildcrafttransport/textures/pipes/diamond_power.png",
    "cable_glass":           f"{BC}/buildcrafttransport/textures/pipes/quartz_power.png",
    "transformer_lv":        f"{BC}/buildcrafttransport/textures/pipes/sandstone_power.png",
    "transformer_mv":        f"{BC}/buildcrafttransport/textures/pipes/quartz_power.png",
    "transformer_hv":        f"{BC}/buildcrafttransport/textures/pipes/diamond_power.png",
    # LogisticsPipes
    "pipe_logistics":        f"{LP}/blocks/pipes/new_texture/basic.png",
    "pipe_provider":         f"{LP}/blocks/pipes/new_texture/provider.png",
    "pipe_request":          f"{LP}/blocks/pipes/new_texture/request.png",
    "pipe_supplier":         f"{LP}/blocks/pipes/new_texture/supplier.png",
    "pipe_crafting":         f"{LP}/blocks/pipes/new_texture/crafting.png",
    "pipe_satellite":        f"{LP}/blocks/pipes/new_texture/satellite.png",
}

# block名 -> {"all": url} or {"top":u,"side":u,"front":u,"back":u,"bottom":u}
TEX_BLOCKS = {
    "engine_redstone":   {"front": f"{BC}/buildcraftcore/textures/blocks/engine/wood/back.png",
                          "side":  f"{BC}/buildcraftcore/textures/blocks/engine/wood/side.png"},
    "engine_stirling":   {"front": f"{BC}/buildcraftenergy/textures/blocks/engine/stone/back.png",
                          "side":  f"{BC}/buildcraftenergy/textures/blocks/engine/stone/side.png"},
    "engine_combustion": {"front": f"{BC}/buildcraftenergy/textures/blocks/engine/iron/back.png",
                          "side":  f"{BC}/buildcraftenergy/textures/blocks/engine/iron/side.png"},
    "engine_nuclear":    {"front": f"{BC}/buildcraftenergy/textures/blocks/engine/rf/back.png",
                          "side":  f"{BC}/buildcraftenergy/textures/blocks/engine/rf/side.png"},
    "quarry":   {"front": f"{BC}/buildcraftbuilders/textures/blocks/quarry/normal/front.png",
                 "side":  f"{BC}/buildcraftbuilders/textures/blocks/quarry/normal/side.png",
                 "top":   f"{BC}/buildcraftbuilders/textures/blocks/quarry/normal/top.png",
                 "back":  f"{BC}/buildcraftbuilders/textures/blocks/quarry/normal/back.png",
                 "bottom":f"{BC}/buildcraftbuilders/textures/blocks/quarry/normal/bottom.png"},
    "filler":   {"front": f"{BC}/buildcraftbuilders/textures/blocks/filler/front.png",
                 "side":  f"{BC}/buildcraftbuilders/textures/blocks/filler/side.png",
                 "top":   f"{BC}/buildcraftbuilders/textures/blocks/filler/top.png",
                 "bottom":f"{BC}/buildcraftbuilders/textures/blocks/filler/bottom.png"},
    "landmark": {"all": f"{BC}/buildcraftbuilders/textures/blocks/marker/path.png"},
    "macerator": {"front": f"{QP}/block/mining_well_front.png",
                  "side":  f"{QP}/block/mining_well.png",
                  "top":   f"{QP}/block/mining_well_top.png"},
    # BC factory/builders 実テクスチャ
    "tank":      {"side": f"{BC}/buildcraftfactory/textures/blocks/tank/side.png",
                  "top":  f"{BC}/buildcraftfactory/textures/blocks/tank/end.png",
                  "bottom": f"{BC}/buildcraftfactory/textures/blocks/tank/end.png"},
    "pump":      {"side": f"{BC}/buildcraftfactory/textures/blocks/pump/side.png",
                  "top":  f"{BC}/buildcraftfactory/textures/blocks/pump/top.png",
                  "bottom": f"{BC}/buildcraftfactory/textures/blocks/pump/bottom.png"},
    "refinery":  {"all": f"{BC}/buildcraftfactory/textures/blocks/refinery/refinery.png"},
    "auto_workbench": {"front": f"{BC}/buildcraftbuilders/textures/blocks/builder/front.png",
                       "side":  f"{BC}/buildcraftbuilders/textures/blocks/builder/side.png",
                       "top":   f"{BC}/buildcraftbuilders/textures/blocks/builder/top.png",
                       "back":  f"{BC}/buildcraftbuilders/textures/blocks/builder/back.png"},
    # IC2系マシン: BCのmining_well面別テクスチャで機械らしい見た目に
    "generator":        {"front": f"{MW}/front.png", "side": f"{MW}/side.png",
                         "top": f"{MW}/top.png", "back": f"{MW}/back.png",
                         "bottom": f"{MW}/bottom.png"},
    "compressor":       {"all": f"{MW}/side.png"},
    "extractor":        {"all": f"{MW}/side.png"},
    "machine_block":    {"all": f"{MW}/side.png"},
    "reactor_chamber":  {"all": f"{MW}/side.png"},
    "nuclear_reactor":  {"all": f"{MEK}/block/block_uranium.png"},
    "mass_fabricator":  {"all": f"{MEK}/block/block_refined_obsidian.png"},
    "teleporter":       {"all": f"{MEK}/block/block_refined_glowstone.png"},
    "solar_panel":      {"all": f"{MEK}/block/energy_core.png"},
    "advanced_machine": {"all": f"{MEK}/block/block_refined_obsidian.png"},
    "tesla_coil":       {"all": f"{MEK}/block/block_osmium.png"},
    "batbox":           {"all": f"{MEK}/block/block_bronze.png"},
    "mfe":              {"all": f"{MEK}/block/block_steel.png"},
    "mfsu":             {"all": f"{MEK}/block/block_refined_obsidian.png"},
    # パイプ類: BC transport のパイプテクスチャを全面に適用
    "pipe_transport": {"all": f"{BC}/buildcrafttransport/textures/pipes/cobblestone_item.png"},
    "pipe_extract":   {"all": f"{BC}/buildcrafttransport/textures/pipes/wood_item_filled.png"},
    "pipe_gold":      {"all": f"{BC}/buildcrafttransport/textures/pipes/gold_item.png"},
    "pipe_diamond":   {"all": f"{BC}/buildcrafttransport/textures/pipes/diamond_item.png"},
    "pipe_obsidian":  {"all": f"{BC}/buildcrafttransport/textures/pipes/obsidian_item.png"},
    "pipe_teleport":  {"all": f"{BC}/buildcrafttransport/textures/pipes/quartz_item.png"},
    "pipe_fluid":     {"all": f"{BC}/buildcrafttransport/textures/pipes/gold_fluid.png"},
    "pipe_kinesis":   {"all": f"{BC}/buildcrafttransport/textures/pipes/gold_power.png"},
    "pipe_logistics": {"all": f"{LP}/blocks/pipes/new_texture/basic.png"},
    "pipe_provider":  {"all": f"{LP}/blocks/pipes/new_texture/provider.png"},
    "pipe_request":   {"all": f"{LP}/blocks/pipes/new_texture/request.png"},
}

# ---------- PNG 生成（16x16, zlib only） ----------

def png_bytes(pixels, w=16, h=16):
    """pixels: list[list[(r,g,b,a)]]"""
    raw = b""
    for row in pixels:
        raw += b"\x00" + b"".join(struct.pack("4B", *px) for px in row)
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)  # RGBA
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def gen_texture(base, accent, pattern):
    """16x16 の簡易テクスチャを生成"""
    px = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            # 縦方向の簡易グラデーション
            c = lerp(accent, base, y / 15.0)
            a = 255
            if pattern == "mesh":
                # BC風: 斜め網目ライン + 縁を暗く
                if x in (0, 15) or y in (0, 15):
                    c = lerp(c, (0, 0, 0), 0.5)
                elif (x + y) % 4 == 0 or (x - y) % 4 == 0:
                    c = lerp(c, accent, 0.55)
                elif (x + y) % 4 == 2 or (x - y) % 4 == 2:
                    c = lerp(c, accent, 0.2)
            elif pattern == "pipe":
                # 中央の帯
                if x in (0, 15) or y in (0, 15):
                    c = lerp(c, (0, 0, 0), 0.4)
                if 5 <= x <= 10 and 5 <= y <= 10:
                    c = lerp(c, accent, 0.35)
            elif pattern in ("engine", "machine"):
                if x in (0, 15) or y in (0, 15):
                    c = lerp(c, (0, 0, 0), 0.45)
                if (x + y) % 4 == 0:
                    c = lerp(c, (0, 0, 0), 0.15)
                if 6 <= x <= 9 and 6 <= y <= 9:
                    c = accent  # 中央ランプ
            elif pattern == "book":
                if x in (0, 15) or y in (0, 15):
                    c = lerp(c, (0, 0, 0), 0.5)
                if 3 <= x <= 4:
                    c = lerp(c, (0, 0, 0), 0.3)  # 背表紙
            elif pattern == "wand":
                c = base if abs(x - y) < 2 else (base[0], base[1], base[2], 0)
                if abs(x - y) < 2 and x > 11:
                    c = accent
                if not abs(x - y) < 2:
                    c = (0, 0, 0, 0)
            elif pattern == "gem":
                d = abs(x - 8) + abs(y - 8)
                if d > 7:
                    c = (0, 0, 0, 0)
                elif d < 3:
                    c = accent
            elif pattern == "coin":
                d2 = (x - 8) ** 2 + (y - 8) ** 2
                if d2 > 56:
                    c = (0, 0, 0, 0)
                elif d2 < 16:
                    c = accent
            elif pattern == "dust":
                if (x * 7 + y * 13) % 5 < 3:
                    c = lerp(base, accent, ((x + y) % 5) / 5.0)
                else:
                    c = (0, 0, 0, 0)
            elif pattern == "torch":
                if y < 6 and 6 <= x <= 9:
                    c = accent
                elif 6 <= x <= 9:
                    c = base
                else:
                    c = (0, 0, 0, 0)
            elif pattern == "tool":
                if abs(x - y) < 3 or abs(x + y - 15) < 2 and y < 6:
                    c = base if abs(x - y) < 2 else accent
                else:
                    c = (0, 0, 0, 0)
            else:  # item/default
                if x in (0, 15) or y in (0, 15):
                    c = lerp(c, (0, 0, 0), 0.4)
            px[y][x] = (*c[:3], 255 if len(c) < 4 or c[3] != 0 else 0)
    return px

# ---------- JSON 生成 ----------

def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content if isinstance(content, str) else json.dumps(content, indent=2))

def item_model(name):
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"neobuilder:item/{name}"}
    }

def block_model(name, faces=None):
    """faces: {"top","side","front","back","bottom"} -> texture id (None=side) """
    t = {"particle": f"neobuilder:block/{name}_front" if faces and "front" in faces
         else f"neobuilder:block/{name}_side" if faces and "side" in faces
         else f"neobuilder:block/{name}"}
    if not faces:
        t["all"] = f"neobuilder:block/{name}"
        return {"parent": "minecraft:block/cube_all", "textures": t}
    t["north"] = t["south"] = t["east"] = t["west"] = f"neobuilder:block/{name}_side"
    if "front" in faces:
        t["north"] = f"neobuilder:block/{name}_front"
    if "back" in faces:
        t["south"] = f"neobuilder:block/{name}_back"
    t["up"] = f"neobuilder:block/{name}_top" if "top" in faces else t["north"]
    t["down"] = f"neobuilder:block/{name}_bottom" if "bottom" in faces else t["north"]
    return {"parent": "minecraft:block/cube", "textures": t}

def blockstates(block, variant_kind, model):
    """override用blockstateを生成"""
    m = {"model": model}
    if variant_kind == "":
        return {"variants": {"": m}}
    if variant_kind == "multipart":
        return {"multipart": [{"apply": m}]}
    if variant_kind == "lit":
        return {"variants": {"lit=true": m, "lit=false": m}}
    if variant_kind == "facing":
        return {"variants": {f"facing={d}": dict(m, y=r)
                for d, r in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]}}
    if variant_kind == "lit_facing":
        return {"variants": {f"facing={d},lit={l}": dict(m, y=r)
                for d, r in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
                for l in ("true", "false")}}
    if variant_kind == "face_facing":
        return {"variants": {f"face={fa},facing={d}": dict(m, y=r)
                for d, r in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
                for fa in ("floor", "wall", "ceiling")}}
    if variant_kind == "charges":
        return {"variants": {f"charges={n}": m for n in range(5)}}
    return {"variants": {"": m}}

def _pipe_elem(frm, to, omit=(), end_faces=()):
    return {"from": frm, "to": to, "faces": {
        f: {"texture": "#all", **({"uv": [4, 4, 12, 12]} if f in end_faces else {})}
        for f in ("north", "south", "east", "west", "up", "down") if f not in omit}}

def pipe_models(tex):
    """(中心キューブ, north腕) モデル. パイプ太さ 6px"""
    t = {"all": tex, "particle": tex}
    core = {"textures": t,
            "elements": [_pipe_elem([5, 5, 5], [11, 11, 11])]}
    arm = {"textures": t,
           "elements": [_pipe_elem([5, 5, 0], [11, 11, 5], omit=("south",),
                                   end_faces=("north",))]}
    return core, arm

def pipe_icon_model(tex):
    """インベントリ表示用: 中心+両腕の直管（水平置き3Dミニモデル）"""
    t = {"all": tex, "particle": tex}
    return {"textures": t, "elements": [
        _pipe_elem([5, 5, 5], [11, 11, 11]),
        _pipe_elem([5, 5, 0], [11, 11, 5], omit=("south",)),
        _pipe_elem([5, 5, 11], [11, 11, 16], omit=("north",)),
    ]}

def pipe_family_icon(name):
    """アイテム名 → 3Dパイプアイコンのファミリ（パイプ以外はNone）"""
    if name.startswith(("cable_", "transformer_")) \
            or name in ("pipe_kinesis", "pipe_teleport_structure"):
        return "pipe_energy"
    if name.startswith("pipe_fluid") or name == "pipe_teleport_fluid":
        return "pipe_fluidf"
    if name.startswith("pipe_"):
        return "pipe_item"
    return None

def engine_model(name):
    """BCエンジン風: 低い台座 + 上部の小ピストンヘッド"""
    def tex(key):
        return f"neobuilder:block/{name}_{key}"
    t = {"particle": tex("front"), "front": tex("front"), "back": tex("back"),
         "side": tex("side"), "top": tex("top"), "bottom": tex("bottom")}
    base = {"from": [1, 0, 1], "to": [15, 12, 15], "faces": {
        "north": {"texture": "#side"}, "south": {"texture": "#side"},
        "east": {"texture": "#side"}, "west": {"texture": "#side"},
        "up": {"texture": "#top"}, "down": {"texture": "#bottom"}}}
    head = {"from": [4, 12, 4], "to": [12, 16, 12], "faces": {
        f: {"texture": "#front"} for f in ("north", "south", "east", "west", "up", "down")}}
    return {"textures": t, "elements": [base, head]}

def wall_pipe_blockstates(up_map):
    """壁blockstate: upを種別bitとして2種を同居させ、N/E/S/W(low|tall)で腕を出す。
    (waterloggedは水描画が出るため使わない / upはサーバ側が物理キャンセルで固定する)"""
    parts = []
    for up, typ in sorted(up_map.items()):
        uv = "true" if up else "false"
        parts.append({"when": {"up": uv},
                      "apply": {"model": f"neobuilder:block/pipe_{typ}_core"}})
        for face, rot in [("north", {}), ("east", {"y": 90}),
                          ("south", {"y": 180}), ("west", {"y": 270})]:
            parts.append({"when": {"up": uv, face: "low|tall"},
                          "apply": {"model": f"neobuilder:block/pipe_{typ}_arm",
                                    "uvlock": True, **rot}})
    return {"multipart": parts}

def pipe_blockstates(fam):
    """multipart: 中心 + faceプロパティごとの腕 (armはnorth向きにモデル化、回転で各方向)"""
    parts = [{"apply": {"model": f"neobuilder:block/{fam}_core"}}]
    for face, rot in [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}),
                      ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})]:
        parts.append({"when": {face: "true"},
                      "apply": {"model": f"neobuilder:block/{fam}_arm",
                                "uvlock": True, **rot}})
    return {"multipart": parts}

# バニラで item/<base> モデルが存在せず block/<base> を参照すべきベース素材
# （クライアントログの Missing block model 一覧から特定）
BLOCK_FALLBACK = {
    "ancient_debris", "andesite", "beacon", "black_concrete", "black_stained_glass",
    "blast_furnace", "blue_stained_glass", "brown_stained_glass", "coal_block",
    "crafting_table", "crying_obsidian", "cut_sandstone", "cyan_stained_glass",
    "daylight_detector", "diamond_block", "diamond_ore", "emerald_block",
    "emerald_ore", "end_rod", "furnace", "glass", "glowstone",
    "gray_stained_glass", "grindstone", "hay_block", "heavy_weighted_pressure_plate",
    "iron_block", "iron_trapdoor", "jungle_log", "light_blue_stained_glass",
    "lightning_rod", "lime_stained_glass", "lodestone", "magenta_stained_glass",
    "netherite_block", "oak_fence", "obsidian", "orange_stained_glass",
    "orange_wool", "piston", "polished_andesite", "polished_diorite",
    "polished_granite", "prismarine", "prismarine_bricks", "purple_stained_glass",
    "quartz_block", "red_stained_glass", "redstone_lamp", "respawn_anchor",
    "sandstone", "sea_lantern", "shroomlight", "smithing_table", "smoker",
    "stone", "stripped_dark_oak_log", "stripped_oak_log", "stripped_spruce_log",
    "yellow_carpet", "yellow_stained_glass", "yellow_wool",
}

# block/<base> も単純名で存在しない特殊ブロック → 実在するモデル名
SPECIAL_BLOCK_MODEL = {
    "respawn_anchor": "minecraft:block/respawn_anchor_0",
    "oak_fence": "minecraft:block/oak_fence_inventory",
    "iron_trapdoor": "minecraft:block/iron_trapdoor_bottom",
}

def items_def(base, entries):
    """minecraft:items/<base>.json（1.21.4+の新アイテム定義）"""
    if base in SPECIAL_BLOCK_MODEL:
        fallback_model = SPECIAL_BLOCK_MODEL[base]
    elif base in BLOCK_FALLBACK:
        fallback_model = f"minecraft:block/{base}"
    elif base == "compass":
        fallback_model = "neobuilder:item/compass_fallback"
    else:
        fallback_model = f"minecraft:item/{base}"
    return {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "fallback": {"type": "minecraft:model", "model": fallback_model},
            "entries": [
                {"threshold": cmd,
                 "model": {"type": "minecraft:model", "model":
                           (f"neobuilder:item/pipe_icon_{name}"
                            if name in PIPE_HOSTS
                            else f"neobuilder:item/{name}")}}
                for name, cmd in entries
            ]
        }
    }


def png_decode(data):
    """最小PNGデコーダ (8bit, non-interlace, color type 2/3/6) -> list[list[(r,g,b,a)]]"""
    import zlib as _zl
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos = 8; idat = b""; w = h = ct = None; plte = []; trns = b""
    while pos < len(data):
        ln = int.from_bytes(data[pos:pos+4], "big"); tag = data[pos+4:pos+8]
        body = data[pos+8:pos+8+ln]; pos += 12 + ln
        if tag == b"IHDR":
            w = int.from_bytes(body[:4], "big"); h = int.from_bytes(body[4:8], "big")
            bd = body[8]; ct = body[9]
        elif tag == b"PLTE":
            plte = [tuple(body[i:i+3]) for i in range(0, len(body), 3)]
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
    raw = _zl.decompress(idat)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    bits = w * ch * bd
    stride = (bits + 7) // 8
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    prev = bytearray(stride); off = 0
    for y in range(h):
        ft = raw[off]; off += 1
        cur = bytearray(raw[off:off+stride]); off += stride
        for i in range(stride):
            a = cur[i-ch] if i >= ch else 0
            b = prev[i]; c = prev[i-ch] if i >= ch else 0
            if ft == 1: cur[i] = (cur[i] + a) & 255
            elif ft == 2: cur[i] = (cur[i] + b) & 255
            elif ft == 3: cur[i] = (cur[i] + (a + b) // 2) & 255
            elif ft == 4:
                p = a + b - c; pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                cur[i] = (cur[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        for x in range(w):
            if bd < 8:
                # 低ビット深度: バイト列からビット単位で取り出し（indexed/gray）
                bi = x * bd
                byte = cur[bi >> 3]
                idx = (byte >> (8 - bd - (bi & 7))) & ((1 << bd) - 1)
                if ct == 3:
                    r, g, b = plte[idx]
                    a = trns[idx] if idx < len(trns) else 255
                    px[y][x] = (r, g, b, a)
                else:
                    g8 = idx * 255 // ((1 << bd) - 1)
                    px[y][x] = (g8, g8, g8, 255)
                continue
            v = cur[x*ch:(x+1)*ch]
            if ct == 6: px[y][x] = (v[0], v[1], v[2], v[3])
            elif ct == 2: px[y][x] = (v[0], v[1], v[2], 255)
            elif ct == 3:
                r, g, b = plte[v[0]]
                a = trns[v[0]] if v[0] < len(trns) else 255
                px[y][x] = (r, g, b, a)
            elif ct == 0: px[y][x] = (v[0], v[0], v[0], 255)
            else: px[y][x] = (v[0], v[1], v[0], v[1])
    return px

def flatten_tex(dst, url, bg):
    """BC等の透過テクスチャを不透明下地へ合成して保存"""
    import urllib.request
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "NeoBuilderPackGen"})
        src = png_decode(urllib.request.urlopen(req, timeout=15).read())
        out = [[(0, 0, 0, 255)] * len(src[0]) for _ in range(len(src))]
        for y, row in enumerate(src):
            for x, (r, g, b, a) in enumerate(row):
                t = a / 255.0
                out[y][x] = (int(r*t + bg[0]*(1-t)), int(g*t + bg[1]*(1-t)),
                             int(b*t + bg[2]*(1-t)), 255)
        with open(dst, "wb") as f:
            f.write(png_bytes(out, len(src[0]), len(src)))
        return True
    except Exception as e:
        print(f"  DL失敗 {url.split('/')[-1]}: {e}")
        return False

def fetch(url, dest):
    """テクスチャをDL。失敗時False"""
    import urllib.request
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "NeoBuilderPackGen"})
        with urllib.request.urlopen(req, timeout=15) as r, open(dest, "wb") as f:
            f.write(r.read())
        return True
    except Exception as e:
        print(f"  DL失敗 {url.split('/')[-1]}: {e}")
        return False

def proc_tex(work, sub, name, palette):
    """手続きテクスチャを書き出す"""
    px = gen_texture(palette[0], palette[1], palette[2])
    os.makedirs(f"{work}/assets/neobuilder/textures/{sub}", exist_ok=True)
    with open(f"{work}/assets/neobuilder/textures/{sub}/{name}.png", "wb") as f:
        f.write(png_bytes(px))

def main():
    out_zip = sys.argv[1] if len(sys.argv) > 1 else "NeoBuilderResourcePack.zip"
    work = sys.argv[2] if len(sys.argv) > 2 else "_pack_build"
    pack_name = sys.argv[3] if len(sys.argv) > 3 else os.path.basename(out_zip)
    if os.path.exists(work):
        shutil.rmtree(work)
    os.makedirs(work)

    # pack.mcmeta
    write(f"{work}/pack.mcmeta", {
        "pack": {
            "pack_format": 68,
            "min_format": 65,
            "max_format": 999,
            "description": "NeoBuilder Resource Pack"
        }
    })

    # アイテムごと: モデル + テクスチャ（オリジナル優先、なければ手続き生成）
    base_map = {}  # base_material -> [(name, cmd)]
    for name, (base, cmd) in ITEMS.items():
        base_map.setdefault(base, []).append((name, cmd))
        write(f"{work}/assets/neobuilder/models/item/{name}.json", item_model(name))
        os.makedirs(f"{work}/assets/neobuilder/textures/item", exist_ok=True)
        dst = f"{work}/assets/neobuilder/textures/item/{name}.png"
        if name in TEX_ITEMS and fetch(TEX_ITEMS[name], dst):
            pass  # オリジナル取得成功
        else:
            pal = PALETTES.get(name, ((200, 200, 200), (255, 255, 255), "item"))
            proc_tex(work, "item", name, pal)

    # compassベースのfallback用（バニラitemモデル非依存）
    if "compass" in base_map:
        write(f"{work}/assets/neobuilder/models/item/compass_fallback.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "neobuilder:item/compass_fallback"}
        })
        dst = f"{work}/assets/neobuilder/textures/item/compass_fallback.png"
        if not fetch("https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.4/assets/minecraft/textures/item/compass_16.png", dst):
            proc_tex(work, "item", "compass_fallback", ((150, 150, 150), (200, 60, 60), "item"))

    # minecraft:items/<base>.json override
    for base, entries in base_map.items():
        entries.sort(key=lambda e: e[1])
        write(f"{work}/assets/minecraft/items/{base}.json", items_def(base, entries))

    # ブロック: blockstate override + block model + block texture（オリジナル優先）
    for name, (block, kind) in BLOCKS.items():
        spec = TEX_BLOCKS.get(name)
        if spec and "all" in spec:
            # 全面同一テクスチャ
            write(f"{work}/assets/neobuilder/models/block/{name}.json", block_model(name))
            dst = f"{work}/assets/neobuilder/textures/block/{name}.png"
            pal0 = PALETTES.get(name, ((200, 200, 200),))[0]
            if not flatten_tex(dst, spec["all"], pal0):
                pal = PALETTES.get(name, ((200, 200, 200), (255, 255, 255), "pipe"))
                proc_tex(work, "block", name, pal)
        elif spec and name.startswith("engine_"):
            # エンジン: BC風 台座+ピストン形状。5面全てにテクスチャが必要
            faces = {k: v for k, v in spec.items()}
            write(f"{work}/assets/neobuilder/models/block/{name}.json", engine_model(name))
            os.makedirs(f"{work}/assets/neobuilder/textures/block", exist_ok=True)
            for face in ("front", "back", "side", "top", "bottom"):
                url = faces.get(face) or faces.get("side") or faces.get("front")
                dst = f"{work}/assets/neobuilder/textures/block/{name}_{face}.png"
                pal = PALETTES.get(name, ((200, 200, 200), (255, 255, 255), "engine"))
                if not flatten_tex(dst, url, pal[0]):
                    proc_tex(work, "block", f"{name}_{face}", pal)
        elif spec:
            # 面別テクスチャ
            faces = {k: v for k, v in spec.items()}
            write(f"{work}/assets/neobuilder/models/block/{name}.json", block_model(name, faces))
            os.makedirs(f"{work}/assets/neobuilder/textures/block", exist_ok=True)
            for face, url in faces.items():
                dst = f"{work}/assets/neobuilder/textures/block/{name}_{face}.png"
                pal = PALETTES.get(name, ((200, 200, 200), (255, 255, 255), "engine"))
                if not flatten_tex(dst, url, pal[0]):
                    proc_tex(work, "block", f"{name}_{face}", pal)
        else:
            write(f"{work}/assets/neobuilder/models/block/{name}.json", block_model(name))
            pal = PALETTES.get(name, ((200, 200, 200), (255, 255, 255), "machine"))
            proc_tex(work, "block", name, pal)
        write(f"{work}/assets/minecraft/blockstates/{block}.json",
              blockstates(block, kind, f"neobuilder:block/{name}"))

    # パイプ: 種別ごとに 中心/腕モデル + 色テクスチャ + 3Dアイコン + 壁blockstate
    wall_groups = {}
    for typ, (host, wl, base, acc) in PIPE_HOSTS.items():
        wall_groups.setdefault(host, {})[wl] = typ
        tex = f"neobuilder:block/pipe_{typ}"
        core, arm = pipe_models(tex)
        write(f"{work}/assets/neobuilder/models/block/pipe_{typ}_core.json", core)
        write(f"{work}/assets/neobuilder/models/block/pipe_{typ}_arm.json", arm)
        write(f"{work}/assets/neobuilder/models/item/pipe_icon_{typ}.json",
              pipe_icon_model(tex))
        dst = f"{work}/assets/neobuilder/textures/block/pipe_{typ}.png"
        bc = BC_PIPE_TEX.get(typ)
        url = f"{BC}/buildcrafttransport/textures/pipes/{bc}.png" if bc else None
        if not url or not flatten_tex(dst, url, base):
            proc_tex(work, "block", f"pipe_{typ}", (base, acc, "mesh"))
    for host, wl_map in wall_groups.items():
        write(f"{work}/assets/minecraft/blockstates/{host}.json",
              wall_pipe_blockstates(wl_map))

    # zip
    if os.path.exists(out_zip):
        os.remove(out_zip)
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(work):
            for fn in files:
                full = os.path.join(root, fn)
                z.write(full, os.path.relpath(full, work))
    print(f"written: {out_zip} ({os.path.getsize(out_zip)} bytes)")

if __name__ == "__main__":
    main()
