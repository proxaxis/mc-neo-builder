# Neo Builder Plugin for Minecraft Paper Servers

ようこそ! このプラグインは、BuildCraft、LogisticsPipes、IC2 などの往年の名作工業 MOD、本格アクション RPG、経済システムをシームレスに体験できる複合プラグインです. サーバ参加時に自動適用される専用リソースパックにより、精巧な 3D モデルのマシン、滑らかなパイプ輸送、カスタム GUI、迫力のボスバトルを再現しています. 

## 1. はじめに

本プラグインは Minecraft Java Edition (Paper MC) 向けに最適化されていますので、クライアント MOD は必要ありません. ただし、ゲームの快適なプレイには専用のリソースパックの有効化が必要ですので、サーバ参加時には確認をお願いします. また、このサーバに入ったら、"科学技術" を使うか、"魔法技術" を使うか選択します. これは排他的であり、他方の技術のアイテムを所持することはできません. 取引で解決しましょう!

## 2. 基本概念

ワールドには、統一された 4 つの単位を中心としたエコシステムがあります.

### 2.1. 単位

#### 2.1.1. `MJ` - `Minecraft Joules`

* 基本となるエネルギー単位です. 
* 工業ではエンジンや発電機で生成され、電線やキネシスパイプを通じてマシンに供給されます. 魔術では、その魔法の出力を表します.
* 工業では粉砕機、採掘機、ポンプなどの稼働に必須です. 魔術では魔法の使用に必須です.

#### 2.1.2. `DL` - `Minecraft Dollar`

* 基本となる経済単位です.
* プレイヤー間取引、ショップでの売買、土地の開放や維持などに使用されます. 

#### 2.1.3. `MP` - `Magic Point`

* 基本となる魔法使用単位です.
* 魔法の使用時に使用されます. 

#### 2.1.4. `EFP` - `Minecraft Environmental Foot Print`

* プレイヤーの活動によって環境に与える影響を表す環境負荷指数です.
* 環境負荷をかけるプレイヤーは相応の対価を払わなければいけません!

### 2.2. 世界

#### 2.2.1. オーバーワールド

基本となる世界. この世界では特殊なモブは沸きません. ただし "災害" があることを忘れてはいけません. この世界では技術力とお金がものを言います.

#### 2.2.2. 異世界

特殊世界. この世界では、ダンジョン、強力な敵、特殊なモブなどが沸きます. この世界で生き残るためには戦わなければなりません. 強くなりましょう. 勝てなければオーバーワールドに戻っても意味がありません.

## 3. 工業システム

### 3.1. 動力とエンジン

* Redstone Engine: レッドストーン信号のみで動く低出力エンジン. パイプからのアイテム抽出に最適です.
* Stirling Engine: 石炭や木炭を燃料とする中型動力. 序盤の粉砕機やポンプの稼働に使用します.
* Combustion Engine: 冷却水と精製油などの燃料を消費して高出力を生み出す大型エンジンです.
* Nuclear Engine: 最高出力を誇る原子力エンジンです. コストパフォーマンスに優れますが、取り扱いには注意が必要です.

### 3.2 パイプと配送

* Transport Pipes: アイテムを搬送します. 木、石、鉄、金、ダイヤ、黒曜石など、材質ごとに分岐、加速、吸引の特性があります. 
* Teleport Pipes: 離れた拠点間やディメンションを越えてアイテム、液体、エネルギーを瞬時に無線転送します.
* Logistics Pipes: リクエストパイプやプロバイダーパイプを接続することで、チェストの中身を検索して手元へ自動納品したり、中間素材から完成品までのオンデマンド自動クラフトを実現します.

### 3.3. 採掘と整地と加工

* Quarry: 指定エリアを岩盤まで全自動採掘します. 動作には十分なエネルギーと広い土地が必要です.
* Filler: 整地、穴埋め、指定ブロックの置換を一瞬で行う建築補助マシンです.
* Macerator: 鉱石を粉砕して粉に変換します. 釜土で焼くことで鉱石を効率的に精錬することができます.

## 4. ロールプレイングシステムとスキルシステム

プレイヤーは自分のプレイスタイルに合わせた職業を選択し、スキルツリーを解放できます.
異世界を冒険してお金を稼ぎましょう!

### 職業

* Warrior: 戦士です. 高い近接火力を持ちます.
* Assassin: 暗殺者です. 高い移動速度を持ちます.
* Guardian: 守護者です. 高い耐久性能を持ちます.
* Healer: 癒すものです. 高い治癒能力を持ちます. 他者を癒すこともできます.
* Mage: 魔道士です. 呪文書を用いた魔法攻撃能力を持ちます.
* Archer: 弓使いです. 本クラスを持つ者だけが弓を使用できます.

> [!WARNING]
> 職業のリセットにはお金がかかります. 慎重に選びましょう!

> [!NOTE]
> スキルはオーバーワールドでも使用可能です. 強き者は弱き者の味方をしましょう.

#### 詳細情報

##### 1. Warrior
- 特徴: 最前線で敵のヘイトを集めつつ、強力な近接攻撃で敵陣を粉砕する物理アタッカーです.
- パッシブ効果: 剣や斧などを装備した時の近接ダメージが +20%、ノックバック耐性が +15%.
- 固有スキル:
  - `ウォークライ`: 周囲の敵を威嚇し、一定時間自身と味方の攻撃力を上昇させる.
  - `クリーブラッシュ`: 前方扇状の敵全体に大ダメージを与え、防御力を一時的に低下させる.
  - `リアリスフレーゼ`: クラスに縛られない特殊スキルを取得できます. 
- 制約と弱点: 基礎機動力が低いため飛行型モブや遠距離の敵に苦戦します.

##### 2. Assassin
- 特徴: 驚異的な俊敏性と高いクリティカル率で単一ターゲットを瞬時に仕留めるスピードスターです.
- パッシブ効果: 常時移動速度 +30%、背後からの攻撃時ダメージ 2.5倍.
- 固有スキル:
  - `シャドウステップ`: ターゲットの背後へ一瞬でテレポートし、次の攻撃を確定クリティカルにする.
  - `スモークスクリーン`: 煙幕を展開して敵のターゲットを解除し、一時的に完全ステルス状態となる.
- 制約と弱点: 基礎近接攻撃力および最大体力が低く、乱戦や正面からの殴り合いには向きません.

##### 3. Guardian
- 特徴: 重厚な防具と強固な盾で味方への攻撃を一身に引き受けるメインタンクです.
- パッシブ効果: 受けるダメージ 25% 軽減、常時ノックバック完全無効、盾構え時の移動ペナルティなし.
- 固有スキル:
  - `タウント`: 周囲のモブの攻撃ターゲットを自身へ強制集中させる.
  - `フォートレス`: 数秒間、受けるあらゆるダメージを極限までカットし、味方へのダメージを肩代わりする.
- 制約と弱点: 攻撃力と移動速度が極めて低く、ソロでの敵討伐には時間がかかります.

##### 4. Healer
- 特徴: 味方の体力回復、バフ付与、状態異常解除を担うパーティの生命線です.
- パッシブ効果: 周囲の味方の自然回復速度向上、自身が受ける回復効果 +30%.
- 固有スキル:
  - `サンクチュアリ`: 地面に聖なる領域を展開し、範囲内の味方を継続回復しつつアンデッドモブに持続ダメージを与える.
  - `リザレクション`: 倒れた仲間をその場で復帰させる（長時間のクールダウンあり）.
- 制約と弱点: 攻撃手段がほぼ皆無であり、常に護衛役の仲間と共に行動する必要があります.

##### 5. Mage
- 特徴: 呪文書を用いた詠唱パズルを解くことで、規格外の広範囲、高威力属性魔法を放つスペルキャスターです.
- パッシブ効果: 属性耐性 +20%、最大 MP 保有量増加.
- 固有スキル:
  - `メテオストライク`: 指定地点に巨大な隕石を落とし、地形効果（延焼、爆発）と超広範囲大ダメージを与える.
  - `フロストノヴァ`: 周囲の敵を氷漬けにして移動、攻撃を封じる.
- 制約と弱点: 防具適性が低く紙装甲. 詠唱中は無防備になるため、パズルを解く速度と安全な位置取りが求められます.

##### 6. Archer
- 特徴: 唯一、弓やクロスボウの装備と射撃が許可された遠距離専門職です.
- パッシブ効果: 射程距離に応じたダメージ増加、矢の消費確率 50% 軽減.
- 固有スキル:
  - `レインオブアローズ`: 指定エリアの上空から無数の矢の雨を降らせ、範囲内の敵を足止めする.
  - `ピアシングショット`: 障害物や複数の敵を貫通し、直撃したボスの装甲を無視する強力な一撃を放つ.
- 制約と弱点: 至近距離での戦闘能力が極めて低く、矢の連射には精密なチャージ管理が必要です.

### Warrior 専用追加スキル

Warriorのレベルアップや討伐実績で得られるスキルポイントを割り振ることで、攻撃性能や前線維持能力を特化させることができます.

#### 1. 攻撃強化系
- ヘビースラッシュ (Lv.1〜5)
  - 通常攻撃時に一定確率で範囲薙ぎ払いが発生し、周囲の敵にも 30%〜60% の拡散ダメージを与える.
- アーマーブレイカー (Lv.1〜3)
  - 防具を装備したモブやボスへのダメージが 15%〜35% 増加し、対象の防御力を数秒間低下させる.
- エクスキューション (Lv.1〜3)
  - 残り体力が 30% 以下の敵に対する近接クリティカルダメージが 50%〜100% 増加する.

#### 2. 生存や維持系
- ブラッドラスト (Lv.1〜3)
  - 近接攻撃で敵を撃破した際、自身の体力をハート 0.5〜1.5 個分回復し、3秒間移動速度が上昇する.
- アンチェインド (Lv.1〜2)
  - スローやスタンなどの移動阻害系デバフの効果時間を 25%〜50% 短縮する.
- バトルハードン (Lv.1〜5)
  - 連続で近接攻撃を受けるたび、被ダメージを段階的に最大 20% まで軽減する（3秒間ダメージを受けないとリセット）.

#### 3. 特殊アクション系
- リープアサルト (アクティブ / CT: 15秒)
  - 前方へ力強く跳躍し、着地時に衝撃波を発生させて周囲の敵を吹き飛ばしつつ短時間の鈍足を付与する.
- バーサークモード (アルティメット / CT: 90秒)
  - 15秒間、受けるダメージが 20% 増加する代わりに、攻撃速度 +40%、近接ダメージ +50%、全ノックバック無効状態となる.

## 5. 生活ユーティリティ

作業のストレスを減らし、工業や農業に集中できる便利機能を搭載しています. 

* CutAll Utility: 斧で原木を壊すと、繋がっている原木と葉を一括で伐採. 
* MineAll Utility: ピッケルで鉱石を壊すと、鉱脈を一括で採掘.
* DigAll Utility: シャベルで土、砂、砂利を一括掘削.

> [!WARNING]
> これらのシステムはオーバーワールドのみで有効です.

## 6. ダンジョンと強化ボス

異世界には、邪悪なトラップと強力なモブが潜むダンジョンが自動生成されます.

* 強化型モブ: 防具をまとったゾンビの群れ、特殊矢を放つスケルトン、爆発範囲の広がったクリーパーなどが出現. 
* カタクリズム、ボス（Cataclysm Bosses）:
  * 独自の連続攻撃、薙ぎ払い、レーザー放射、ノックバック無効化を持つレイド級ボス. 
  * 討伐することで、工業の最上位素材（Void Core等）や、固有スキルを持つ伝説の武器や防具をドロップします. 

## 7. 災害と救済

オーバーワールドではごくまれに "災害" が発生します. これに対抗できるのは、武力を持った人たちです. 協力して切り抜けましょう!

## 8. 魔道士用の詠唱システム

魔道士が魔術を実行するためには "詠唱" が必要です. 呪文書を使用すると、目の前のタスクをこなす必要があります. 簡単なパズルです、誰でもできるでしょう?

## 9. 参考情報

参考にした主要 MOD の GitHub リポジトリ、解説 Wiki リンク、Summary、主要追加アイテムやブロックのまとめです.

> [!WARNING]
> ※一部の MOD は総アイテム数が数百に及ぶため、主要なアイテム群を網羅して記載しています.

### 1. BuildCraft

* GitHub: [Buildcraft/Buildcraft](https://github.com/Buildcraft/Buildcraft)
* Wiki: [FTB Wiki - BuildCraft](https://ftbwiki.org/BuildCraft) / [Official Wiki (GitHub Pages)](https://www.google.com/search?q=https://mod-buildcraft.com/wiki/)
* Summary: マイクラ工業化、自動化MODの金字塔. パイプによるアイテム、液体輸送、MJ や RF 動力のエンジン、クァーリーによる自動採掘、フィラーによる自動建築、整地機能などを追加します. 
* All/Main Items:
  * 機械や建築: Quarry、Mining Well、Pump、Filler、Builder、Architect Table、Electronic / Auto Workbench、Refinery、Laser、Assembly Table
  * エンジン: Redstone Engine、Stirling Engine、Combustion Engine
  * 輸送とパイプ: Transport / Fluid / Kinesis（Wooden、Cobblestone、Stone、Iron、Gold、Diamond、Emerald、Obsidian、Void）
  * 制御とツール: Wrench、Land Mark、Path Marker、Blueprint、Template、Gates（Pipe Gate、Autarchic Gate 等）、各種Gear（Wood、Stone、Iron、Gold、Diamond）

### 2. Additional Pipes (Teleport Pipes)

* GitHub: [tky0086/AdditionalPipes](https://www.google.com/search?q=https://github.com/tky0086/AdditionalPipes) (または [Kyran/AdditionalPipes](https://www.google.com/search?q=https://github.com/Kyran/AdditionalPipes))
* Wiki: [Tekkit Wiki - Additional Pipes](https://tekkitwiki.com/wiki/Additional_Pipes)
* Summary: BuildCraftのパイプ機能を拡張するアドオン. 同一周波数を設定することで長距離、ディメンション間を瞬時に転送できるテレポートパイプ（Phased Pipe）や分配パイプを追加します. 
* All/Main Items:
* テレポートパイプ: Item Teleport Pipe、Fluid Teleport Pipe、Power/Energy Teleport Pipe
* 特殊パイプ: Distribution Transport Pipe、Advanced Insertion Pipe、Extraction Pipe、Redstone Pipe、Water Pump Pipe

### 3. Logistics Pipes

* GitHub: [RS485/LogisticsPipes](https://github.com/rs485/logisticspipes)
* Wiki: [Logistics Pipes Official Wiki](https://www.google.com/search?q=https://github.com/RS485/LogisticsPipes/wiki) / [GT New Horizons Wiki](https://wiki.gtnewhorizons.com/wiki/Logistics_Pipes)
* Summary: BuildCraftのパイプシステムを大幅に高度化し、オンデマンドの自動クラフト、遠隔アイテムリクエスト、インベントリ自動管理ネットワークを構築可能にするMOD. 
* All/Main Items:
* パイプ: Basic Logistics Pipe、Request Pipe、Provider Pipe、Crafting Pipe、Supplier Pipe、Satellite Pipe、Chassis Pipe (Mk1〜Mk5)
* モジュール: ItemSink Module、Passive Supplier Module、Extractor Module、Polymorphic ItemSink Module、QuickSort Module
* ブロック、ツール: Logistics Power Junction、Crafting Table、Remote Orderer、Logistics Programmer、Pipe Controller、Network Monitor

### 4. AdvancedFiller

* GitHub: [a1lic/McMod-AdvFiller](https://github.com/a1lic/McMod-AdvFiller) (原作者: 和製MOD)
* Wiki: [Minecraft Japan Wiki (AdvancedFiller)](https://www.google.com/search?q=https://w.atwiki.jp/minecraft/pages/707.html)
* Summary: BuildCraftのフィラー（Filler）およびクァーリー（Quarry）の機能を拡張、統合した機械MOD. 整地、充填、指定ブロックの置換、採掘を高精度、高速に行えます. 
* All/Main Items:
* 機械: Advanced Filler（拡張フィラー）、Advanced Quarry（拡張クァーリー）
* 設定ツール、ブロック: Marker（赤、緑、青の指定用マーカー）、Range Setter（範囲設定ツール）、各種パターンカード（Fill、Flatten、Box、Erase、Replace等）

### 5. ExtraTanks

* GitHub: [yuo/ExtraTanks](https://www.google.com/search?q=https://github.com/yuo/ExtraTanks) (または有志メンテナンスリポジトリ)
* Wiki: [Minecraft Japan Wiki (ExtraTanks)](https://www.google.com/search?q=https://w.atwiki.jp/minecraft/pages/1000.html)
* Summary: BuildCraft互換の液体タンクを強化する和製MOD. 金属素材（鉄、金、ダイヤなど）を用いた大容量の多層タンクやポータブルタンクを追加します. 
* All/Main Items:
* タンクブロック: Copper Tank、Iron Tank、Silver Tank、Gold Tank、Diamond Tank、Obsidian Tank
* アップグレード: Capacity Upgrade（容量拡張）、Fluid Valve、Portable Tank

### 6. QuarryPlus (Additional Enchanted Miner)

* GitHub: [Kotori316/QuarryPlus](https://github.com/Kotori316/QuarryPlus)
* Wiki: [QuarryPlus GitHub Wiki](https://www.google.com/search?q=https://github.com/Kotori316/QuarryPlus/wiki) / [FTB Wiki](https://ftbwiki.org/QuarryPlus)
* Summary: BuildCraftの採掘機（Quarry）やポンプに「エンチャント（効率、幸運、シルクタッチなど）」を付与可能にする強力なアドオン. 現在は単体でも動作するバージョンが継続開発されています. 
* All/Main Items:
* 機械: QuarryPlus、Chunk Destroyer、PumpPlus、MiningWellPlus、MarkerPlus、WorkbenchPlus
* 特殊装置: EnchantMover（ツールから機械へエンチャント移行）、Book EnchantMover、Status Checker
* モジュール: Pump Module、Exp Pump Module、Fuel Module、Replacer Module

### 7. 一括破壊系（CutAll / MineAll / DigAll）

* GitHub: [takun/TreeCapitator / MC-All-Mods](https://github.com/)（作者: akalibing / takun などの公開リポジトリ）
* Wiki: [Minecraft Japan Wiki (一括破壊系MOD)](https://www.google.com/search?q=https://w.atwiki.jp/minecraft/pages/289.html)
* Summary: 連鎖的に同種ブロックを一括破壊する定番の和製ユーティリティMOD. CキーなどでON/OFF切り替え可能. 
* 追加アイテム:
* CutAll: 固有アイテム追加なし（斧ツールを持った際に原木、葉を一括破壊）
* MineAll: 固有アイテム追加なし（ツルハシを持った際に鉱石を一括破壊）
* DigAll: 固有アイテム追加なし（シャベルを持った際に土、砂、砂利を一括破壊）

### 8. RedPower 2

* GitHub: [eloraam/redpower2](https://github.com/) (※クローン/リメイク: [ProjectRed (MrTJP)](https://github.com/MrTJP/ProjectRed))
* Wiki: [RedPower 2 FTB Wiki](https://ftbwiki.org/RedPower_2)
* Summary: 細密レッドストーン配線（ワイヤー）、論理ゲート、パイプ搬送（Pneumatic Tube）、回路設計、さらにForth言語によるコンピュータシステムやフレーム機構を追加した伝説的技術MOD. 
* All/Main Items:
* Wiring/Logic: Red Alloy Wire、Insulated Wire、Bundled Cable、AND/OR/XOR/TimerなどのLogic Gate
* マシン、搬送: Deployer、Block Breaker、Transposer、Filter、Retriever、Sorting Machine、Pneumatic Tube、Frame
* マシン、動力: Kinetic Generator、Windmill、Alloy Furnace、Solar Panel、Computer、Disk Drive、Monitor
* ツール、素材: Sickle、Handsaw、Screwdriver、Athame、Gem（Ruby、Sapphire、Green Sapphire）、Basalt/Marble

### 9. RPG Series (Simply Skills / RPG Gods / More RPG Classes)

* GitHub: [ZsoltFabok (Daedelus)](https://www.google.com/search?q=https://github.com/ZsoltFabok)
* Wiki: [RPG Series Official Wiki (CurseForge/GitBook)](https://www.google.com/search?q=https://curseforge.com/minecraft/mc-mods/rpg-series)
* Summary: 近年のMinecraft（Fabric/Forge）向けに開発された、クラス（戦士、魔道士、ローグ、パラディン等）、スペルブック、装備、ステータスシステムを導入する本格アクションRPG拡張群. 
* All/Main Items:
* 武器、防具: 各クラス専用防具セット（Cloth、Leather、Plate等）、Staff、Wand、Dagger、Claymore、Spell Blade
* アイテム: Spell Books（火、氷、回復、雷など）、Rune、Skill Gem、Class Tokens、Boss Essences

### 10. L'Ender's Cataclysm

* GitHub: [L-Ender-s-Cataclysm](https://www.google.com/search?q=https://github.com/L-Ender-s-Cataclysm)
* Wiki: [Cataclysm Wiki (Fandom)](https://www.google.com/search?q=https://cataclysm-mod.fandom.com/wiki/L_Ender%2527s_Cataclysm_Wiki)
* Summary: 超高クオリティなダンジョン構造物と、独自の戦闘アニメーションや攻撃パターンを持つ超強力なボス（Netherite Monstrosity、Ender Guardian、Harbinger等）を追加するエンドコンテンツ戦闘MOD. 
* All/Main Items:
* ボスドロップ、武器: The Incinerator、Gauntlet of Guard、Meat Shredder、Laser Gatling、Infernal Forge、Tidal Claws、Void Core
* 防具、装備: Ignitium Armor、Monstrous Helm、Cursed Sight、Mech Eye
* 召喚具、素材: Eye of Abyss、Eye of Nether、Mechanical Fuse、Void Stone、Witherite Ingot

### 11. IndustrialCraft 2 (IC2)

* GitHub: [IndustrialCraft-2 (Jenkins/Repo)](https://www.google.com/search?q=https://github.com/IndustrialCraft-2)
* Wiki: [IC2 Official Wiki](https://wiki.industrial-craft.net/)
* Summary: EU（Energy Unit）電力概念、鉱石倍化、ゴム生成、原子力発電、クアンタム装備などを確立した歴史的工業化MOD. 
* All/Main Items:
* 発電機: Generator、Solar Panel、Water/Wind Mill、Geothermal Generator、Nuclear Reactor（原子炉）
* 加工機械: Macerator（粉砕機）、Electric Furnace、Compressor、Extractor、Recycler、Mass Fabricator
* 配線、蓄電: Copper/Gold/Iron/Glass Cable、BatBox、CESU、MFE、MFSU
* ツール、装備: Mining Drill、Diamond Drill、Chainsaw、Wrench、Nano Armor、QuantumSuit（クアンタムスーツ）、Jetpack、Mining Laser
* 素材: Rubber、Tin/Copper/Bronze/Uranium、Refined Iron、Iridium、Carbon Plate、Circuit（電子回路）