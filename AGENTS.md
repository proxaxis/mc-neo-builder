# NeoBuilder 開発メモ

## ビルド & 配備

- ソース: `/operationer/workspace/app` (コンテナ内 `/app`)
- **JDK 25 必須**: Paper API `26.3.build.211-stable` は Java 25 コンパイル。`app/.tools/jdk-25.0.5+8` を使用。
- Maven: `app/.tools/apache-maven-3.9.16`（ローカルリポジトリは `/app/.m2/repository` で永続化）
- ビルド:
  ```
  docker exec -u root -e JAVA_HOME=/app/.tools/jdk-25.0.5+8 mc_neo_builder_workspace \
    sh -c 'cd /app && ./.tools/apache-maven-3.9.16/bin/mvn -q -Dmaven.repo.local=/app/.m2/repository package'
  ```
- 配備: jar は `nv_dist` ボリューム (`workspace:/app/dist` → `testserver:/dist` ro) に出力される:
  ```
  docker exec mc_neo_builder_testserver sh -c 'cp /dist/NeoBuilder.jar /data/plugins/'
  docker restart mc_neo_builder_testserver
  ```
- RCON: `docker exec mc_neo_builder_testserver rcon-cli <cmd>` (パスワード devpassword)
- デバッグポート: 5005 (JDWP)

## 構成

`com.neobuilder` パッケージ配下:
- `core/data/DataStore` — JSON永続化（非同期書き込み）
- `item/ItemRegistry` — カスタムアイテム(PDC `nb_item`/`nb_tech`) + 粉精錬レシピ
- `gui/GuiManager+NbGui` — クリックキャンセル型インベントリGUI
- `tech/AllegianceManager` — 科学/魔法の排他制御・拾得/搬入監視
- `economy/EconomyManager` — DL残高（メモリ＋非同期永続化）
- `industry/` — Machine基盤 + Engine/Quarry/Filler/Macerator（tick分割駆動）
- `pipe/` — 抽出/輸送/テレポート(周波数)/黒曜石/キネシス/ロジスティクス(BFS上限+tick予算)
  - `FluidManager` — 流体パイプ: 大釜を流体コンテナとして水/溶岩を輸送(333mB単位)。燃焼エンジンは大釜溶岩を燃料にできる
- `rpg/` — 6職業・パッシブ・スキル・MP・戦士ツリー
- `magic/` — 詠唱ルーンシーケンスGUI
- `utility/` — CutAll/MineAll/DigAll（生活ワールドのみ、BFS上限+分割適用）
- `world/` — EFP・災害・カタクリズムボス + `DungeonManager`（ダンジョン生成）+ `BossRig`（アーマースタンド演出）

## 2ワールド構成

- `world_dungeon` — 戦闘/ダンジョンワールド。新規チャンク生成時に塔/地下石室が確率生成（config `dungeon.spawn-chance`）
- `world_life` — 工業/生活ワールド。一括破壊・災害の対象
- 両方ともプラグイン起動時に WorldCreator で自動生成

## リソースパック

- 生成: `python3 /operationer/tools/gen_pack.py <出力zip> <作業dir>`
- アイテムモデル: `assets/minecraft/items/<base>.json` の range_dispatch(custom_model_data) override
- ブロックモデル: `assets/minecraft/blockstates/<block>.json` の variants/multipart override（機械を3Dモデル化）
- 配信: `packserver` サービス（compose、nv_dist を jwebserver で配信、ホスト8000番）。`docker compose up -d packserver` で起動済。nv_dist 配下を直接配信するため zip 差し替えは即反映
- server.properties は compose の RESOURCE_PACK* env で設定済（localhost:8000、SHA1 は zip 再生成時に更新）

### テクスチャ方針（必須ルール）

- **テクスチャは源流MODの実物を使うこと**（BuildCraft / IndustrialCraft2 / LogisticsPipes / Mekanism / Botania 等）。取得元URLは `tools/gen_pack.py` の `TEX_ITEMS`/`TEX_BLOCKS`/`PIPE_FAMILIES` に集約。個人使用前提のため公開配布はしない。
- **ただしプラグイン（クライアント無改造）なので適応加工が必須**:
  - ブロックモデルは `textures/block/`（blocksアトラス）のスプライトしか使えない → item側テクスチャはblock側へコピーして使う
  - MODのパイプ断面アイコン等は透過主体 → そのまま貼ると透けるため `flatten_tex()` で不透明下地へ合成（パレット/1bit PNG対応の自前デコーダ `png_decode()` 実装済み）
  - 透過合成でも端フレーム画素がブロック境界に黒線として出る場合は、均一不透明の生成テクスチャへフォールバック
  - `pack.mcmeta` は新形式必須: `min_format`/`max_format` が無いとメタデータ読み込み自体が失敗する
  - ブロック系ベース素材のアイテムfallbackは `minecraft:block/<base>`（`item/<base>` モデルは存在しない）。respawn_anchor/oak_fence/iron_trapdoor 等は `SPECIAL_BLOCK_MODEL` で実在モデル名にマップ
- パイプ描画は multipart blockstate + 6方向faceプロパティブロック（`brown_mushroom_block`=輸送, `red_mushroom_block`=流体, `mushroom_stem`=エネルギー）で中心キューブ+方向腕。サーバ側がPipeManagerのfaceフラグ計算と連動
- エンジンは `engine_model()` でBC風「台座+ピストン」形状
- **カスタムブロック化は「ベース素材の一意性」が前提**: 同じバニラ素材を共有する複数カスタムアイテムは片方しか差替できない（例: pump/compressor が共にPISTONだった→compressorをMOSS_BLOCKへ変更）。ItemRegistry登録時は他と被らない素材を選ぶこと。マシン/パイプは位置レジストリ管理なのでベース素材変更は安全
- **プロパティ持ちブロックを差し替える場合は `multipart` の apply-only で全状態をカバー**するのが最も簡単（daylight_detector/lightning_rod/axis系等）。facingバリアント別に見せたい場合のみ個別variants
- マシン系の一意ベース素材例: magma_block(pump), moss_block(compressor), target(generator), honeycomb_block(reactor_chamber), raw_iron_block(machine_block), dripstone_block(extractor), smithing_table(refinery), crafting_table(auto_workbench), hay_block(tank), netherite_block(原子炉), ancient_debris(mass_fab), beacon(teleporter)
- **ChunkLoadEventでパイプface再計算**: 起動時の一括applyVisualは未ロードチャンクをスキップするため必須

## 既知の環境注意

- **workspaceコンテナの `/app` はホスト `workspace/app` と同期されないことがある**。ビルド前に `docker cp src/. mc_neo_builder_workspace:/app/src/` で直接同期すること（同期せずビルドすると古いjarが配備される）
- ビルド成果物は `target/neo-builder-0.1.0.jar` → `dist/NeoBuilder.jar` へコピーが必要

## minecraft-mcp-server（bot操作）

- `/operationer/tools/minecraft-mcp-server`（yuniko-software 版 + ローカル改修）
- Devin MCP登録: `.devin/mcp_config.json` → セッション再起動で `mcp` ツール経由で bot が操作可能になる
- **testserver に ViaVersion+ViaBackwards 5.12.2-SNAPSHOT を追加導入済み**（`/data/plugins/`）。bot は 26.1 プロトコルで接続（mineflayer/minecraft-protocol の上限が 26.1 のため）
- ローカル改修（src/bot-connection.ts）:
  - `--version` CLIオプション追加
  - `add_resource_pack` ハンドラ差替: ACCEPTED→DOWNLOADED→LOADED の3段応答（mineflayer標準は DOWNLOADED を飛ばし、26.x鯖が configuration で待ち続ける）
  - `physicsTick` 毎に `tick_end` 送信（mineflayer#4189: 26.3鯖の1tick1移動パケット制限で2個目のmoveでkickされる対策）
- 依存は minecraft-data 3.117 / mineflayer 4.39 / minecraft-protocol 1.68 に更新済み
- GUIクライアント側の操作は `tools/mcdesk`（scrot+xdotool経由、MC本体は `/config/mc`、起動 `/config/mc/launch.sh`、オフラインユーザ `Devin`）

## 仮想デスクトップ

- `desktop` サービス（compose, `lscr.io/linuxserver/webtop:ubuntu-xfce`, KasmVNC/Selkies）
- アクセス: `http://<ホスト>:3000`（HTTPS は 3001）Basic認証 user: `abc` / pass: `devpassword`
- `n-mc_neo_builder` ネットワーク参加のため、デスクトップ内からは `testserver:25565` でMCサーバに接続可能
- ホーム等は `nv_desktop` ボリュームに永続化

## 未実装/今後

- ダンジョンボスのより高度なフェーズ演出、アイテム実3Dモデル（パイプ系は3Dアイコン化済み）
- オリジナルMODテクスチャは `TEX_ITEMS`/`TEX_BLOCKS`/`PIPE_FAMILIES` で主要アイテムは流用済み。未カバー分（新規IC2系マシン等）は引き続きURL追加で拡充

## パイプ描画の最終実装（wallホスト方式）

- **ホスト**: 各パイプ種別に `*_wall` ブロックを割当（`PIPE_HOSTS`/`WALL_HOSTS`）。N/E/S/W=`none|low|tall`+`up`+`waterlogged`。`waterlogged` で1壁に2種を同居（約40種まで可能）
- **サーバ側**: `PipeManager.applyVisual` で `Wall.setHeight(face, LOW/NONE)` + `setWaterlogged` を設定。`up`/`down` は描画しない（壁のupはバニラが自動再計算して突起になるため腕モデルも当てない。downプロパティは壁に存在しない）
- **blockstate**: `wall_pipe_blockstates()` — waterlogged別に when:{prop:"low|tall"} で腕を条件適用
- **テクスチャ**: `BC_PIPE_TEX` で各パイプにBC実テクスチャを割当 → `flatten_tex` で種別色の不透明下地へ合成（BCの「透明+暗フレーム」を活かす）。BC名が存在しない種はmesh生成へFB。腕端面は `uv:[4,4,12,12]` で中央切取（端フレームの黒線を防ぐ）

## 検証で確立した事実

- ブロックモデルは `textures/block/` 配下のみ参照可（itemsアトラス混在はモデル全拒否）
- `pack.mcmeta`: pack_format≥65は `min_format`/`max_format` 必須（旧形式値だと `supported_formats` 要求）
- ブロック系ベースのアイテムfallbackは `minecraft:block/<base>`（`item/<base>` は無い）
- BCのMOD用PNGは1bitパレット+透過主体が多い → 貼ると透ける。`png_decode`+`flatten_tex` で不透明合成が必須
- BCマシン/パイプのベース素材が他アイテムと被ると個別差替不能 → 一意の未使用ブロックへItemRegistry側で再割当
- 位置レジストリ管理（Machine/Pipe）なのでベース素材変更は検出に影響しない
- workspaceコンテナ `/app` はホストと同期しない → `docker cp src/.` 必須。jarは `target/` → `dist/` コピー

## 残課題

- パイプの下向き接続腕（壁にdownプロパティ無し。chorus_plantなら6面あるが種類は1つだけ）
- DL失敗したBC名（iron_fluid, emzuli_fluid, quartz_fluid, cobblestone_power, iron_power等）はmesh生成FB中 — 正しいBC名が分かれば `BC_PIPE_TEX` 修正で差替え可
- ピストンアニメーション等は未実装

## 実機検証で見つかった実装バグ（修正済）

- **`Machine` が `EnergyManager.MjHolder` を実装していなかった** → `distributeToNeighbors` の `instanceof MjHolder` が常にfalseで、エンジンが**隣接マシンに直接給電できなかった**（キネシスパイプ経路は `receiveEnergy` 直呼びで動いていた）。`Machine implements MjHolder` で修正。実機でスターリング→粉砕機の粉生産を確認。
- **`FluidManager.route()` がBFS開始ノード自身の隣接コンテナを検査しなかった** → 「大釜→パイプ→大釜」の最短構成で永遠に輸送されない（queue空で即false→差し戻し）。開始ノードをqueueに入れて修正。
- **流体の往復振動** → パイプが配送先コンテナからも抽出するため流体が行き来して半々で安定する。`Pipe.lastDest`（非永続）に直近送液先を記録し、抽出元スキャンで除外して解消。3レベル完全転送を確認。
- `route()` は送液先キーを返す形に変更し、抽出元は `exclude` で挿入先から除外。

## MCP/bot 運用の注意（実機検証で確認）

- **複数devin acpセッションが同名 `DevinBot` のMCPサーバを起動し蹴り合う**（"You logged in from another location"）。`ps aux | grep "main.js --host"` で重複を確認し、ソケット未接続の余分プロセスをkillする。蹴り合い中はインベントリがdesyncし、giveしたアイテムが `list-inventory` に出ない・place-block が全フェースで失敗する等の怪挙動になる。**まずプロセス重複を疑え**
- サーバ再起動でbotは切断されるが、MCPサーバは自動再接続する（〜40秒）
- `execute ... run say` の出力はRCONに返らない → 状態確認は `execute if block <pos> <block>[state]` の `Test passed/failed` を使う
- mineflayerは**2つの壁（1.5高当たり判定）に挟まれた床面をクリックできない**（視線遮蔽で "No suitable reference block found"）→ パイプ列は端から順に外向きに設置する。隙間への後置きは不可能
- `gamemode creative` 中はmineflayerの装備/インベントリ追跡が壊れやすい → 検証はサバイバルで
- `/nb give` はプレイヤー専用 → botには `send-chat "/nb give <id>"`、バニラアイテムはRCON `give DevinBot` 可
- `pipes.json`/`machines.json` は**非同期保存**（dirtyフラグ+定期フラッシュ）。登録直後にファイルへ反映されないのは正常で、実態はメモリ上のレジストリ。machines.json の energy/fuelBurn/progress はdirty契機（登録/解除）時しか保存されない
- `data get block` はブロックエンティティ以外で "not a block entity" エラー → ブロック種別/状態はbot `get-block-info` か `execute if block` で
- チャンク未ロード時は機械/パイプのtickも止まる → 無人検証は `forceload add` でチャンク固定

## 検証済みの動作（v27時点）

- パイプ設置→登録+一意ホスト壁変換（extract=andesite_wall、transport=cobblestone_wall、fluid=resin_brick_wall）+ 隣接方向のみ low 腕、waterlogged=false 維持
- チェスト→抽出管→輸送管×2→チェスト: アイテム完全転送
- エンジンが隣接チェストの石炭を消費（fuelBurn=1600）し発電→隣接粉砕機へ給電→鉱石→粉×2を側面チェストへ排出
- 流体管: 水大釜→空大釜へ333mB単位で全量転送
- パイプ破壊→登録解除。隣接ブロック設置/撤去で接続状態が壊れない（BlockPhysicsEventキャンセル有効）
