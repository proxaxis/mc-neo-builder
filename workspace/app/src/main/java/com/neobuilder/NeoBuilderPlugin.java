package com.neobuilder;

import com.neobuilder.core.data.DataStore;
import com.neobuilder.economy.EconomyManager;
import com.neobuilder.energy.EnergyManager;
import com.neobuilder.gui.GuiManager;
import com.neobuilder.industry.MachineManager;
import com.neobuilder.item.ItemRegistry;
import com.neobuilder.magic.ChantManager;
import com.neobuilder.pipe.PipeManager;
import com.neobuilder.rpg.RpgManager;
import com.neobuilder.tech.AllegianceManager;
import com.neobuilder.utility.UtilityManager;
import com.neobuilder.world.DungeonManager;
import com.neobuilder.world.WorldManager;
import com.neobuilder.world.WorldgenManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Neo Builder Plugin メインクラス.
 * 各サブシステムのマネージャを生成・接続する.
 */
public final class NeoBuilderPlugin extends JavaPlugin {

    private DataStore dataStore;
    private ItemRegistry itemRegistry;
    private GuiManager guiManager;
    private EconomyManager economyManager;
    private AllegianceManager allegianceManager;
    private EnergyManager energyManager;
    private MachineManager machineManager;
    private PipeManager pipeManager;
    private RpgManager rpgManager;
    private ChantManager chantManager;
    private UtilityManager utilityManager;
    private WorldManager worldManager;
    private DungeonManager dungeonManager;
    private WorldgenManager worldgenManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createWorlds();

        this.dataStore = new DataStore(this);
        this.itemRegistry = new ItemRegistry(this);
        this.itemRegistry.registerRecipes();
        this.guiManager = new GuiManager(this);
        this.economyManager = new EconomyManager(this);
        this.allegianceManager = new AllegianceManager(this);
        this.energyManager = new EnergyManager(this);
        this.machineManager = new MachineManager(this);
        this.pipeManager = new PipeManager(this);
        this.rpgManager = new RpgManager(this);
        this.chantManager = new ChantManager(this);
        this.utilityManager = new UtilityManager(this);
        this.worldManager = new WorldManager(this);
        this.dungeonManager = new DungeonManager(this);
        this.worldgenManager = new WorldgenManager(this);

        this.guiManager.enable();
        this.economyManager.enable();
        this.allegianceManager.enable();
        this.energyManager.enable();
        this.machineManager.enable();
        this.pipeManager.enable();
        this.rpgManager.enable();
        this.chantManager.enable();
        this.utilityManager.enable();
        this.worldManager.enable();
        this.dungeonManager.enable();
        this.worldgenManager.enable();

        getLogger().info("[NeoBuilder] enabled. Machines=" + machineManager.count()
                + " Pipes=" + pipeManager.count());
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) dungeonManager.disable();
        if (worldManager != null) worldManager.disable();
        if (utilityManager != null) utilityManager.disable();
        if (chantManager != null) chantManager.disable();
        if (rpgManager != null) rpgManager.disable();
        if (pipeManager != null) pipeManager.disable();
        if (machineManager != null) machineManager.disable();
        if (energyManager != null) energyManager.disable();
        if (allegianceManager != null) allegianceManager.disable();
        if (economyManager != null) economyManager.disable();
        if (dataStore != null) dataStore.close();
        getLogger().info("[NeoBuilder] disabled.");
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    /** 2ワールドの自動生成（戦闘用異世界・生活用工業世界） */
    private void createWorlds() {
        String other = getConfig().getString("other-world", "world_dungeon");
        String life = getConfig().getString("life-world", "world_life");
        if (Bukkit.getWorld(other) == null) {
            new org.bukkit.WorldCreator(other)
                    .environment(org.bukkit.World.Environment.NORMAL)
                    .createWorld();
            getLogger().info("[NeoBuilder] created world: " + other);
        }
        if (Bukkit.getWorld(life) == null) {
            new org.bukkit.WorldCreator(life)
                    .environment(org.bukkit.World.Environment.NORMAL)
                    .createWorld();
            getLogger().info("[NeoBuilder] created world: " + life);
        }
    }

    /** 戦闘/ダンジョンワールドか */
    public boolean isOtherWorld(org.bukkit.World world) {
        return world.getName().equals(getConfig().getString("other-world", "world_dungeon"));
    }

    /** 工業/生活ワールドか */
    public boolean isLifeWorld(org.bukkit.World world) {
        return world.getName().equals(getConfig().getString("life-world", "world_life"));
    }

    /** デバッグログ出力 */
    public void debug(String msg) {
        if (getConfig().getBoolean("debug")) getLogger().info("[NeoBuilder][DEBUG] " + msg);
    }

    // --- 管理コマンド ---
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "neobuilder" -> { return adminCommand(sender, args); }
            case "tech" -> {
                if (!(sender instanceof Player p)) return true;
                allegianceManager.openSelectionGui(p);
                return true;
            }
            case "job" -> {
                if (!(sender instanceof Player p)) return true;
                rpgManager.openClassGui(p);
                return true;
            }
            case "skill" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length == 0) { rpgManager.listSkills(p); return true; }
                if (args.length >= 2 && args[0].equalsIgnoreCase("learn")) {
                    rpgManager.learnWarriorSkill(p, args[1]);
                    return true;
                }
                rpgManager.castSkill(p, args[0]);
                return true;
            }
            case "balance" -> {
                if (!(sender instanceof Player p)) return true;
                p.sendMessage("残高: " + economyManager.format(economyManager.getBalance(p.getUniqueId())));
                return true;
            }
            case "pay" -> {
                if (!(sender instanceof Player p)) return true;
                if (args.length < 2) { p.sendMessage("/pay <player> <amount>"); return true; }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { p.sendMessage("プレイヤーが見つかりません"); return true; }
                try {
                    double amount = Double.parseDouble(args[1]);
                    if (economyManager.transfer(p.getUniqueId(), target.getUniqueId(), amount)) {
                        p.sendMessage(economyManager.format(amount) + " を " + target.getName() + " に送金しました");
                    } else {
                        p.sendMessage("残高不足または不正な金額です");
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage("金額が不正です");
                }
                return true;
            }
        }
        return false;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neobuilder.admin")) {
            sender.sendMessage("権限がありません");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("/nb give <item> | /nb efp | /nb disaster | /nb reload");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("プレイヤーのみ"); return true; }
                if (args.length < 2) { p.sendMessage("/nb give <itemKey>"); return true; }
                var item = itemRegistry.create(args[1]);
                if (item == null) { p.sendMessage("不明なアイテム: " + args[1]); return true; }
                p.getInventory().addItem(item);
                return true;
            }
            case "efp" -> {
                sender.sendMessage("EFP=" + worldManager.getEfp()
                        + " threshold=" + getConfig().getDouble("efp.disaster-threshold"));
                return true;
            }
            case "disaster" -> {
                worldManager.triggerDisaster();
                sender.sendMessage("災害を発生させました");
                return true;
            }
            case "boss" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("プレイヤーのみ"); return true; }
                worldManager.spawnBoss(p.getLocation());
                return true;
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("config を再読み込みしました");
                return true;
            }
        }
        sender.sendMessage("不明なサブコマンド");
        return true;
    }

    // --- getters ---
    public DataStore dataStore() { return dataStore; }
    public ItemRegistry items() { return itemRegistry; }
    public GuiManager gui() { return guiManager; }
    public EconomyManager economy() { return economyManager; }
    public AllegianceManager allegiance() { return allegianceManager; }
    public EnergyManager energy() { return energyManager; }
    public MachineManager machines() { return machineManager; }
    public PipeManager pipes() { return pipeManager; }
    public RpgManager rpg() { return rpgManager; }
    public ChantManager chants() { return chantManager; }
    public WorldManager world() { return worldManager; }
    public DungeonManager dungeons() { return dungeonManager; }
}
