# NeoBuilder アイテム・マシン図鑑

本ドキュメントは NeoBuilder プラグインで追加される全カスタムアイテム・マシンのプレイヤー向けマニュアルです。
BuildCraft / IndustrialCraft2 / LogisticsPipes 等の工業系MODに相当する要素を、バニラクライアントのまま楽しめます。

## はじめに

### 技術陣営について

- 初回ログイン後、`/tech` で **科学技術** または **魔法技術** の陣営を選択します（選択は1回のみ。変更は有償）。
- 科学陣営は「SCIENCE」アイテム、魔法陣営は「MAGIC」アイテムのみ使用できます。相反陣営のアイテムは拾得・設置・インベントリへの搬入が拒否されます。
- 陣営なし（NONE）のアイテムはどちらの陣営でも使用できます。

### ワールド構成

- `world_life` — 工業・生活ワールド。マシン設置、一括破壊（斧=伐採 / ツルハシ=鉱脈 / シャベル=掘削）、追加鉱石の生成はこちら。
- `world_dungeon` — 戦闘・ダンジョンワールド。塔や地下石室が生成され、宝箱からDLコイン等が入手できます。

### エネルギー（MJ）の流れ

- エンジン・発電機が作り出すエネルギーの単位は **MJ** です。
- 発電設備は **隣接するマシンへ直接**、または **キネシスパイプ・ケーブル類を経由して** 消費機器へ送電します。
- 液体の輸送には大釜（カルドロン）を流体コンテナとして使います。大釜の水位 1 段 = 333 mB です。

### アイテムの入手（管理者向け give コマンド）

```
/nb give <アイテムID>
```

- OP権限（`neobuilder.admin`）が必要です。実行すると自分のインベントリに 1 個入ります。
- 本書の各表の「give コマンド」列のIDを `<アイテムID>` に指定してください。

---

## エンジン

設置型の発電機です。燃料・条件を満たすと MJ を生成し、隣接マシンまたはキネシスパイプへ出力します。
燃料は隣接するチェスト等のインベントリから自動で消費します。

| アイテム | give コマンド | 出力 | 燃料・条件 |
|---|---|---|---|
| レッドストーンエンジン | `/nb give engine_redstone` | 1 MJ/t | レッドストーン信号で稼働（燃料不要） |
| スターリングエンジン | `/nb give engine_stirling` | 5 MJ/t | 石炭・木炭（1個で1600tick燃焼） |
| 燃焼エンジン | `/nb give engine_combustion` | 20 MJ/t | 溶岩バケツ（2000tick）または隣接大釜の溶岩（1段で1000tick。流体パイプで供給可） |
| 原子力エンジン | `/nb give engine_nuclear` | 100 MJ/t | ブレイズロッド・エンダーパール（1個で8000tick） |

## 発電・蓄電・配電（IC2系）

### 発電機・蓄電機

| アイテム | give コマンド | 説明 |
|---|---|---|
| 発電機 | `/nb give generator` | 石炭・木炭（160tick）・ブレイズロッド（240tick）を燃やし、燃焼中 10 MJ/t 発電 |
| ソーラーパネル | `/nb give solar_panel` | 昼間かつ真上に遮蔽がなければ 1 MJ/t 発電 |
| 原子炉 | `/nb give nuclear_reactor` | 原子力エンジン相当（100 MJ/t、ブレイズロッド/エンダーパール燃料） |
| リアクターチェンバー | `/nb give reactor_chamber` | 原子炉拡張用の素材ブロック |
| BatBox | `/nb give batbox` | 蓄電容量 40,000 MJ。最大 32 MJ/t で周囲へ放電 |
| MFEユニット | `/nb give mfe` | 蓄電容量 600,000 MJ。最大 128 MJ/t で周囲へ放電 |
| MFSユニット | `/nb give mfsu` | 蓄電容量 10,000,000 MJ。最大 512 MJ/t で周囲へ放電 |

### ケーブル・変圧器

設置するとキネシスパイプと同じく MJ を伝送する線路として機能します。

| アイテム | give コマンド | 説明 |
|---|---|---|
| 銅ケーブル | `/nb give cable_copper` | 低圧配線 |
| 金ケーブル | `/nb give cable_gold` | 中圧配線 |
| 金ケーブル(絶縁x2) | `/nb give cable_gold_ins2` | 絶縁型の中圧配線 |
| HVケーブル | `/nb give cable_hv` | 高圧配線 |
| HVケーブル(絶縁x2) | `/nb give cable_hv_ins2` | 絶縁型の高圧配線 |
| HVケーブル(絶縁x4) | `/nb give cable_hv_ins4` | 高絶縁型の高圧配線 |
| グラスファイバー | `/nb give cable_glass` | 超低損失配線 |
| LV変圧器 | `/nb give transformer_lv` | 低圧変圧器（配線として接続可能） |
| MV変圧器 | `/nb give transformer_mv` | 中圧変圧器（配線として接続可能） |
| HV変圧器 | `/nb give transformer_hv` | 高圧変圧器（配線として接続可能） |

### 携帯電源（素材）

| アイテム | give コマンド | 説明 |
|---|---|---|
| REバッテリー | `/nb give re_battery` | 小型蓄電池（素材） |
| エネルギークリスタル | `/nb give energy_crystal` | 中級蓄電池（素材） |
| ラポトロンクリスタル | `/nb give lapotron_crystal` | 上級蓄電池（素材） |

---

## 基本パイプ

設置すると自動でパイプ形状に変化し、同系統のパイプや接続先へ腕が伸びます。破壊するとアイテムとして回収できます。
アイテム輸送は「抽出パイプで取り出す → パイプ網を経由 → 途中のチェスト等へ格納」の流れです。

### アイテム輸送パイプ

| アイテム | give コマンド | 説明 |
|---|---|---|
| 輸送パイプ | `/nb give pipe_transport` | 基本的なアイテム搬送管 |
| 抽出パイプ | `/nb give pipe_extract` | 隣接インベントリからアイテムを引き抜く（輸送網の入口） |
| 金パイプ | `/nb give pipe_gold` | 輸送管（加速材質） |
| ダイヤパイプ | `/nb give pipe_diamond` | 輸送管（分岐ルーティング材質） |
| 石材輸送パイプ | `/nb give pipe_stone` | 輸送管（石素材） |
| 砂岩輸送パイプ | `/nb give pipe_sandstone` | 輸送管（砂岩素材） |
| 鉄輸送パイプ | `/nb give pipe_iron` | 輸送管（一方通行制御向け材質） |
| エメラルド輸送パイプ | `/nb give pipe_emerald` | 輸送管（フィルタ抽出向け材質） |
| 黒曜石パイプ | `/nb give pipe_obsidian` | 周囲3ブロックのドロップアイテムを吸い込んで網へ送る |
| ヴォイド輸送パイプ | `/nb give pipe_void` | アイテム廃棄管（※現バージョンでは輸送経路に乗りません） |
| 分配輸送パイプ | `/nb give pipe_distribution` | 輸送管（ラウンドロビン分配向け材質） |
| テレポートパイプ | `/nb give pipe_teleport` | 右クリックで周波数 0〜9 を切替。同周波数のパイプへアイテムを無線転送 |

### 流体パイプ

大釜をコンテナとして水・溶岩を 333 mB 単位で輸送します。材質違いも同様に動作します。

| アイテム | give コマンド | 説明 |
|---|---|---|
| 流体パイプ | `/nb give pipe_fluid` | 基本的な液体輸送管 |
| 丸石流体パイプ | `/nb give pipe_fluid_cobble` | 流体管（丸石材質） |
| 石材流体パイプ | `/nb give pipe_fluid_stone` | 流体管（石材質） |
| 砂岩流体パイプ | `/nb give pipe_fluid_sand` | 流体管（砂岩質） |
| 鉄流体パイプ | `/nb give pipe_fluid_iron` | 流体管（鉄材質） |
| 金流体パイプ | `/nb give pipe_fluid_gold` | 流体管（金材質） |
| エメラルド流体パイプ | `/nb give pipe_fluid_emerald` | 流体管（エメラルド材質） |
| ヴォイド流体パイプ | `/nb give pipe_fluid_void` | 流体管（廃棄用材質） |
| ダイヤ流体パイプ | `/nb give pipe_fluid_diamond` | 流体管（ダイヤ材質） |
| 防水テレポートパイプ | `/nb give pipe_teleport_fluid` | 流体管（無線転送向け材質） |

### エネルギー伝送パイプ

| アイテム | give コマンド | 説明 |
|---|---|---|
| キネシスパイプ | `/nb give pipe_kinesis` | MJ を運ぶエネルギー管。エンジンと離れたマシンを接続 |
| 構造テレポートパイプ | `/nb give pipe_teleport_structure` | エネルギー管（無線転送向け材質） |

## 物流パイプ（ロジスティクス）

プロバイダー管で在庫を公開し、リクエスト管から取り寄せる自動物流網を構築します。

| アイテム | give コマンド | 説明 |
|---|---|---|
| ロジスティクスパイプ | `/nb give pipe_logistics` | 物流網の基本パイプ（輸送管としても機能） |
| プロバイダーパイプ | `/nb give pipe_provider` | 隣接インベントリの中身を物流網へ公開する |
| リクエストパイプ | `/nb give pipe_request` | 右クリックで在庫一覧GUIを表示。左クリック=1個、右クリック=16個、Shift=64個を取り寄せ |
| サプライヤーパイプ | `/nb give pipe_supplier` | 物流管（継続供給向け材質） |
| クラフティングパイプ | `/nb give pipe_crafting` | 物流管（自動クラフト向け材質） |
| サテライトパイプ | `/nb give pipe_satellite` | 物流管（中継ポイント向け材質） |
| リモートオーダー | `/nb give remote_orderer` | 物流網へのリモートアクセス端末（アイテム） |

---

## マシン

設置型の加工・作業機械です。多くは MJ を消費して動作し、**隣接するチェスト等から素材を取り込み、真上→側面のインベントリへ成果物を排出**します（排出先が無ければドロップ）。破壊するとアイテムとして回収できます。

### BuildCraft系マシン

| アイテム | give コマンド | 説明 |
|---|---|---|
| クァーリー | `/nb give quarry` | 自身を中心とした最大17×17の領域を岩盤まで自動掘削（1ブロック 60 MJ）。採掘物は隣接チェストへ |
| フィラー | `/nb give filler` | 自身の直下 17×17 の空洞・水・溶岩・植物を隣接チェストのブロックで埋め立て（1ブロック 20 MJ） |
| 粉砕機 | `/nb give macerator` | 鉱石・原石を粉2個へ粉砕（1回 400 MJ）。粉はかまどで精錬するとインゴットになる＝鉱石2倍化 |
| ランドマーク | `/nb give landmark` | 範囲指定用マーカー |
| 自動作業台 | `/nb give auto_workbench` | 原木→板材×4、丸石→石を自動加工（1回 10 MJ） |
| タンク | `/nb give tank` | 水・溶岩を貯蔵する大容量コンテナ（16,000 mB） |
| ポンプ | `/nb give pump` | 2秒毎に100 MJを消費し、直下の水源を汲み上げて隣接大釜へ注水 |
| 精製所 | `/nb give refinery` | 原油バケツ/溶岩バケツ → 燃料バケツ（10 MJ/t × 100tick） |

### IC2系加工機械

| アイテム | give コマンド | 説明 |
|---|---|---|
| 鉄かまど | `/nb give iron_furnace` | 高速かまど（80tick/個、5 MJ/t） |
| 電気かまど | `/nb give electric_furnace` | 電力で精錬するかまど（100tick/個、5 MJ/t） |
| 誘導かまど | `/nb give induction_furnace` | 最速の電気かまど（40tick/個、5 MJ/t） |
| 圧縮機 | `/nb give compressor` | 石炭9個→石炭ブロック、生炭素繊維2個→生炭素メッシュ（60tick/回） |
| 抽出機 | `/nb give extractor` | ゴムの原木→ゴムを抽出（60tick/回） |
| 物質生成機 | `/nb give mass_fabricator` | 莫大なMJを消費してUUマターを生成（1000tick/個） |
| マシンブロック | `/nb give machine_block` | 機械の筐体（素材ブロック） |
| 高度マシン | `/nb give advanced_machine` | 高度機械の筐体（素材ブロック） |
| テスラコイル | `/nb give tesla_coil` | 0.5秒毎に50 MJを消費し、半径4ブロックの敵Mobへ放電（6ダメージ） |
| テレポーター | `/nb give teleporter` | 同一ワールド内の別テレポーターへ、上に乗ったエンティティを転送（1回 500 MJ） |
| 強化ガラス | `/nb give reinforced_glass` | 爆発耐性の高いガラス（建材） |
| 強化石材 | `/nb give reinforced_stone` | 爆発耐性の高い石材（建材） |
| 強化ドア | `/nb give reinforced_door` | 強化されたドア（建材） |

---

## 鉱石・採取資源

`world_life` の新規チャンクに追加生成される資源です。

| アイテム | give コマンド | 説明 |
|---|---|---|
| ウラン鉱石 | `/nb give uranium` | Y32未満に生成（エメラルド鉱石の外見）。原子力燃料の元 |
| イリジウム鉱石 | `/nb give iridium_ore` | 深層に極めて稀に生成（ダイヤ鉱石の外見） |
| ゴムの原木 | `/nb give rubber_wood` | 地表に稀に生えるゴムの木の原木。抽出機でゴムになる |
| 原油バケツ | `/nb give oil_bucket` | 原油入りバケツ。原油脈（黒い池）が地表に稀に生成。精製所で燃料へ |
| 燃料バケツ | `/nb give fuel_bucket` | 精製済み燃料（精製所の産物） |

## 素材・部品

クラフト・加工の中間素材群です。

### ギア・パイプ部品

| アイテム | give コマンド | 説明 |
|---|---|---|
| 木製ギア | `/nb give gear_wood` | 機械部品（木） |
| 石製ギア | `/nb give gear_stone` | 機械部品（石） |
| 鉄製ギア | `/nb give gear_iron` | 機械部品（鉄） |
| 金製ギア | `/nb give gear_gold` | 機械部品（金） |
| ダイヤ製ギア | `/nb give gear_diamond` | 機械部品（ダイヤ） |
| パイプシーラント | `/nb give pipe_sealant` | パイプを防水化するシール材 |
| パイププラグ | `/nb give pipe_plug` | パイプ接続を遮断する栓 |
| テレポートテザー | `/nb give teleport_tether` | テレポート関連の素材 |

### 金属・粉・合金

| アイテム | give コマンド | 説明 |
|---|---|---|
| 鉄粉 | `/nb give dust_iron` | 粉砕機の産物。かまどで精錬→鉄インゴット |
| 金粉 | `/nb give dust_gold` | 粉砕機の産物。かまどで精錬→金インゴット |
| 銅粉 | `/nb give dust_copper` | 粉砕機の産物。かまどで精錬→銅インゴット |
| 錫粉 | `/nb give dust_tin` | 錫の粉（素材） |
| 青銅粉 | `/nb give dust_bronze` | 青銅の粉（素材） |
| 精錬鉄 | `/nb give refined_iron` | 強化鉄素材 |
| 錫 | `/nb give tin_ingot` | 錫インゴット |
| 青銅 | `/nb give bronze_ingot` | 青銅インゴット |
| 高度合金 | `/nb give advanced_alloy` | 高度な合金素材 |
| 高密度銅板 | `/nb give dense_copper_plate` | 圧縮した銅板 |
| 混合金属インゴット | `/nb give mixed_metal_ingot` | 複数金属の混合素材 |
| 精製ウラン | `/nb give refined_uranium` | 精製したウラン燃料 |
| イリジウム板 | `/nb give iridium_plate` | イリジウム加工板 |

### カーボン・石炭加工

| アイテム | give コマンド | 説明 |
|---|---|---|
| 生炭素繊維 | `/nb give raw_carbon_fibre` | 炭素繊維の原料 |
| 生炭素メッシュ | `/nb give raw_carbon_mesh` | 圧縮機で生炭素繊維2個から加工 |
| カーボンプレート | `/nb give carbon_plate` | 炭素素材の完成板 |
| 石炭ボール | `/nb give coal_ball` | 石炭加工中間体 |
| 圧縮石炭ボール | `/nb give compressed_coal_ball` | 圧縮した石炭ボール |
| 石炭チャンク | `/nb give coal_chunk` | 高密度石炭素材 |
| ゴム | `/nb give rubber` | 抽出機でゴムの原木から得られる素材 |

### 回路・セル・その他

| アイテム | give コマンド | 説明 |
|---|---|---|
| 電子回路 | `/nb give circuit` | 基本電子部品 |
| 高度電子回路 | `/nb give advanced_circuit` | 上位電子部品 |
| UUマター | `/nb give matter` | 物質生成機の産物。万能素材 |
| 空のセル | `/nb give empty_cell` | 液体封入用の空容器 |
| ウランセル | `/nb give uranium_cell` | 原子炉用燃料セル |
| 冷却セル | `/nb give cooling_cell` | 原子炉冷却用セル |

### アップグレード

| アイテム | give コマンド | 説明 |
|---|---|---|
| オーバークロッカー | `/nb give upgrade_overclocker` | マシンの稼働速度を向上させる改造品 |
| 変圧アップグレード | `/nb give upgrade_transformer` | 受入電圧を上げる改造品 |
| 蓄電アップグレード | `/nb give upgrade_storage` | 内部エネルギー容量を増やす改造品 |

---

## 装備

| アイテム | give コマンド | 説明 |
|---|---|---|
| バットパック | `/nb give batpack` | 携帯蓄電パック（革鎧ベース） |
| ラップパック | `/nb give lappack` | 大容量携帯蓄電パック（鉄鎧ベース） |
| ジェットパック | `/nb give jetpack` | 飛行用パック（エリトラベース） |
| 電気ジェットパック | `/nb give electric_jetpack` | 電力駆動の飛行パック（エリトラベース） |
| ナノスーツ | `/nb give nano_suit` | 高出力防護スーツ（チェインメイルベース） |
| 量子スーツ | `/nb give quantum_suit` | 最上位防具（ネザライトベース） |

## 道具

| アイテム | give コマンド | 説明 |
|---|---|---|
| レンチ | `/nb give wrench` | マシン・パイプの回収ツール |
| チェーンソー | `/nb give chain_saw` | 高速伐採ツール |
| 採掘ドリル | `/nb give mining_drill` | 高速採掘ツール |
| ダイヤドリル | `/nb give diamond_drill` | 最速採掘ツール |
| 樹液採取具 | `/nb give treetap` | ゴムの木から樹液を採取する道具 |
| 電動樹液採取具 | `/nb give electric_treetap` | 電動式の樹液採取具 |
| ナノセイバー | `/nb give nano_saber` | 高出力の光刃武器 |

> 備考: `world_life` では通常の斧・ツルハシ・シャベルでも一括破壊（原木+葉 / 鉱石鉱脈 / 土砂系）が発動します（上限128ブロック、耐久消費あり）。

## 魔法アイテム（MAGIC陣営）

| アイテム | give コマンド | 説明 |
|---|---|---|
| 呪文書 | `/nb give spellbook` | 右クリックで詠唱開始。ルーンシーケンス成功で「フロストノヴァ」発動（Mage専用） |
| 魔導書: メテオ | `/nb give grimoire_meteor` | 右クリックで詠唱開始。「メテオストライク」を発動（Mage専用） |
| 魔導書: フロスト | `/nb give grimoire_frost` | 右クリックで詠唱開始。「フロストノヴァ」を発動（Mage専用） |
| ワンド | `/nb give wand` | 詠唱補助杖 |
| マナクリスタル | `/nb give mana_crystal` | MP回復アイテム |

> 呪文書・魔導書は `/job` で **Mage** を選択したプレイヤーのみ使用できます。詠唱は専用GUIでルーンを順番通りに入力するパズル形式です。

## その他

| アイテム | give コマンド | 説明 |
|---|---|---|
| DLコイン | `/nb give coin` | 通貨アイテム。ダンジョンの宝箱から入手可能 |
| ヴォイドコア | `/nb give void_core` | ボスドロップ素材。ダンジョン宝箱からも稀に入手 |

## Chocolate Quest（Better Dungeons）系アイテム

ダンジョン探索で手に入る武器・ガジェット・杖。陣営タグなし（ただし剣系ベースは科学陣営では攻撃不可）。

### ダガー（背撃2倍・右クリックでガード）

| アイテム | give コマンド | 説明 |
|---|---|---|
| 鉄のダガー | `/nb give dagger_iron` | 背撃2倍 / 右クリ:ガード（耐性+速度） |
| ダイヤのダガー | `/nb give dagger_diamond` | 上位ダガー |
| トリックスターダガー | `/nb give dagger_trickster` | 右クリ:短時間透明 / Shift+右クリ:瞬移8m |
| ニンジャダガー | `/nb give dagger_ninja` | 右クリ:透明化5s / Shift+右クリ:瞬移10m |

### スピア（長射程・右クリック特殊攻撃）

| アイテム | give コマンド | 説明 |
|---|---|---|
| 鉄のスピア | `/nb give spear_iron` | 右クリ:前方薙ぎ払い |
| ダイヤのスピア | `/nb give spear_diamond` | 上位スピア |
| ファイアスピア | `/nb give spear_fire` | 右クリ:直線敵を炎上 |
| ガンスピア | `/nb give spear_gun` | 右クリ:貫通弾発射 |

### グレートソード（右クリック薙ぎ払い）

| アイテム | give コマンド | 説明 |
|---|---|---|
| 鉄のグレートソード | `/nb give greatsword_iron` | 8ダメ薙ぎ払い |
| ダイヤのグレートソード | `/nb give greatsword_diamond` | 9ダメ薙ぎ払い |
| ブルグレートソード | `/nb give greatsword_bull` | 9ダメ薙ぎ払い |
| モンキンググレートソード | `/nb give greatsword_monking` | 10ダメ薙ぎ払い |

### ガジェット・杖・特殊

| アイテム | give コマンド | 説明 |
|---|---|---|
| フックショット | `/nb give hookshot` | 右クリ:20m先の敵を引き寄せ |
| リボルバー | `/nb give revolver` | 右クリ:弾発射（CD3s） |
| 回復の杖 | `/nb give staff_healing` | 右クリ:対象/自身を6回復 |
| 炎の杖 | `/nb give staff_fire` | 右クリ:火球発射 |
| 雷の杖 | `/nb give staff_thunder` | 右クリ:視線先へ落雷 |
| 風の杖 | `/nb give staff_wind` | 右クリ:周囲6m吹き飛ばし |
| 吸血の杖 | `/nb give staff_vampiric` | 右クリ:対象に3ダメ+自3回復 |
| 毒の杖 | `/nb give staff_poison` | 右クリ:周囲敵に毒II 10s |
| 蜘蛛の杖 | `/nb give staff_spider` | 右クリ:対象を強拘束（鈍足V） |
| 呪われた骨 | `/nb give cursed_bone` | 右クリ:吸血スケルトン2体召喚（消費） |
| ヒーリングポーション | `/nb give potion_healing` | 右クリ:HP4回復（消費） |
| ネクロマンサーのバッジ | `/nb give badge_necromancer` | ボス討伐証 |
| リッチのバッジ | `/nb give badge_liche` | ボス討伐証 |
| ウォーカーキングのバッジ | `/nb give badge_walker` | ボス討伐証 |
| 海賊船長のバッジ | `/nb give badge_pirate` | ボス討伐証 |
| ウォーカーソード | `/nb give sword_walker` | ボスドロップ武器 |
| スパイダーソード | `/nb give sword_spider` | 攻撃時毒付与 |
| ブルバトルアクス | `/nb give battle_axe_bull` | 強ノックバック |
