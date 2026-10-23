# Minecraft 操作ガイド（AIエージェント向け）

この環境では Minecraft サーバ（Paper 26.3）・実クライアント（仮想デスクトップ上）・bot（mineflayer/MCP）の3系統が操作できる。目的に応じて使い分けること。

## 構成

| コンポーネント | 場所 | 備考 |
|---|---|---|
| MCサーバ | コンテナ `mc_neo_builder_testserver` (`testserver:25565`, RCON `25575`) | Paper 26.3 + NeoBuilder + ViaVersion/ViaBackwards |
| 仮想デスクトップ | コンテナ `mc_neo_builder_desktop` | Ubuntu/XFCE、ブラウザからは http://ホスト:3000 (user `abc` / pass `devpassword`) |
| GUIクライアント | desktop 内 `/config/mc`、起動 `/config/mc/launch.sh` | オフラインID `Devin`、Minecraft 26.3 |
| bot | `/operationer/tools/minecraft-mcp-server` | mineflayer製、ID `DevinBot`、26.1プロトコルで参加 |

操作はすべてこのマシン（operationer コンテナ）のシェルから `docker exec` / `tools/mcdesk` / MCPツール経由で行う。

## 1. GUIクライアント操作 — `tools/mcdesk`

実クライアントの画面取得・キー/マウス操作。**表示確認が必要な作業（プラグインGUIの目視確認等）はこちら**。

```
tools/mcdesk launch [サーバ]   # 起動。引数で testserver:25565 等へ自動参加
tools/mcdesk shot              # スクリーンショット → /tmp/mc_shot_*.png (readツールで見る)
tools/mcdesk click X Y [btn]   # クリック（btn: 1左/3右/4,5ホイール）
tools/mcdesk type TEXT         # 文字列入力（まずフィールドをclickしてfocus）
tools/mcdesk key KEY           # 例: Return, Escape, t, ctrl+s
tools/mcdesk move X Y / drag X1 Y1 X2 Y2
tools/mcdesk windows / focus   # ウィンドウ一覧 / Minecraftウィンドウをアクティブ化
tools/mcdesk log / ps / stop
tools/mcdesk exec CMD...       # desktopコンテナ内で任意コマンド (DISPLAY=:1)
```

### 座標の注意（重要）

- **実画面は 2736x1568**。`shot` のPNGをreadで見ると縮小表示されるため、**画像上の座標 ≈ 実座標の半分**。目標位置は画面比率で見積もってから実座標に換算すること（例: 画像幅の50% → 実x=1368）。
- **MCのボタンは `click` 1発で反応しないことがある**。確実なのは押下を分解する方法:
  ```
  docker exec mc_neo_builder_desktop sh -c 'xdotool mousemove X Y mousedown 1; sleep 0.15; xdotool mouseup 1'
  ```
  テキストフィールドは `click` でfocusして `type` でOK。
- サーバ参加は `Multiplayer` → 登録済み `NeoBuilder Test`（testserver:25565）をダブルクリック、または `launch testserver:25565` で直接。

## 2. bot操作 — MCP `minecraft` サーバ

`DevinBot` というプレイヤーがワールド内を動く。MCPツール（セッションに `minecraft` サーバとして登録済み）:

- 移動: `get-position`, `move-to-position(x,y,z)`, `move-in-direction`, `look-at`, `jump`, `fly-to`
- ブロック: `place-block`, `dig-block`, `get-block-info`, `find-blocks`
- インベントリ/クラフト: `list-inventory`, `find-item`, `equip-item`, `craft-item`, `list-recipes`, `can-craft`, `smelt-item`
- その他: `send-chat`, `read-chat`, `find-entity`, `detect-gamemode`

手動起動（デバッグ用）:
```
node /operationer/tools/minecraft-mcp-server/dist/main.js \
  --host testserver --port 25565 --username DevinBot --version 26.1
```

### 制約

- **26.1プロトコルで参加**（ViaBackwards変換）。26.3で追加されたブロック/アイテムはbot側で見えない・触れない可能性あり。GUIクライアント側はネイティブ26.3なので、新規要素の確認はmcdeskで。
- 同名 `DevinBot` の二重ログインは蹴り合いになる。MCP起動中に手動起動しないこと。
- 改造箇所: `src/bot-connection.ts`（`--version`追加、リソースパック3段応答、`tick_end`毎tick送信）。npm install し直してもsrc側は残る。

## 3. サーバ直接操作 — RCON / コンソール

```
docker exec mc_neo_builder_testserver rcon-cli <cmd>   # 例: list, say, give Devin diamond 64, op DevinBot
docker logs -f mc_neo_builder_testserver               # 鯖ログ
docker restart mc_neo_builder_testserver               # 再起動（接続中の人が切れる）
```

## 4. プラグインのビルド→配備（要・テスト用）

```
# 同期→ビルド（workspaceコンテナの /app はホストと自動同期しないことがある）
docker cp /operationer/workspace/app/src/. mc_neo_builder_workspace:/app/src/
docker exec -u root -e JAVA_HOME=/app/.tools/jdk-25.0.5+8 mc_neo_builder_workspace \
  sh -c 'cd /app && ./.tools/apache-maven-3.9.16/bin/mvn -q -Dmaven.repo.local=/app/.m2/repository package'
# jar を配備ボリュームへ → testserverへコピー→再起動
docker exec mc_neo_builder_workspace cp /app/target/neo-builder-0.1.0.jar /app/dist/NeoBuilder.jar
docker exec mc_neo_builder_testserver sh -c 'cp /dist/NeoBuilder.jar /data/plugins/'
docker restart mc_neo_builder_testserver
```

## 運用ルール

- **検証は「botで操作＋GUIクライアントで目視」の組合せが基本**。プラグインのGUI/パーティクル等の見た目は bot では分からない。
- 接続先は必ず `testserver`（dockerネットワーク名）。`localhost` は別物。
- リソースパックは強制適用済み（`localserver:8000` → packserver に hosts解決済み）。botは実DLしないが受理応答は返す。
- サーバ再起動すると全員切断される。ユーザーのクライアント（YSK357等）が繋いでいる時は事前に注意書きを出す。
