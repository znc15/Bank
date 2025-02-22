// 主类
package org.littlesheep.bank;

import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.littlesheep.bank.storage.StorageManager;
import org.littlesheep.bank.storage.JsonStorage;
import org.littlesheep.bank.storage.SqliteStorage;
import org.littlesheep.bank.storage.MysqlStorage;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import org.littlesheep.bank.gui.BankGUI;
import org.littlesheep.bank.gui.BankGUIListener;
import org.littlesheep.bank.papi.BankExpansion;
import org.littlesheep.bank.loan.LoanRateManager;
import org.littlesheep.bank.loan.LoanPenaltyManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import org.bstats.bukkit.Metrics;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import org.json.JSONObject;
import java.net.URI;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import org.littlesheep.bank.listeners.TimeChangeListener;

public final class Bank extends JavaPlugin {
    private Economy econ = null;                      // Vault经济系统接口
    private StorageManager storageManager;            // 存储管理器
    private Map<String, Double> demandRates;          // 活期存款利率表
    private Map<String, Double> timeRates;            // 定期存款利率表
    private long lastRateUpdate;                      // 上次利率更新时间
    private BankGUI bankGUI;                         // 银行GUI界面
    private LoanRateManager loanRateManager;          // 贷款利率管理器
    private LoanPenaltyManager loanPenaltyManager;    // 贷款违约金管理器
    private boolean papiEnabled = false;              // PlaceholderAPI是否启用
    private BankCommand bankCommand;                  // 银行命令处理器
    private FileConfiguration langConfig;             // 语言配置
    private String currentLanguage;                   // 当前语言设置
    private FileConfiguration messages;  // 添加这行声明

    @Override
    public void onEnable() {
        // 显示插件信息
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName());
        getLogger().info("Version/版本: " + getDescription().getVersion());
        getLogger().info("Author/作者: " + String.join(", ", getDescription().getAuthors()));
        getLogger().info("QQ Group/QQ群: 690216634");
        getLogger().info("Github: https://github.com/znc15/Bank");
        getLogger().info(getDescription().getName() + " 正在启用！");
        getLogger().info("Ciallo～(∠・ω< )⌒★");
        getLogger().info("==========================================");

        // 保存默认配置
        saveDefaultConfig();
        
        // 创建日志文件夹
        File logDir = new File(getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
            getLogger().info("创建日志文件夹...");
        }
        
        // 设置语言
        currentLanguage = getConfig().getString("settings.language", "zh_cn");
        loadLanguageFile();
        reloadMessages();
        
        // 启用 bStats
        if (getConfig().getBoolean("settings.metrics", true)) {
            int pluginId = 24853;
            new Metrics(this, pluginId);
            getLogger().info("正在启用 bStats 统计...");
        }

        // 检查并设置Vault依赖
        if (!setupEconomy()) {
            getLogger().severe("没有找到Vault插件！禁用插件中...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化存储系统
        setupStorage();
        
        // 初始化利率
        initializeRates();
        
        // 如果启用动态利率，启动定时更新任务
        if (getConfig().getBoolean("interest.dynamic-rate.enabled", false)) {
            long interval = getConfig().getLong("interest.dynamic-rate.update-interval", 24) * 20 * 3600;
            getServer().getScheduler().runTaskTimer(this, this::updateRates, interval, interval);
        }
        
        // 注册命令
        bankCommand = new BankCommand(this);
        getCommand("bank").setExecutor(bankCommand);
        getCommand("bank").setTabCompleter(bankCommand);
        
        // 初始化GUI
        bankGUI = new BankGUI(this);
        getServer().getPluginManager().registerEvents(new BankGUIListener(this), this);
        
        // 检查并注册 PAPI 扩展
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (new BankExpansion(this).register()) {
                getLogger().info("成功注册 PlaceholderAPI 扩展！");
                papiEnabled = true;
            } else {
                getLogger().warning("注册 PlaceholderAPI 扩展失败！");
            }
        } else {
            getLogger().info("未找到 PlaceholderAPI，变量功能已禁用。");
        }
        
        this.loanRateManager = new LoanRateManager(this);
        this.loanPenaltyManager = new LoanPenaltyManager(this);
        
        // 添加日志清理任务
        long cleanupInterval = getConfig().getLong("log.cleanup-interval", 24) * 20 * 3600; // 默认24小时
        getServer().getScheduler().runTaskTimer(this, () -> bankCommand.cleanupOldLogs(), cleanupInterval, cleanupInterval);
        
        // 注册时间变化监听器
        getServer().getPluginManager().registerEvents(new TimeChangeListener(this), this);
        
        // 只有在使用现实时间时才设置定时任务
        if (getConfig().getString("settings.time-type", "REAL").equalsIgnoreCase("REAL")) {
            setupInterestTask();
        }
        
        getLogger().info("银行插件已启动！");

        checkUpdate();
    }

    @Override
    public void onDisable() {
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName() + " 已禁用！");
        getLogger().info("Ciallo～(∠・ω< )⌒★");
        getLogger().info("==========================================");

        if (storageManager != null) {
            storageManager.close();
        }
    }

    private void setupStorage() {
        String storageType = getConfig().getString("storage-type", "DB");
        switch (storageType.toUpperCase()) {
            case "JSON":
                storageManager = new JsonStorage(this);
                break;
            case "MYSQL":
                storageManager = new MysqlStorage(this);
                break;
            default:
                storageManager = new SqliteStorage(this);
                break;
        }
        storageManager.init();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public Economy getEconomy() {
        return econ;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * 初始化利率系统
     * 包括活期和定期存款的各种期限利率
     */
    private void initializeRates() {
        demandRates = new HashMap<>();
        timeRates = new HashMap<>();
        
        // 检查时间类型配置（游戏时间或现实时间）
        String timeType = getConfig().getString("settings.time-type", "REAL");
        if (!timeType.equalsIgnoreCase("REAL") && !timeType.equalsIgnoreCase("GAME")) {
            getLogger().warning("时间类型配置无效，默认使用现实时间（REAL）");
        }
        
        if (getConfig().getBoolean("interest.dynamic-rate.enabled", false)) {
            // 动态利率模式下的初始化
            demandRates.put("default", getConfig().getDouble("interest.dynamic-rate.demand.base-rate", 3.0));
            demandRates.put("week", getConfig().getDouble("interest.demand.period-rates.week", 2.0));
            demandRates.put("month", getConfig().getDouble("interest.demand.period-rates.month", 3.0));
            demandRates.put("year", getConfig().getDouble("interest.demand.period-rates.year", 4.0));
            
            timeRates.put("week", getConfig().getDouble("interest.dynamic-rate.time.week.base-rate", 4.0));
            timeRates.put("month", getConfig().getDouble("interest.dynamic-rate.time.month.base-rate", 5.0));
            timeRates.put("year", getConfig().getDouble("interest.dynamic-rate.time.year.base-rate", 6.0));
        } else {
            // 固定利率模式下的初始化
            demandRates.put("default", getConfig().getDouble("interest.demand.annual-rate", 3.0));
            demandRates.put("week", getConfig().getDouble("interest.demand.period-rates.week", 2.0));
            demandRates.put("month", getConfig().getDouble("interest.demand.period-rates.month", 3.0));
            demandRates.put("year", getConfig().getDouble("interest.demand.period-rates.year", 4.0));
            
            timeRates.put("week", getConfig().getDouble("interest.time.period-rates.week.rate", 4.0));
            timeRates.put("month", getConfig().getDouble("interest.time.period-rates.month.rate", 5.0));
            timeRates.put("year", getConfig().getDouble("interest.time.period-rates.year.rate", 6.0));
        }
        lastRateUpdate = System.currentTimeMillis();
    }

    /**
     * 更新动态利率
     * 根据总存款量调整利率
     */
    private void updateRates() {
        // 如果未启用动态利率，直接返回
        if (!getConfig().getBoolean("interest.dynamic-rate.enabled", false)) {
            return;
        }

        // 计算当前总存款量
        double totalDeposits = calculateTotalDeposits();
        
        // 更新各类型利率
        updateRateForPeriod("demand", "default", totalDeposits);
        updateRateForPeriod("demand", "week", totalDeposits);
        updateRateForPeriod("demand", "month", totalDeposits);
        updateRateForPeriod("demand", "year", totalDeposits);
        
        updateRateForPeriod("time", "week", totalDeposits);
        updateRateForPeriod("time", "month", totalDeposits);
        updateRateForPeriod("time", "year", totalDeposits);
        
        lastRateUpdate = System.currentTimeMillis();
    }

    /**
     * 根据存款总量更新特定类型和期限的利率
     * @param type 存款类型（demand活期/time定期）
     * @param period 存款期限（default/week/month/year）
     * @param totalDeposits 当前总存款量
     */
    private void updateRateForPeriod(String type, String period, double totalDeposits) {
        // 构建配置路径
        String basePath;
        if (period.equals("default")) {
            basePath = "interest.dynamic-rate." + type + ".";
        } else {
            basePath = "interest.dynamic-rate." + type + "." + period + ".";
        }
        
        // 获取当前利率（根据存款类型从不同Map中获取）
        double currentRate = type.equals("demand") ? demandRates.get(period) : timeRates.get(period);
        // 获取利率的最小值和最大值限制
        double minRate = getConfig().getDouble(basePath + "min-rate");
        double maxRate = getConfig().getDouble(basePath + "max-rate");
        // 获取存款总量的低阈值和高阈值
        double lowThreshold = getConfig().getDouble("interest.dynamic-rate.thresholds.low");
        double highThreshold = getConfig().getDouble("interest.dynamic-rate.thresholds.high");
        // 获取利率调整步长
        double increaseStep = getConfig().getDouble("interest.dynamic-rate.adjustment.increase");
        double decreaseStep = getConfig().getDouble("interest.dynamic-rate.adjustment.decrease");

        // 根据存款总量调整利率
        if (totalDeposits < lowThreshold) {
            // 存款总量低于阈值时提高利率，但不超过最大值
            currentRate = Math.min(maxRate, currentRate + increaseStep);
        } else if (totalDeposits > highThreshold) {
            // 存款总量高于阈值时降低利率，但不低于最小值
            currentRate = Math.max(minRate, currentRate - decreaseStep);
        }

        // 更新对应类型的利率表
        if (type.equals("demand")) {
            demandRates.put(period, currentRate);
        } else {
            timeRates.put(period, currentRate);
        }
    }

    /**
     * 计算所有玩家的存款总量
     * @return 存款总量
     */
    private double calculateTotalDeposits() {
        StorageManager storage = getStorageManager();
        // 是否包含定期存款在总量计算中
        boolean includeTimeDeposits = getConfig().getBoolean("interest.dynamic-rate.include-time-deposits", true);
        double total = 0;
        
        // 遍历所有玩家计算存款总量
        for (UUID uuid : storage.getAllPlayers()) {
            // 加入活期存款
            total += storage.getBalance(uuid);
            // 如果配置包含定期存款，也加入计算
            if (includeTimeDeposits) {
                TimeDeposit timeDeposit = storage.getTimeDeposit(uuid);
                if (timeDeposit != null) {
                    total += timeDeposit.getAmount();
                }
            }
        }
        return total;
    }

    public double getDemandRate(String period) {
        return demandRates.getOrDefault(period, demandRates.get("default"));
    }

    public double getTimeRate(String period) {
        return timeRates.getOrDefault(period, timeRates.get("week"));
    }

    public Map<String, Double> getDemandRates() {
        return new HashMap<>(demandRates);
    }

    public Map<String, Double> getTimeRates() {
        return new HashMap<>(timeRates);
    }

    public long getLastRateUpdate() {
        return lastRateUpdate;
    }

    /**
     * 会员等级系统相关方法
     */
    public boolean isMembershipEnabled() {
        return getConfig().getBoolean("membership.enabled", false);
    }

    /**
     * 根据总余额获取会员等级
     * @param totalBalance 玩家总余额
     * @return 会员等级（diamond/platinum/gold/silver/bronze/none）
     */
    public String getMembershipLevel(double totalBalance) {
        if (!isMembershipEnabled()) return "";
        
        String[] levels = {"diamond", "platinum", "gold", "silver", "bronze"};
        for (String level : levels) {
            double requirement = getConfig().getDouble("membership.levels." + level + ".requirement", 0);
            if (totalBalance >= requirement) {
                return level;
            }
        }
        return "none";
    }

    public double getMembershipBonus(String level) {
        if (!isMembershipEnabled() || level.equals("none")) return 0.0;
        return getConfig().getDouble("membership.levels." + level + ".bonus-rate", 0.0);
    }

    public BankGUI getBankGUI() {
        return bankGUI;
    }

    public boolean isGUIEnabled() {
        return getConfig().getBoolean("gui.enabled", true);
    }

    public LoanRateManager getLoanRateManager() {
        return loanRateManager;
    }

    public LoanPenaltyManager getLoanPenaltyManager() {
        return loanPenaltyManager;
    }

    /**
     * 检查 PAPI 是否已启用
     * @return 是否启用了 PAPI
     */
    public boolean isPapiEnabled() {
        return papiEnabled;
    }

    public BankCommand getBankCommand() {
        return bankCommand;
    }

    /**
     * 加载语言文件
     * 如果指定语言文件不存在则回退到中文
     */
    public void loadLanguageFile() {
        // 确保lang目录存在
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
            getLogger().info("创建语言文件夹: " + langDir.getPath());
        }

        // 保存默认语言文件
        if (!new File(langDir, currentLanguage + ".yml").exists()) {
            saveResource("lang/" + currentLanguage + ".yml", false);
            getLogger().info("保存默认语言文件: " + currentLanguage + ".yml");
        }

        File langFile = new File(langDir, currentLanguage + ".yml");
        // getLogger().info("尝试加载语言文件: " + langFile.getAbsolutePath());
        
        if (!langFile.exists()) {
            // getLogger().warning("语言文件不存在: " + langFile.getAbsolutePath());
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // 检查语言文件是否成功加载
        if (langConfig.getKeys(false).isEmpty()) {
            // getLogger().warning("语言文件为空或加载失败，切换到默认中文");
            currentLanguage = "zh_cn";
            saveResource("lang/zh_cn.yml", false);
            langFile = new File(langDir, "zh_cn.yml");
            langConfig = YamlConfiguration.loadConfiguration(langFile);
        }
        
        // getLogger().info("语言文件加载完成，包含 " + langConfig.getKeys(true).size() + " 个配置项");
    }

    /**
     * 重新加载语言文件
     */
    public void reloadMessages() {
        File langFile = new File(getDataFolder(), "lang/" + getConfig().getString("settings.language", "zh_cn") + ".yml");
        if (!langFile.exists()) {
            saveResource("lang/zh_cn.yml", false);
        }
        
        try {
            String encoding = getConfig().getString("settings.language-file-encoding", "UTF-8");
            messages = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(langFile), encoding));
        } catch (Exception e) {
            // getLogger().warning("加载语言文件时出错: " + e.getMessage());
        }
    }

    /**
     * 获取指定路径的语言消息
     * @param path 消息路径
     * @return 格式化后的消息文本
     */
    public String getMessage(String path) {
        // getLogger().info("所有消息键: " + messages.getKeys(true));
        
        String message = messages.getString(path);
        if (message == null) {
            // getLogger().warning("找不到语言文件中的消息: " + path);
            return "§c消息未找到: " + path;
        }
        
        String prefix = messages.getString("prefix", "&a[银行系统] ");
        prefix = prefix.replace('&', '§');
        
        return message.replace('&', '§')
                     .replace("{prefix}", prefix);
    }

    /**
     * 获取带参数的语言消息
     * @param path 消息路径
     * @param args 替换参数
     * @return 格式化后的消息文本
     */
    public String getMessage(String path, Object... args) {
        String message = messages.getString(path, "Message not found: " + path);
        if (args != null && args.length > 0) {
            // 将所有参数转换为字符串
            String[] stringArgs = new String[args.length];
            for (int i = 0; i < args.length; i++) {
                stringArgs[i] = String.valueOf(args[i]);
            }
            message = String.format(message, (Object[]) stringArgs);
        }
        return message.replace("&", "§").replace("{prefix}", getConfig().getString("prefix", "§f[§6Bank§f]"));
    }

    /**
     * 获取当前语言
     * @return 当前语言代码
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * 检查插件更新
     * 通过GitHub API获取最新版本信息
     */
    private void checkUpdate() {
        getLogger().info("正在检查更新...");
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                // 获取当前版本号
                String currentVersion = getDescription().getVersion();
                // 构建GitHub API请求
                URI uri = new URI("https://api.github.com/repos/znc15/Bank/releases/latest");
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                
                // 处理404错误（仓库不存在或无发布版本）
                if (conn.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    getLogger().info("当前版本 " + currentVersion + " 已是最新版本！");
                    return;
                }

                // 读取API响应
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // 解析JSON响应获取最新版本信息
                JSONObject json = new JSONObject(response.toString());
                String latestVersion = json.getString("tag_name").replace("v", "");
                String downloadUrl = json.getString("html_url");

                // 比较版本并输出更新信息
                if (shouldUpdate(currentVersion, latestVersion)) {
                    getLogger().warning("发现新版本: " + latestVersion);
                    getLogger().warning("当前版本: " + currentVersion);
                    getLogger().warning("下载地址: " + downloadUrl);
                } else {
                    getLogger().info("当前版本 " + currentVersion + " 已是最新版本！");
                }
            } catch (Exception e) {
                getLogger().info("检查更新失败，当前版本 " + getDescription().getVersion() + " 可能是最新版本");
                if (getConfig().getBoolean("settings.debug", false)) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 比较版本号，判断是否需要更新
     * @param currentVersion 当前版本
     * @param latestVersion 最新版本
     * @return 如果需要更新返回true
     */
    private boolean shouldUpdate(String currentVersion, String latestVersion) {
        // 将版本号分割为数组
        String[] current = currentVersion.split("\\.");
        String[] latest = latestVersion.split("\\.");
        
        // 逐位比较版本号
        for (int i = 0; i < Math.min(current.length, latest.length); i++) {
            int currentPart = Integer.parseInt(current[i]);
            int latestPart = Integer.parseInt(latest[i]);
            
            if (currentPart < latestPart) {
                return true;
            } else if (currentPart > latestPart) {
                return false;
            }
        }
        
        // 如果前面的版本号都相同，较长的版本号更新
        return latest.length > current.length;
    }

    /**
     * 获取经过的时间（考虑游戏时间和现实时间）
     */
    public long getElapsedTime(long startTime) {
        String timeType = getConfig().getString("settings.time-type", "REAL");
        
        if (timeType.equalsIgnoreCase("GAME")) {
            long currentFullDays = getServer().getWorlds().get(0).getFullTime() / 24000L;
            long startFullDays = startTime / 24000L;
            
            getLogger().info("[调试] 计算游戏时间");
            getLogger().info("[调试] 当前游戏天数: " + currentFullDays);
            getLogger().info("[调试] 开始游戏天数: " + startFullDays);
            
            if (currentFullDays > startFullDays) {
                long daysPassed = currentFullDays - startFullDays;
                getLogger().info("[调试] 经过天数: " + daysPassed);
                return daysPassed * 24 * 60 * 60 * 1000L;
            } else {
                return System.currentTimeMillis() - startTime;
            }
        } else {
            return System.currentTimeMillis() - startTime;
        }
    }

    /**
     * 重新加载GUI配置
     */
    public void reloadGUI() {
        if (bankGUI != null) {
            bankGUI.reloadConfig();
            getLogger().info("GUI配置已重新加载");
        }
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    // 添加日志记录方法
    public void logTransaction(String playerName, String action, double amount, String details) {
        // 获取日志目录
        File logDir = new File(getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
            getLogger().info("创建日志目录: " + logDir.getPath());
        }

        // 按日期创建日志文件
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String today = dateFormat.format(new Date());
        File logFile = new File(logDir, today + ".log");

        try {
            // 确保日志文件存在
            if (!logFile.exists()) {
                logFile.createNewFile();
                getLogger().info("创建新日志文件: " + logFile.getPath());
            }

            // 写入日志
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                String timestamp = timeFormat.format(new Date());
                String logEntry = String.format("[%s] %s - %s: %.2f - %s", 
                    timestamp, playerName, action, amount, details);
                writer.println(logEntry);
                getLogger().info("记录日志: " + logEntry);
            }
        } catch (IOException e) {
            getLogger().warning("写入日志失败: " + e.getMessage());
            if (getConfig().getBoolean("settings.debug", false)) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置利息计算任务
     */
    private void setupInterestTask() {
        int payoutHour = getConfig().getInt("interest.payout-hour", 0);
        
        // 计算下次执行时间
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, payoutHour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        
        if (next.before(Calendar.getInstance())) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        // 计算延迟时间（以tick为单位）
        long delay = (next.getTimeInMillis() - System.currentTimeMillis()) / 50;
        // 一天的tick数
        long period = 24 * 60 * 60 * 20;
        
        // 启动定时任务
        getServer().getScheduler().runTaskTimer(this, () -> {
            getLogger().info("开始计算每日利息...");
            calculateDailyInterest();
        }, delay, period);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        getLogger().info("下次利息发放时间: " + sdf.format(next.getTime()));
    }

    /**
     * 计算并发放每日利息
     */
    public void calculateDailyInterest() {
        StorageManager storage = getStorageManager();
        double minBalance = getConfig().getDouble("interest.minimum-balance", 1000.0);
        
        for (UUID uuid : storage.getAllPlayers()) {
            double balance = storage.getBalance(uuid);
            if (balance >= minBalance) {
                // 计算活期利息
                double demandRate = getDemandRate("default");
                String level = getMembershipLevel(balance);
                double bonus = getMembershipBonus(level);
                double dailyRate = (demandRate + bonus) / 100.0 / 365.0;
                double interest = balance * dailyRate;
                
                // 更新余额
                storage.setBalance(uuid, balance + interest);
                
                // 获取玩家名称并记录日志
                String playerName = getServer().getOfflinePlayer(uuid).getName();
                if (playerName != null) {
                    // 记录日志
                    logTransaction(playerName, "利息", interest, 
                        String.format("活期利息 (利率: %.2f%% + %.2f%%)", demandRate, bonus));
                    
                    // 如果玩家在线，发送消息通知
                    if (getServer().getPlayer(uuid) != null) {
                        String formattedMessage = getMessage("interest.received")
                            .replace("%amount%", String.format("%.2f", interest));
                        getServer().getPlayer(uuid).sendMessage(formattedMessage);
                    }
                }
            }
        }
        
        getLogger().info("每日利息计算完成！");
    }
}
