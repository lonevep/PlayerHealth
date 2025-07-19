package io.github.lonevep.playerHealth;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.attribute.Attribute;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

public final class PlayerHealth extends JavaPlugin implements TabCompleter {
    private static final String VERSION = "1.1.0";
    private static final String AUTHOR = "lone_vep";
    private static final String QQ = "2022901674";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");
    
    private FileConfiguration messageConfig;
    private FileConfiguration variableConfig;
    private boolean debug;
    private final Random random = new Random();
    private PlayerHealthExpansion expansion;

    @Override
    public void onEnable() {
        printStartupInfo();
        initializeConfigs();
        registerPlaceholderAPI();
        Objects.requireNonNull(getCommand("playerhealth")).setTabCompleter(this);
    }

    private void printStartupInfo() {
        System.out.println("§a插件已加载 ✅");
        System.out.println("§a版本: §f" + VERSION);
        System.out.println("§a作者: §f" + AUTHOR);
        System.out.println("§aQQ: §f" + QQ);
        System.out.println("§a接定制插件 定制服务端 多业务");
        System.out.println("§a感谢您的使用！");
    }

    private void initializeConfigs() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        loadMessageConfig();
        loadVariableConfig();
        validateVariables();
    }

    private void registerPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            expansion = new PlayerHealthExpansion(this);
            expansion.register();
            debug(null, "PlaceholderAPI 依赖已挂钩 ✅");
        } else {
            getLogger().warning("PlaceholderAPI 依赖未挂钩 ❌");
        }
    }

    private void loadMessageConfig() {
        File messageFile = new File(getDataFolder(), "message.yml");
        if (!messageFile.exists()) {
            try {
                saveResource("message.yml", false);
            } catch (IllegalArgumentException ignore) {
                // 文件已存在时不报错
            }
        }
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
    }

    private void loadVariableConfig() {
        File variableFile = new File(getDataFolder(), "variable.yml");
        if (!variableFile.exists()) {
            try {
                saveResource("variable.yml", false);
            } catch (IllegalArgumentException ignore) {
                // 文件已存在时不报错
            }
        }
        variableConfig = YamlConfiguration.loadConfiguration(variableFile);
    }

    private void validateVariables() {
        if (variableConfig == null) return;
        
        // 检查变量功能是否启用
        if (!variableConfig.getBoolean("enable", true)) {
            debug(null, "[变量校验] 变量功能已禁用，跳过校验");
            return;
        }
        
        Set<String> names = new HashSet<>();
        for (String varName : variableConfig.getKeys(false)) {
            // 跳过enable配置项
            if ("enable".equals(varName)) continue;
            
            if (!isValidVariableName(varName, names)) continue;
            
            String mode = variableConfig.getString(varName + ".mode", "normal");
            if (!isValidMode(mode, varName)) continue;
            
            if (mode.equalsIgnoreCase("normal")) {
                validateNormalVariable(varName);
            }
        }
    }

    private boolean isValidVariableName(String varName, Set<String> names) {
        if (varName == null || varName.trim().isEmpty()) {
            debug(null, "变量名不能为空: " + varName + "，请检查variable.yml配置");
            return false;
        }
        if (!varName.matches("[a-zA-Z0-9_]+")) {
            debug(null, "变量名不能为特殊字符或空格: " + varName + "，请检查variable.yml配置");
            return false;
        }
        if (varName.matches("\\d+")) {
            debug(null, "变量名不能为纯数字: " + varName + "，请检查variable.yml配置");
            return false;
        }
        if (names.contains(varName)) {
            debug(null, "变量名重复: " + varName + "，请检查variable.yml配置");
            return false;
        }
        names.add(varName);
        return true;
    }

    private boolean isValidMode(String mode, String varName) {
        if (!mode.equalsIgnoreCase("normal") && !mode.equalsIgnoreCase("elite")) {
            debug(null, "变量 " + varName + " 的mode字段无效(仅允许normal/elite): " + mode + "，请检查variable.yml配置");
            return false;
        }
        return true;
    }

    private void validateNormalVariable(String varName) {
        List<String> config = variableConfig.getStringList(varName + ".values");
        double totalWeight = 0;

        for (String line : config) {
            if (line.startsWith("weight::")) {
                try {
                    totalWeight += Double.parseDouble(line.substring(8));
                } catch (NumberFormatException e) {
                    debug(null, "变量 " + varName + " 权重格式错误: " + line + "，请检查variable.yml配置");
                }
            } else if (line.startsWith("number::")) {
                validateNumberFormat(line, varName);
            }
        }
        
        if (Math.abs(totalWeight - 1.0) > 0.0001) {
            debug(null, "变量 " + varName + " 权重总和不是1.0, 当前: " + totalWeight + "，请检查variable.yml配置");
        }
    }

    private void validateNumberFormat(String line, String varName) {
        String[] parts = line.substring(8).split("_");
        if (parts.length != 3) {
            debug(null, "变量 " + varName + " number格式错误: " + line + "，请检查variable.yml配置");
            return;
        }
        try {
            Double.parseDouble(parts[0]);
            Double.parseDouble(parts[1]);
            Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            debug(null, "变量 " + varName + " number参数格式错误: " + line + "，请检查variable.yml配置");
        }
    }

    private String getMessage(String key, Map<String, String> params) {
        String msg = messageConfig.getString(key, "");
        if (msg.trim().isEmpty()) return null;
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LegacyComponentSerializer.legacySection().serialize(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    public String getNumberVar(Player player, String varName) {
        debug(player, "[变量DEBUG] getNumberVar入口: varName=" + varName + ", player=" + (player == null ? "null" : player.getName()));
        
        if (player == null) {
            debug(null, "[变量解析] 变量: " + varName + "，调用来源: PAPI/外部插件，Player为null，可能是动作插件未正确传递玩家对象。");
            return "0";
        }
        
        if (variableConfig == null) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: 内部，variableConfig为null");
            return "0";
        }
        
        // 检查变量功能是否启用
        if (!variableConfig.getBoolean("enable", true)) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: 内部，变量功能已禁用");
            return "0";
        }
        
        if (!variableConfig.contains(varName + ".mode")) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: 内部，未配置mode字段，当前配置: " + variableConfig.getConfigurationSection(varName));
            return "0";
        }
        
        String mode = variableConfig.getString(varName + ".mode", "normal");
        return mode.equalsIgnoreCase("elite") ? 
            processEliteVariable(player, varName) : 
            processNormalVariable(player, varName);
    }

    private String processEliteVariable(Player player, String varName) {
        if (!variableConfig.contains(varName + ".prefix")) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，prefix缺失，当前配置: " + variableConfig.getConfigurationSection(varName));
            return "0";
        }
        
        String prefix = variableConfig.getString(varName + ".prefix");
        if (prefix == null || prefix.isEmpty()) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，prefix无效，当前配置: " + variableConfig.getConfigurationSection(varName));
            return "0";
        }
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !item.hasItemMeta()) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，玩家未持有带有lore的物品，Player: " + player.getName());
            return "0";
        }
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，物品没有lore，Player: " + player.getName());
            return "0";
        }
        
        List<Component> loreComponents = meta.lore();
        if (loreComponents == null || loreComponents.isEmpty()) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，物品lore为空，Player: " + player.getName());
            return "0";
        }
        
        // 将Component转换为String
        List<String> lore = new ArrayList<>();
        for (Component component : loreComponents) {
            lore.add(LegacyComponentSerializer.legacySection().serialize(component));
        }
        
        return extractNumberFromLore(player, varName, prefix, lore);
    }

    private String extractNumberFromLore(Player player, String varName, String prefix, List<String> lore) {
        String prefixNoColor = LegacyComponentSerializer.legacySection().serialize(LegacyComponentSerializer.legacySection().deserialize(prefix));
        
        for (int i = 0; i < lore.size(); i++) {
            String loreLineStr = lore.get(i);
            String loreNoColor = LegacyComponentSerializer.legacySection().serialize(LegacyComponentSerializer.legacySection().deserialize(loreLineStr));
            
            if (loreNoColor.startsWith(prefixNoColor)) {
                String afterPrefix = loreNoColor.substring(prefixNoColor.length());
                Matcher m = NUMBER_PATTERN.matcher(afterPrefix);
                
                if (m.find()) {
                    String num = m.group(1);
                    debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，物品lore第" + (i+1) + "行前缀后数字读取成功: " + num + "，Player: " + player.getName());
                    debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=" + num);
                    return num;
                } else {
                    debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，物品lore第" + (i+1) + "行前缀后未找到数字，lore内容: " + loreLineStr + "，Player: " + player.getName());
                    debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=0");
                    return "0";
                }
            }
        }
        
        debug(player, "[变量解析] 变量: " + varName + "，调用来源: elite，未找到匹配前缀的lore行: " + prefixNoColor + "，Player: " + player.getName());
        debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=0");
        return "0";
    }

    private String processNormalVariable(Player player, String varName) {
        List<String> config = variableConfig.getStringList(varName + ".values");
        if (config.isEmpty()) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: normal，values为空，当前配置: " + variableConfig.getConfigurationSection(varName));
            debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=0");
            return "0";
        }
        
        List<RandomNumber> numbers = parseRandomNumbers(config, player, varName);
        if (numbers.isEmpty()) {
            debug(player, "[变量解析] 变量: " + varName + "，调用来源: normal，未找到有效number配置，Player: " + player.getName());
            debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=0");
            return "0";
        }
        
        return generateRandomValue(numbers, player, varName);
    }

    private List<RandomNumber> parseRandomNumbers(List<String> config, Player player, String varName) {
        List<RandomNumber> numbers = new ArrayList<>();
        
        for (int i = 0; i < config.size(); i++) {
            String line = config.get(i);
            if (line.startsWith("number::")) {
                String[] parts = line.substring(8).split("_");
                if (parts.length == 3) {
                    try {
                        double min = Double.parseDouble(parts[0]);
                        double max = Double.parseDouble(parts[1]);
                        int decimals = Integer.parseInt(parts[2]);
                        double weight = 1.0;
                        
                        if (i + 1 < config.size() && config.get(i + 1).startsWith("weight::")) {
                            weight = Double.parseDouble(config.get(i + 1).substring(8));
                            i++;
                        }
                        
                        numbers.add(new RandomNumber(min, max, decimals, weight));
                    } catch (NumberFormatException e) {
                        debug(player, "[变量解析] 变量: " + varName + "，调用来源: normal，number/weight参数格式错误: " + line + "，Player: " + player.getName());
                    }
                } else {
                    debug(player, "[变量解析] 变量: " + varName + "，调用来源: normal，number格式错误: " + line + "，Player: " + player.getName());
                }
            }
        }
        
        return numbers;
    }

    private String generateRandomValue(List<RandomNumber> numbers, Player player, String varName) {
        double totalWeight = numbers.stream().mapToDouble(n -> n.weight).sum();
        double rand = random.nextDouble() * totalWeight;
        double currentWeight = 0;
        
        for (RandomNumber num : numbers) {
            currentWeight += num.weight;
            if (rand <= currentWeight) {
                double value = num.min + (random.nextDouble() * (num.max - num.min));
                debug(player, "[变量解析] 变量: " + varName + "，调用来源: normal，随机数生成: " + value + "，Player: " + player.getName());
                debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=" + value);
                String format = "%." + num.decimals + "f";
                return String.format(format, value);
            }
        }
        
        RandomNumber first = numbers.get(0);
        double value = first.min + (random.nextDouble() * (first.max - first.min));
        debug(player, "[变量解析] 变量: " + varName + "，调用来源: normal，默认返回第一个随机数: " + value + "，Player: " + player.getName());
        debug(player, "[变量DEBUG] getNumberVar出口: varName=" + varName + ", 返回值=" + value);
        String format = "%." + first.decimals + "f";
        return String.format(format, value);
    }

    private record RandomNumber(double min, double max, int decimals, double weight) {}

    public class PlayerHealthExpansion extends PlaceholderExpansion {
        private final PlayerHealth plugin;
        
        public PlayerHealthExpansion(PlayerHealth plugin) {
            this.plugin = plugin;
        }
        
        @Override
        public @NotNull String getIdentifier() {
            return "ph";
        }
        
        @Override
        public @NotNull String getAuthor() {
            return AUTHOR;
        }
        
        @Override
        public @NotNull String getVersion() {
            return VERSION;
        }
        
        @Override
        public boolean persist() {
            return true;
        }
        
        @Override
        public String onPlaceholderRequest(Player player, String identifier) {
            debug(player, "[PAPI DEBUG] onPlaceholderRequest: identifier=" + identifier + ", player=" + (player == null ? "null" : player.getName()));
            debug(player, "[PAPI DEBUG] 扩展标识符: " + getIdentifier() + ", 完整变量格式: %" + getIdentifier() + "_" + identifier + "%");
            
            if (identifier.startsWith("ph_var_")) {
                String varName = identifier.substring(7);
                String result = plugin.getNumberVar(player, varName);
                debug(player, "[PAPI DEBUG] onPlaceholderRequest: varName=" + varName + ", 返回值=" + result);
                
                if (player == null) {
                    debug(null, "PlaceholderAPI请求变量 " + varName + " 时Player为null，可能是动作插件未正确传递玩家对象。");
                    return "0(变量配置异常,请检查variable.yml)";
                }
                
                if ("0".equals(result)) {
                    return "0(变量配置异常,请检查variable.yml)";
                }
                return result;
            }
            
            debug(player, "[PAPI DEBUG] onPlaceholderRequest: identifier未匹配ph_var_，原样返回null");
            return null;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendUsageMessage(sender, label);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        return switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "give", "set" -> handleHealthCommand(sender, label, args);
            case "parse" -> {
                handleParseCommand(sender, label, args);
                yield true;
            }
            default -> {
                sendUsageMessage(sender, label);
                yield true;
            }
        };
    }

    private void sendUsageMessage(CommandSender sender, String label) {
        String usageKey = label.equalsIgnoreCase("ph") ? "usage-ph" : "usage-playerhealth";
        String usageMsg = getMessage(usageKey, new HashMap<>());
        if (usageMsg != null) {
            for (String line : usageMsg.split("\\r?\\n")) {
                if (!line.trim().isEmpty()) sender.sendMessage(line);
            }
        }
    }

    private boolean handleReload(CommandSender sender) {
        loadMessageConfig();
        loadVariableConfig();
        validateVariables();
        String msg = getMessage("reload-success", new HashMap<>());
        if (msg != null) sender.sendMessage(msg);
        debug(sender, "message.yml和variable.yml已重载");
        return true;
    }

    private boolean handleHealthCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendUsageMessage(sender, label);
            return true;
        }
        
        String subCommand = args[0];
        String targetName = args[1];
        String amountStr = args[2];
        
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Map<String, String> params = new HashMap<>();
            params.put("target", targetName);
            String msg = getMessage("error-player-not-found", params);
            if (msg != null) sender.sendMessage(msg);
            debug(sender, "Player not found: " + targetName);
            return true;
        }
        
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            String msg = getMessage("error-invalid-amount", new HashMap<>());
            if (msg != null) sender.sendMessage(msg);
            debug(sender, "Invalid amount: " + amountStr);
            return true;
        }
        
        return processHealthCommand(sender, target, subCommand, amount);
    }

    private boolean processHealthCommand(CommandSender sender, Player target, String subCommand, double amount) {
        var attribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHealth = attribute != null ? attribute.getValue() : 20.0;
        
        if (subCommand.equalsIgnoreCase("give")) {
            return handleGiveCommand(sender, target, amount, maxHealth);
        } else if (subCommand.equalsIgnoreCase("set")) {
            handleSetCommand(sender, target, amount, maxHealth);
            return true;
        }
        
        return false;
    }

    private boolean handleGiveCommand(CommandSender sender, Player target, double amount, double maxHealth) {
        double health = target.getHealth();
        double newHealth = Math.min(health + amount, maxHealth);
        target.setHealth(newHealth);
        
        Map<String, String> paramsSender = new HashMap<>();
        paramsSender.put("target", target.getName());
        paramsSender.put("amount", String.valueOf((int)amount));
        String msgSender = getMessage("give-success", paramsSender);
        if (msgSender != null) sender.sendMessage(msgSender);
        
        Map<String, String> paramsTarget = new HashMap<>();
        paramsTarget.put("amount", String.valueOf((int)amount));
        paramsTarget.put("health", String.valueOf((int)newHealth));
        paramsTarget.put("max_health", String.valueOf((int)maxHealth));
        String msgTarget = getMessage("give-receive", paramsTarget);
        if (msgTarget != null) target.sendMessage(msgTarget);
        
        debug(sender, "Gave health: " + amount + " to " + target.getName());
        return true;
    }

    private void handleSetCommand(CommandSender sender, Player target, double amount, double maxHealth) {
        if (amount > maxHealth) {
            Map<String, String> params = new HashMap<>();
            params.put("max_health", String.valueOf((int)maxHealth));
            String msg = getMessage("error-exceed-max", params);
            if (msg != null) sender.sendMessage(msg);
            debug(sender, "Set amount exceeds max health: " + amount + "/" + maxHealth);
            return;
        }
        
        target.setHealth(amount);
        
        Map<String, String> paramsSender = new HashMap<>();
        paramsSender.put("target", target.getName());
        paramsSender.put("amount", String.valueOf((int)amount));
        String msgSender = getMessage("set-success", paramsSender);
        if (msgSender != null) sender.sendMessage(msgSender);
        
        Map<String, String> paramsTarget = new HashMap<>();
        paramsTarget.put("amount", String.valueOf((int)amount));
        paramsTarget.put("health", String.valueOf((int)amount));
        paramsTarget.put("max_health", String.valueOf((int)maxHealth));
        String msgTarget = getMessage("set-receive", paramsTarget);
        if (msgTarget != null) target.sendMessage(msgTarget);
        
        debug(sender, "Set health: " + amount + " for " + target.getName());
    }

    private void handleParseCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendUsageMessage(sender, label);
            return;
        }

        String varName = args[1];
        if (!variableConfig.contains(varName)) {
            String msg = getMessage("error-variable-not-found", new HashMap<>());
            if (msg != null) sender.sendMessage(msg);
            debug(sender, "Variable not found: " + varName);
            return;
        }

        String mode = variableConfig.getString(varName + ".mode", "normal");
        if (!mode.equalsIgnoreCase("normal")) {
            String msg = getMessage("error-variable-not-normal", new HashMap<>());
            if (msg != null) sender.sendMessage(msg);
            debug(sender, "Variable " + varName + " is not a normal variable.");
            return;
        }

        List<String> values = variableConfig.getStringList(varName + ".values");
        if (values.isEmpty()) {
            String msg = getMessage("error-variable-values-empty", new HashMap<>());
            if (msg != null) sender.sendMessage(msg);
            debug(sender, "Variable " + varName + " has no values configured.");
            return;
        }

        debug(sender, "Parsing variable: " + varName);
        for (String line : values) {
            if (line.startsWith("number::")) {
                String[] parts = line.substring(8).split("_");
                if (parts.length == 3) {
                    try {
                        double min = Double.parseDouble(parts[0]);
                        double max = Double.parseDouble(parts[1]);
                        int decimals = Integer.parseInt(parts[2]);
                        double weight = 1.0;
                        
                        if (line.contains("weight::")) {
                            int weightIndex = values.indexOf(line) + 1;
                            if (weightIndex < values.size() && values.get(weightIndex).startsWith("weight::")) {
                                weight = Double.parseDouble(values.get(weightIndex).substring(8));
                            }
                        }
                        
                        debug(sender, "Parsed number: min=" + min + ", max=" + max + ", decimals=" + decimals + ", weight=" + weight);
                    } catch (NumberFormatException e) {
                        debug(sender, "Error parsing number line: " + line + " - " + e.getMessage());
                    }
                } else {
                    debug(sender, "Error parsing number line: " + line + " - Invalid format.");
                }
            } else if (line.startsWith("weight::")) {
                try {
                    double weight = Double.parseDouble(line.substring(8));
                    debug(sender, "Parsed weight: " + weight);
                } catch (NumberFormatException e) {
                    debug(sender, "Error parsing weight line: " + line + " - " + e.getMessage());
                }
            }
        }
        String msg = getMessage("parse-success", new HashMap<>());
        if (msg != null) sender.sendMessage(msg);
        debug(sender, "Variable " + varName + " parsed successfully.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("give", "set", "reload", "parse");
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("parse")) {
                return getVariableCompletions(args[1]);
            } else {
                return getPlayerCompletions(args[1]);
            }
        }
        
        return Collections.emptyList();
    }

    private List<String> getVariableCompletions(String input) {
        List<String> completions = new ArrayList<>();
        if (variableConfig != null) {
            for (String varName : variableConfig.getKeys(false)) {
                if (varName.toLowerCase().startsWith(input.toLowerCase())) {
                    completions.add(varName);
                }
            }
        }
        return completions;
    }

    private List<String> getPlayerCompletions(String input) {
        List<String> completions = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(input.toLowerCase())) {
                completions.add(p.getName());
            }
        }
        return completions;
    }

    private void debug(CommandSender sender, String msg) {
        if (debug) {
            if (sender != null) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(LegacyComponentSerializer.legacySection().deserialize("§e[DEBUG] " + msg)));
            } else {
                getLogger().info("[DEBUG] " + LegacyComponentSerializer.legacySection().serialize(LegacyComponentSerializer.legacySection().deserialize(msg)));
            }
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
        System.out.println("§c插件已关闭");
        System.out.println("§a作者: §f" + AUTHOR);
        System.out.println("§aQQ: §f" + QQ);
        System.out.println("§a接定制插件 定制服务端 多业务");
        System.out.println("§a感谢您的使用！");
    }
}
