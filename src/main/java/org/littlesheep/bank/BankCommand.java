package org.littlesheep.bank;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.littlesheep.bank.storage.StorageManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;
import java.util.Date;
import java.util.UUID;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;


public class BankCommand implements CommandExecutor, TabCompleter {
    // 经济系统接口
    private final Economy econ;
    // 插件主类实例
    private final Bank plugin;
    // 所有可用的子命令列表
    private final List<String> subCommands = Arrays.asList(
        "help", "deposit", "withdraw", "balance",
        "timedeposit", "timewithdraw", "rates", "loan",
        "repay", "confirm" , "admin" , "logs" , "reload"
    );

    // 定期存款的周期类型
    private final List<String> periodTypes = Arrays.asList("week", "month", "year");

    public BankCommand(Bank plugin) {
        this.plugin = plugin;
        this.econ = plugin.getEconomy();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 第一个参数的补全提示 - 返回所有可用的子命令
        if (args.length == 1) {
            return subCommands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        // 存款/取款命令的金额补全提示
        if (args.length == 2 && (args[0].equalsIgnoreCase("deposit") || 
                                args[0].equalsIgnoreCase("withdraw") ||
                                args[0].equalsIgnoreCase("timedeposit"))) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("1000");
            suggestions.add("5000");
            suggestions.add("10000");
            return suggestions;
        }

        // 定期存款的周期类型补全提示
        if (args.length == 3 && args[0].equalsIgnoreCase("timedeposit")) {
            return periodTypes.stream()
                    .filter(period -> period.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("common.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            plugin.getBankGUI().openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                showHelp(player);
                break;
            case "balance":
                showAllAccountInfo(player);
                break;
            case "rates":
                showRates(player);
                break;
            case "confirm":
                handleConfirm(player);
                break;
            case "deposit":
            case "withdraw":
            case "timedeposit":
            case "timewithdraw":
            case "loan":
            case "repay":
                if (args.length < 2 && !args[0].equalsIgnoreCase("timewithdraw")) {
                    player.sendMessage(plugin.getMessage("common.usage", "/bank " + args[0] + " <金额>"));
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(plugin.getMessage("common.invalid-amount"));
                    return true;
                }

                if (amount <= 0) {
                    player.sendMessage(plugin.getMessage("common.amount-positive"));
                    return true;
                }

                switch (args[0].toLowerCase()) {
                    case "deposit":
                        deposit(player, amount);
                        break;
                    case "withdraw":
                        withdraw(player, amount);
                        break;
                    case "timedeposit":
                        if (args.length < 3) {
                            player.sendMessage(plugin.getMessage("time-deposit.usage"));
                            player.sendMessage(plugin.getMessage("time-deposit.period-options"));
                            return true;
                        }
                        timeDeposit(player, amount, args[2]);
                        break;
                    case "timewithdraw":
                        timeWithdraw(player);
                        break;
                    case "loan":
                        handleLoan(player, args);
                        break;
                    case "repay":
                        handleRepay(player);
                        break;
                }
                break;
            case "admin":
                handleAdmin(player, args);
                break;
            case "logs":
                if (!player.hasPermission("bank.admin")) {
                    player.sendMessage(plugin.getMessage("admin.no-permission-log"));
                    return true;
                }

                if (args.length == 1) {
                    // 显示最近的日志，默认第一页
                    showRecentLogs(player, 1);
                    return true;
                }

                if (args.length == 2) {
                    try {
                        // 显示指定页码的最近日志
                        int page = Integer.parseInt(args[1]);
                        showRecentLogs(player, page);
                    } catch (NumberFormatException e) {
                        // 如果第二个参数不是数字，则认为是玩家名称搜索
                        searchPlayerLogs(player, args[1], 1);
                    }
                    return true;
                }

                if (args.length == 3) {
                    // 搜索指定玩家的日志并显示指定页码
                    try {
                        int page = Integer.parseInt(args[2]);
                        searchPlayerLogs(player, args[1], page);
                    } catch (NumberFormatException e) {
                        player.sendMessage(plugin.getMessage("invalid-page"));
                    }
                    return true;
                }
                break;
            case "reload":
                if (!sender.hasPermission("bank.admin.reload")) {
                    sender.sendMessage(plugin.getMessage("admin.no-permission-reload"));
                    return true;
                }
                
                try {
                    // 重新加载配置文件
                    plugin.reloadConfig();
                    
                    // 重新加载语言文件
                    plugin.reloadMessages();
                    
                    // 重新加载GUI
                    plugin.reloadGUI();
                    
                    sender.sendMessage(plugin.getMessage("admin.reload-success"));
                    plugin.getLogger().info("配置文件已重新加载");
                } catch (Exception e) {
                    sender.sendMessage(plugin.getMessage("admin.reload-failed"));
                    plugin.getLogger().warning("重新加载配置文件时发生错误: " + e.getMessage());
                }
                break;
            default:
                player.sendMessage(plugin.getMessage("common.invalid-command"));
        }
        return true;
    }

    /**
     * 处理存款操作
     * @param player 玩家
     * @param amount 存款金额
     */
    private void deposit(Player player, double amount) {
        // 检查玩家现金是否足够
        if (econ.getBalance(player) < amount) {
            player.sendMessage("§c你没有足够的钱！");
            return;
        }
        
        StorageManager storage = plugin.getStorageManager();
        double currentBalance = storage.getBalance(player.getUniqueId());
        
        // 扣除现金并增加存款余额
        econ.withdrawPlayer(player, amount);
        storage.setBalance(player.getUniqueId(), currentBalance + amount);
        
        // 记录日志
        plugin.logTransaction(player.getName(), "存款", amount, "活期存款");
        
        // 发送存款成功消息
        player.sendMessage("§a=== 存款成功 ===");
        player.sendMessage(String.format("§a存入金额：%.2f", amount));
        player.sendMessage(String.format("§a活期余额：%.2f", currentBalance + amount));
        player.sendMessage(String.format("§a现金余额：%.2f", econ.getBalance(player)));
    }

    /**
     * 处理取款操作
     * @param player 玩家
     * @param amount 取款金额
     */
    private void withdraw(Player player, double amount) {
        StorageManager storage = plugin.getStorageManager();
        double currentBalance = storage.getBalance(player.getUniqueId());
        
        // 检查账户余额是否充足
        if (currentBalance < amount) {
            player.sendMessage("§c你的银行账户余额不足！");
            return;
        }
        
        // 扣除存款并增加现金
        storage.setBalance(player.getUniqueId(), currentBalance - amount);
        econ.depositPlayer(player, amount);
        
        // 记录日志
        plugin.logTransaction(player.getName(), "取款", amount, "活期取款");
        
        // 发送取款成功消息
        player.sendMessage("§a=== 取款成功 ===");
        player.sendMessage(String.format("§a取出金额：%.2f", amount));
        player.sendMessage(String.format("§a活期余额：%.2f", currentBalance - amount));
        player.sendMessage(String.format("§a现金余额：%.2f", econ.getBalance(player)));
    }

    /**
     * 显示账户所有信息
     * 包括:活期存款、定期存款、贷款信息、会员等级等
     * @param player 玩家
     */
    private void showAllAccountInfo(Player player) {
        StorageManager storage = plugin.getStorageManager();
        double balance = storage.getBalance(player.getUniqueId());
        TimeDeposit timeDeposit = storage.getTimeDeposit(player.getUniqueId());
        double cashBalance = econ.getBalance(player);
        double demandRate = plugin.getDemandRate("default");
        double timeRate = plugin.getTimeRate("week");
        double minBalance = plugin.getConfig().getDouble("interest.minimum-balance", 1000.0);
        double totalBalance = balance + timeDeposit.getAmount();
        
        // 显示基本账户信息
        player.sendMessage(plugin.getMessage("account.title"));
        player.sendMessage(plugin.getMessage("account.cash", cashBalance));
        
        // 显示活期存款信息
        player.sendMessage(plugin.getMessage("account.demand-title"));
        player.sendMessage(plugin.getMessage("account.demand-amount", balance));
        player.sendMessage(plugin.getMessage("account.demand-rate", demandRate));
        
        // 显示利息计算信息
        if (balance >= minBalance) {
            double dailyInterest = balance * (demandRate / 100.0 / 365);
            player.sendMessage(plugin.getMessage("account.demand-active"));
            player.sendMessage(plugin.getMessage("account.demand-interest", dailyInterest));
        } else {
            player.sendMessage(plugin.getMessage("account.demand-inactive"));
            player.sendMessage(plugin.getMessage("account.demand-activation", minBalance - balance));
        }

        player.sendMessage(plugin.getMessage("account.time-deposit-rates.title"));
        player.sendMessage(plugin.getMessage("account.time-deposit-rates.week", timeRate));
        player.sendMessage(plugin.getMessage("account.time-deposit-rates.month", plugin.getTimeRate("month")));
        player.sendMessage(plugin.getMessage("account.time-deposit-rates.year", plugin.getTimeRate("year")));

        if (timeDeposit.getAmount() > 0) {
            long daysPassed = (System.currentTimeMillis() - timeDeposit.getDepositDate()) / (1000 * 60 * 60 * 24);
            int minDays = plugin.getConfig().getInt("interest.minimum-time-deposit-days", 7);
            long daysLeft = Math.max(0, minDays - daysPassed);
            
            double expectedInterest = timeDeposit.getAmount() * (timeRate / 100.0) * (minDays / 365.0);
            double currentInterest = timeDeposit.getAmount() * (timeRate / 100.0) * (daysPassed / 365.0);
            
            player.sendMessage(plugin.getMessage("account.time-deposit-info.title"));
            player.sendMessage(plugin.getMessage("account.time-deposit-info.amount", timeDeposit.getAmount()));
            player.sendMessage(plugin.getMessage("account.time-deposit-info.rate", timeRate));
            player.sendMessage(plugin.getMessage("account.time-deposit-info.days-passed", daysPassed));
            
            if (daysLeft > 0) {
                player.sendMessage(plugin.getMessage("account.time-deposit-info.days-left", daysLeft));
                player.sendMessage(plugin.getMessage("account.time-deposit-info.expected-interest", expectedInterest));
            } else {
                player.sendMessage(plugin.getMessage("account.time-deposit-info.status-mature"));
                player.sendMessage(plugin.getMessage("account.time-deposit-info.current-interest", currentInterest));
            }
        }

        if (plugin.isMembershipEnabled()) {
            String level = plugin.getMembershipLevel(totalBalance);
            double bonus = plugin.getMembershipBonus(level);
            
            player.sendMessage(plugin.getMessage("account.membership.title"));
            player.sendMessage(plugin.getMessage("account.membership.current-level", formatMembershipLevel(level)));
            player.sendMessage(plugin.getMessage("account.membership.bonus-rate", bonus));
            
            if (!level.equals("diamond")) {
                String nextLevel = getNextLevel(level);
                double nextRequirement = plugin.getConfig().getDouble("membership.levels." + nextLevel + ".requirement");
                player.sendMessage(plugin.getMessage("account.membership.upgrade-needed", nextRequirement - totalBalance));
            }
        }
        
        double totalAssets = cashBalance + balance + timeDeposit.getAmount();
        player.sendMessage(plugin.getMessage("account.total-assets.title"));
        player.sendMessage(plugin.getMessage("account.total-assets.amount", totalAssets));
        
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, plugin.getConfig().getInt("interest.payout-hour", 0));
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        
        if (next.before(Calendar.getInstance())) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        player.sendMessage(plugin.getMessage("account.next-payout", sdf.format(next.getTime())));

        if (plugin.getConfig().getBoolean("interest.dynamic-rate.enabled", false)) {
            player.sendMessage(plugin.getMessage("account.dynamic-rate.title"));
            player.sendMessage("§a利率类型：§e动态");
            player.sendMessage("§a上次调整：" + sdf.format(new Date(plugin.getLastRateUpdate())));
            player.sendMessage(String.format("§a活期利率范围：%.1f%% - %.1f%%",
                plugin.getConfig().getDouble("interest.dynamic-rate.demand.min-rate"),
                plugin.getConfig().getDouble("interest.dynamic-rate.demand.max-rate")));
            player.sendMessage(String.format("§a定期利率范围：%.1f%% - %.1f%%",
                plugin.getConfig().getDouble("interest.dynamic-rate.time.min-rate"),
                plugin.getConfig().getDouble("interest.dynamic-rate.time.max-rate")));
        }

        // 添加贷款信息显示
        Loan loan = plugin.getStorageManager().getLoan(player.getUniqueId());
        if (loan != null && !loan.isPaid()) {
            player.sendMessage(plugin.getMessage("account.loan-info.title"));
            player.sendMessage(plugin.getMessage("account.loan-info.amount", loan.getAmount()));
            player.sendMessage("§a贷款日期：" + sdf.format(new Date(loan.getLoanDate())));
            player.sendMessage(plugin.getMessage("account.loan-info.period", loan.getDays()));
            
            long daysLeft = loan.getDays() - (System.currentTimeMillis() - loan.getLoanDate()) / (1000 * 60 * 60 * 24);
            player.sendMessage(plugin.getMessage("account.loan-info.days-left", Math.max(0, daysLeft)));
        }
    }

    private String formatMembershipLevel(String level) {
        return plugin.getMessage("membership.levels." + level.toLowerCase());
    }

    private String getNextLevel(String currentLevel) {
        switch (currentLevel) {
            case "none": return "bronze";
            case "bronze": return "silver";
            case "silver": return "gold";
            case "gold": return "platinum";
            case "platinum": return "diamond";
            default: return "diamond";
        }
    }

    /**
     * 处理定期存款操作
     * @param player 玩家
     * @param amount 存款金额
     * @param period 存款周期(week/month/year)
     */
    private void timeDeposit(Player player, double amount, String period) {
        // 验证周期类型是否有效
        if (!Arrays.asList("week", "month", "year").contains(period)) {
            player.sendMessage(plugin.getMessage("common.invalid-period"));
            return;
        }

        // 检查玩家现金是否足够
        if (econ.getBalance(player) < amount) {
            player.sendMessage(plugin.getMessage("common.insufficient-funds"));
            return;
        }
        
        StorageManager storage = plugin.getStorageManager();
        TimeDeposit currentDeposit = storage.getTimeDeposit(player.getUniqueId());
        
        // 检查是否已有定期存款
        if (currentDeposit.getAmount() > 0) {
            player.sendMessage(plugin.getMessage("time-deposit.exists"));
            return;
        }
        
        // 执行定期存款操作
        econ.withdrawPlayer(player, amount);
        storage.setTimeDeposit(player.getUniqueId(), 
            new TimeDeposit(amount, System.currentTimeMillis(), period));
        
        // 记录日志
        plugin.logTransaction(player.getName(), "定期存款", amount, "期限: " + period);
        
        // 发送成功消息
        String periodText = period.equals("week") ? "周" : period.equals("month") ? "月" : "年";
        player.sendMessage(plugin.getMessage("time-deposit.success", amount, periodText));
        showTimeDepositInfo(player);
    }

    private void timeWithdraw(Player player) {
        StorageManager storage = plugin.getStorageManager();
        TimeDeposit deposit = storage.getTimeDeposit(player.getUniqueId());
        
        if (deposit.getAmount() <= 0) {
            player.sendMessage(plugin.getMessage("time-deposit.no-deposit"));
            return;
        }

        int requiredDays;
        switch (deposit.getPeriod()) {
            case "week": requiredDays = 7; break;
            case "month": requiredDays = 30; break;
            case "year": requiredDays = 365; break;
            default: requiredDays = 7;
        }

        long daysPassed = (System.currentTimeMillis() - deposit.getDepositDate()) / (1000 * 60 * 60 * 24);

        if (daysPassed < requiredDays) {
            player.sendMessage(plugin.getMessage("time-deposit.early-withdraw", requiredDays - daysPassed));
            return;
        }

        double timeRate = plugin.getTimeRate(deposit.getPeriod());
        double interest = deposit.getAmount() * (timeRate / 100.0) * (daysPassed / 365.0);
        
        storage.setTimeDeposit(player.getUniqueId(), new TimeDeposit(0, 0, ""));
        econ.depositPlayer(player, deposit.getAmount() + interest);
        
        // 记录日志
        plugin.logTransaction(player.getName(), "定期取款", deposit.getAmount() + interest, 
            String.format("本金: %.2f, 利息: %.2f", deposit.getAmount(), interest));
        
        player.sendMessage(plugin.getMessage("time-deposit.withdraw-success", deposit.getAmount(), interest));
    }

    private void showTimeDepositInfo(Player player) {
        StorageManager storage = plugin.getStorageManager();
        TimeDeposit timeDeposit = storage.getTimeDeposit(player.getUniqueId());
        long depositDate = timeDeposit.getDepositDate();
        double timeRate = plugin.getConfig().getDouble("interest.time-annual-rate", 6.0);
        int minDays = plugin.getConfig().getInt("interest.minimum-time-deposit-days", 7);
        long daysPassed = (System.currentTimeMillis() - depositDate) / (1000 * 60 * 60 * 24);
        long daysLeft = Math.max(0, minDays - daysPassed);

        player.sendMessage(plugin.getMessage("time-deposit.info"));
        player.sendMessage(plugin.getMessage("time-deposit.amount", timeDeposit.getAmount()));
        player.sendMessage(plugin.getMessage("time-deposit.rate", timeRate));
        player.sendMessage(plugin.getMessage("time-deposit.days-passed", daysPassed));
        
        if (daysLeft > 0) {
            player.sendMessage(plugin.getMessage("time-deposit.days-left", daysLeft));
        } else {
            player.sendMessage(plugin.getMessage("time-deposit.status-mature"));
        }
    }

    private void showRates(Player player) {
        player.sendMessage(plugin.getMessage("rates.title"));
        
        player.sendMessage(plugin.getMessage("rates.demand-title"));
        player.sendMessage(plugin.getMessage("rates.demand-base", plugin.getDemandRate("default")));
        
        player.sendMessage(plugin.getMessage("rates.time-title"));
        player.sendMessage(plugin.getMessage("rates.week", plugin.getTimeRate("week")));
        player.sendMessage(plugin.getMessage("rates.month", plugin.getTimeRate("month")));
        player.sendMessage(plugin.getMessage("rates.year", plugin.getTimeRate("year")));

        if (plugin.getConfig().getBoolean("interest.dynamic-rate.enabled", false)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            player.sendMessage(plugin.getMessage("rates.dynamic-title"));
            player.sendMessage(plugin.getMessage("rates.last-update", sdf.format(new Date(plugin.getLastRateUpdate()))));
            player.sendMessage(plugin.getMessage("rates.demand-range",
                plugin.getConfig().getDouble("interest.dynamic-rate.demand.min-rate"),
                plugin.getConfig().getDouble("interest.dynamic-rate.demand.max-rate")));
            player.sendMessage(plugin.getMessage("rates.time-range",
                plugin.getConfig().getDouble("interest.dynamic-rate.time.min-rate"),
                plugin.getConfig().getDouble("interest.dynamic-rate.time.max-rate")));
        }

        if (plugin.isMembershipEnabled()) {
            player.sendMessage(plugin.getMessage("membership.bonus-info.title"));
            player.sendMessage(plugin.getMessage("membership.bonus-info.bronze"));
            player.sendMessage(plugin.getMessage("membership.bonus-info.silver"));
            player.sendMessage(plugin.getMessage("membership.bonus-info.gold"));
            player.sendMessage(plugin.getMessage("membership.bonus-info.platinum"));
            player.sendMessage(plugin.getMessage("membership.bonus-info.diamond"));
        }

        player.sendMessage(plugin.getMessage("rates.tip"));
    }

    private void showHelp(Player player) {
        player.sendMessage(plugin.getMessage("help.title"));
        player.sendMessage(plugin.getMessage("help.commands.help"));
        player.sendMessage(plugin.getMessage("help.commands.deposit"));
        player.sendMessage(plugin.getMessage("help.commands.withdraw"));
        player.sendMessage(plugin.getMessage("help.commands.balance"));
        player.sendMessage(plugin.getMessage("help.commands.rates"));
        player.sendMessage(plugin.getMessage("help.commands.timedeposit"));
        player.sendMessage(plugin.getMessage("help.commands.timewithdraw"));
        player.sendMessage(plugin.getMessage("help.commands.confirm"));
        player.sendMessage(plugin.getMessage("help.commands.loan"));
        player.sendMessage(plugin.getMessage("help.commands.repay"));
        player.sendMessage(plugin.getMessage("help.footer"));
        player.sendMessage(plugin.getMessage("help.tip"));
        if (player.hasPermission("bank.admin")) {
            player.sendMessage(plugin.getMessage("help.admin"));
        }
    }

    /**
     * 处理贷款申请
     * @param player 玩家
     * @param args 命令参数
     */
    public void handleLoan(Player player, String[] args) {
        // 检查命令参数
        if (args.length < 2) {
            player.sendMessage(plugin.getMessage("loan.usage"));
            return;
        }

        double amount;
        int days;
        try {
            // 解析贷款金额和期限
            amount = Double.parseDouble(args[1]);
            days = args.length > 2 ? Integer.parseInt(args[2]) : 7; // 默认7天
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("loan.invalid-number"));
            return;
        }

        // 检查贷款系统是否启用
        if (!plugin.getConfig().getBoolean("loan.enabled", true)) {
            player.sendMessage(plugin.getMessage("loan.system-disabled"));
            return;
        }

        // 检查是否有未还清的贷款
        Loan currentLoan = plugin.getStorageManager().getLoan(player.getUniqueId());
        if (currentLoan != null && !currentLoan.isPaid()) {
            player.sendMessage(plugin.getMessage("loan.existing-loan"));
            return;
        }

        // 检查贷款限额和期限
        double maxAmount = plugin.getConfig().getDouble("loan.max-amount", 100000);
        int maxDays = plugin.getConfig().getInt("loan.max-days", 30);
        
        if (amount > maxAmount) {
            player.sendMessage(plugin.getMessage("loan.max-amount", maxAmount));
            return;
        }
        
        if (days > maxDays) {
            player.sendMessage(plugin.getMessage("loan.max-period", maxDays));
            return;
        }

        // 检查会员等级要求
        String level = plugin.getMembershipLevel(plugin.getStorageManager().getBalance(player.getUniqueId()));
        String minLevel = plugin.getConfig().getString("loan.requirements.min-level", "bronze");
        if (!meetsLevelRequirement(level, minLevel)) {
            player.sendMessage(plugin.getMessage("loan.level-insufficient"));
            return;
        }

        // 创建贷款并发放资金
        Loan loan = new Loan(amount, System.currentTimeMillis(), days);
        plugin.getStorageManager().setLoan(player.getUniqueId(), loan);
        plugin.getEconomy().depositPlayer(player, amount);
        
        // 记录日志
        plugin.logTransaction(player.getName(), "贷款", amount, 
            String.format("期限: %d天, 利率: %.2f%%", days, plugin.getLoanRateManager().getCurrentRate()));
        
        // 显示贷款信息
        double rate = plugin.getLoanRateManager().getCurrentRate();
        player.sendMessage(plugin.getMessage("loan.success-title"));
        player.sendMessage(plugin.getMessage("loan.success-amount", amount));
        player.sendMessage(plugin.getMessage("loan.success-period", days));
        player.sendMessage(plugin.getMessage("loan.success-rate", rate));
        player.sendMessage(plugin.getMessage("loan.success-due", amount * (1 + rate/100)));
        player.sendMessage(plugin.getMessage("loan.warning"));
    }

    private void handleRepay(Player player) {
        Loan loan = plugin.getStorageManager().getLoan(player.getUniqueId());
        if (loan == null || loan.isPaid()) {
            player.sendMessage(plugin.getMessage("loan.no-loan"));
            return;
        }

        double rate = plugin.getLoanRateManager().getCurrentRate();
        long elapsedTime = plugin.getElapsedTime(loan.getLoanDate());
        long days = elapsedTime / (1000 * 60 * 60 * 24);
        double interest = loan.getAmount() * (rate/100) * (days/365.0);
        double totalDue = loan.getAmount() + interest;

        if (plugin.getEconomy().getBalance(player) < totalDue) {
            player.sendMessage(plugin.getMessage("loan.insufficient-funds", totalDue, plugin.getEconomy().getBalance(player)));
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, totalDue);
        loan.setPaid(true);
        plugin.getStorageManager().setLoan(player.getUniqueId(), loan);
        
        // 记录日志
        plugin.logTransaction(player.getName(), "还款", totalDue, 
            String.format("本金: %.2f, 利息: %.2f", loan.getAmount(), interest));
        
        player.sendMessage(plugin.getMessage("loan.repay-success-title"));
        player.sendMessage(plugin.getMessage("loan.repay-principal", loan.getAmount()));
        player.sendMessage(plugin.getMessage("loan.repay-interest", interest));
        player.sendMessage(plugin.getMessage("loan.repay-total", totalDue));
    }

    /**
     * 检查会员等级要求
     * @param currentLevel 当前等级
     * @param requiredLevel 要求等级
     * @return 是否满足要求
     */
    private boolean meetsLevelRequirement(String currentLevel, String requiredLevel) {
        List<String> levels = Arrays.asList("none", "bronze", "silver", "gold", "platinum", "diamond");
        int currentIndex = levels.indexOf(currentLevel);
        int requiredIndex = levels.indexOf(requiredLevel);
        return currentIndex >= requiredIndex;
    }

    /**
     * 处理管理员命令
     * @param player 管理员
     * @param args 命令参数
     */
    private void handleAdmin(Player player, String[] args) {
        // 检查管理员权限
        if (!player.hasPermission("bank.admin")) {
            player.sendMessage(plugin.getMessage("admin.no-permission"));
            return;
        }

        // 显示管理员帮助信息
        if (args.length < 2) {
            showAdminHelp(player);
            return;
        }

        // 处理不同的管理员子命令
        switch (args[1].toLowerCase()) {
            case "balance":
                // 余额管理命令
                if (!player.hasPermission("bank.admin.balance")) {
                    player.sendMessage(plugin.getMessage("admin.no-permission-balance"));
                    return;
                }
                handleBalanceAdmin(player, args);
                break;

            case "loan":
                // 贷款管理命令
                if (!player.hasPermission("bank.admin.loan")) {
                    player.sendMessage(plugin.getMessage("admin.no-permission-loan"));
                    return;
                }
                handleLoanAdmin(player, args);
                break;

            case "log":
                if (!player.hasPermission("bank.admin.log")) {
                    plugin.getLogger().info("消息内容: " + plugin.getMessages().getString("admin.no-permission-log"));
                    player.sendMessage(plugin.getMessage("admin.no-permission-log"));
                    return;
                }
                handleLogAdmin(player, args);
                break;

            default:
                showAdminHelp(player);
        }
    }

    private void handleBalanceAdmin(Player player, String[] args) {
        if (args.length < 5) {
            player.sendMessage(plugin.getMessage("admin.usage-balance"));
            return;
        }

        String action = args[2].toLowerCase();
        String targetName = args[3];
        Player target = plugin.getServer().getPlayer(targetName);
        String reason = args.length > 5 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : "无";

        if (target == null) {
            player.sendMessage(plugin.getMessage("admin.player-not-found", targetName));
            return;
        }

        try {
            double amount = Double.parseDouble(args[4]);
            if (amount < 0) {
                player.sendMessage(plugin.getMessage("admin.negative-amount"));
                return;
            }

            UUID targetUUID = target.getUniqueId();
            StorageManager storage = plugin.getStorageManager();
            double currentBalance = storage.getBalance(targetUUID);

            switch (action) {
                case "set":
                    storage.setBalance(targetUUID, amount);
                    player.sendMessage(plugin.getMessage("admin.balance-set", targetName, amount, reason));
                    target.sendMessage(plugin.getMessage("admin.notify-set", amount, reason));
                    break;

                case "add":
                    storage.setBalance(targetUUID, currentBalance + amount);
                    player.sendMessage(plugin.getMessage("admin.balance-add", targetName, amount, reason));
                    target.sendMessage(plugin.getMessage("admin.notify-add", amount, reason));
                    break;

                case "remove":
                    if (currentBalance < amount) {
                        player.sendMessage(plugin.getMessage("admin.insufficient-balance"));
                        return;
                    }
                    storage.setBalance(targetUUID, currentBalance - amount);
                    player.sendMessage(plugin.getMessage("admin.balance-remove", targetName, amount, reason));
                    target.sendMessage(plugin.getMessage("admin.notify-remove", amount, reason));
                    break;
            }
            
            // 记录操作日志
            logAdminAction(player, action, target, amount, reason);
            
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("admin.invalid-number"));
        }
    }

    private void handleLoanAdmin(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(plugin.getMessage("admin.usage-loan"));
            return;
        }

        String action = args[2].toLowerCase();
        String targetName = args[3];
        Player target = plugin.getServer().getPlayer(targetName);
        String reason = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "无";

        if (target == null) {
            player.sendMessage(plugin.getMessage("admin.player-not-found", targetName));
            return;
        }

        UUID targetUUID = target.getUniqueId();
        StorageManager storage = plugin.getStorageManager();
        Loan loan = storage.getLoan(targetUUID);

        switch (action) {
            case "clear":
                if (loan != null && !loan.isPaid()) {
                    loan.setPaid(true);
                    storage.setLoan(targetUUID, loan);
                    player.sendMessage(plugin.getMessage("admin.loan-cleared", targetName, reason));
                    target.sendMessage(plugin.getMessage("admin.notify-cleared", reason));
                    logAdminAction(player, "clearloan", target, loan.getAmount(), reason);
                } else {
                    player.sendMessage(plugin.getMessage("admin.no-loan"));
                }
                break;

            case "info":
                if (loan != null && !loan.isPaid()) {
                    player.sendMessage(plugin.getMessage("admin.loan-info-title"));
                    player.sendMessage(plugin.getMessage("admin.loan-info-amount", loan.getAmount()));
                    player.sendMessage(plugin.getMessage("admin.loan-info-date", new Date(loan.getLoanDate())));
                    player.sendMessage(plugin.getMessage("admin.loan-info-period", loan.getDays()));
                    long daysLeft = (loan.getLoanDate() + loan.getDays() * 24L * 60L * 60L * 1000L 
                        - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L);
                    player.sendMessage(plugin.getMessage("admin.loan-info-days-left", Math.max(0, daysLeft)));
                } else {
                    player.sendMessage(plugin.getMessage("admin.no-loan"));
                }
                break;
        }
    }

    private void handleLogAdmin(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getMessage("admin.usage-log"));
            return;
        }

        String action = args[2].toLowerCase();
        int page = args.length > 3 ? Integer.parseInt(args[3]) : 1;

        switch (action) {
            case "list":
                showRecentLogs(player, page);
                break;
            case "search":
                if (args.length < 4) {
                    player.sendMessage(plugin.getMessage("admin.specify-player"));
                    return;
                }
                searchPlayerLogs(player, args[3], page);
                break;
        }
    }

    private void showAdminHelp(Player player) {
        player.sendMessage(plugin.getMessage("admin.help-title"));
        if (player.hasPermission("bank.admin.balance")) {
            player.sendMessage(plugin.getMessage("admin.help-balance"));
        }
        if (player.hasPermission("bank.admin.loan")) {
            player.sendMessage(plugin.getMessage("admin.help-loan"));
        }
        if (player.hasPermission("bank.admin.log")) {
            player.sendMessage(plugin.getMessage("admin.help-log"));
        }
        if (player.hasPermission("bank.admin.reload")) {
            player.sendMessage(plugin.getMessage("admin.help-reload"));
        }
    }

    /**
     * 记录管理员操作日志
     * @param admin 执行操作的管理员
     * @param action 操作类型
     * @param target 目标玩家
     * @param amount 涉及金额
     * @param reason 操作原因
     */
    private void logAdminAction(Player admin, String action, Player target, double amount, String reason) {
        // 获取日志目录
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
            plugin.getLogger().info("创建日志目录: " + logDir.getPath());
        }

        // 按日期创建日志文件
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String today = dateFormat.format(new Date());
        File logFile = new File(logDir, today + ".log");

        try {
            // 确保日志文件存在
            if (!logFile.exists()) {
                logFile.createNewFile();
                plugin.getLogger().info("创建新日志文件: " + logFile.getPath());
            }

            // 写入日志
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
                String timestamp = timeFormat.format(new Date());
                String logEntry = String.format("[%s] %s 对 %s 执行了 %s 操作，金额：%.2f，原因：%s", 
                    timestamp, admin.getName(), target.getName(), action, amount, reason);
                writer.println(logEntry);
                plugin.getLogger().info("记录日志: " + logEntry);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("写入日志失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 清理过期日志文件
     */
    public void cleanupOldLogs() {
        int retentionDays = plugin.getConfig().getInt("log.retention-days", 30);
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) return;

        long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles == null) return;

        for (File file : logFiles) {
            try {
                // 从文件名解析日期
                Date fileDate = sdf.parse(file.getName().replace(".log", ""));
                if (fileDate.getTime() < cutoffTime) {
                    if (file.delete()) {
                        plugin.getLogger().info("已删除过期日志文件: " + file.getName());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("清理日志文件时出错: " + e.getMessage());
            }
        }
    }

    private void showRecentLogs(Player player, int page) {
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            player.sendMessage(plugin.getMessage("log.no-logs"));
            return;
        }

        try {
            // 获取所有日志文件并按日期排序
            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null || logFiles.length == 0) {
                player.sendMessage(plugin.getMessage("log.no-logs"));
                return;
            }

            Arrays.sort(logFiles, Collections.reverseOrder());
            
            List<String> allLogs = new ArrayList<>();
            // 读取最近的日志文件
            for (File file : logFiles) {
                allLogs.addAll(Files.readAllLines(file.toPath()));
                if (allLogs.size() >= 100) break; // 最多读取100条记录
            }

            // 计算分页
            int totalPages = (allLogs.size() + 9) / 10;
            page = Math.min(Math.max(1, page), totalPages);

            player.sendMessage(plugin.getMessage("admin.recent-title", page, totalPages));
            
            int start = (page - 1) * 10;
            int end = Math.min(start + 10, allLogs.size());
            
            for (int i = start; i < end; i++) {
                player.sendMessage("§e" + allLogs.get(i));
            }
        } catch (IOException e) {
            player.sendMessage(plugin.getMessage("log.read-error", e.getMessage()));
        }
    }

    private void searchPlayerLogs(Player player, String targetName, int page) {
        File logDir = new File(plugin.getDataFolder(), "logs");
        if (!logDir.exists()) {
            player.sendMessage(plugin.getMessage("log.no-logs"));
            return;
        }

        try {
            File[] logFiles = logDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null || logFiles.length == 0) {
                player.sendMessage(plugin.getMessage("log.no-logs"));
                return;
            }

            Arrays.sort(logFiles, Collections.reverseOrder());
            
            List<String> playerLogs = new ArrayList<>();
            // 从所有日志文件中搜索玩家记录
            for (File file : logFiles) {
                List<String> fileLogs = Files.readAllLines(file.toPath());
                playerLogs.addAll(fileLogs.stream()
                    .filter(log -> log.contains(targetName))
                    .collect(Collectors.toList()));
            }

            int totalPages = (playerLogs.size() + 9) / 10;
            page = Math.min(Math.max(1, page), totalPages);

            player.sendMessage(plugin.getMessage("admin.player-title", targetName, page, totalPages));
            
            int start = (page - 1) * 10;
            int end = Math.min(start + 10, playerLogs.size());
            
            for (int i = start; i < end; i++) {
                player.sendMessage("§e" + playerLogs.get(i));
            }
        } catch (IOException e) {
            player.sendMessage(plugin.getMessage("log.read-error", e.getMessage()));
        }
    }

    /**
     * 处理定期存款提前支取确认
     * @param player 玩家
     */
    private void handleConfirm(Player player) {
        TimeDeposit deposit = plugin.getStorageManager().getTimeDeposit(player.getUniqueId());
        if (deposit == null || deposit.getAmount() <= 0) {
            player.sendMessage(plugin.getMessage("time-deposit.no-deposit"));
            return;
        }

        double amount = deposit.getAmount();
        boolean isMatured = plugin.getStorageManager().canWithdrawTimeDeposit(player.getUniqueId());
        
        if (!isMatured) {
            // 处理提前支取
            if (!plugin.getConfig().getBoolean("time-deposit.allow-early-withdraw", true)) {
                player.sendMessage(plugin.getMessage("time-deposit.early-withdraw-not-allowed"));
                return;
            }

            // 根据配置确定提前支取的惩罚类型
            String penaltyType = plugin.getConfig().getString("time-deposit.early-withdraw-penalty.type", "interest");
            if (penaltyType.equals("interest")) {
                // 不计利息提前支取
                plugin.getEconomy().depositPlayer(player, amount);
                plugin.getStorageManager().setTimeDeposit(player.getUniqueId(), new TimeDeposit(0, 0, ""));
                player.sendMessage(plugin.getMessage("time-deposit.early-withdraw-no-interest", amount));
            } else {
                // 收取手续费提前支取
                double penalty = plugin.getConfig().getDouble("time-deposit.early-withdraw-penalty.percentage", 10);
                double deduction = amount * (penalty / 100.0);
                double finalAmount = amount - deduction;
                
                plugin.getEconomy().depositPlayer(player, finalAmount);
                plugin.getStorageManager().setTimeDeposit(player.getUniqueId(), new TimeDeposit(0, 0, ""));
                player.sendMessage(plugin.getMessage("time-deposit.early-withdraw-penalty", penalty));
                player.sendMessage(plugin.getMessage("time-deposit.early-withdraw-details", 
                    amount, deduction, finalAmount));
            }
        } else {
            // 正常到期支取
            double interestRate = plugin.getTimeRate(deposit.getPeriod());
            long days = (System.currentTimeMillis() - deposit.getDepositDate()) / (1000 * 60 * 60 * 24);
            double interest = amount * (interestRate / 100.0) * (days / 365.0);
            double total = amount + interest;

            plugin.getEconomy().depositPlayer(player, total);
            plugin.getStorageManager().setTimeDeposit(player.getUniqueId(), new TimeDeposit(0, 0, ""));
            player.sendMessage(plugin.getMessage("time-deposit.matured-withdraw", amount, interest, total));
        }
    }
} 