// 利息结算任务类
package org.littlesheep.bank;

import org.bukkit.scheduler.BukkitRunnable;
import org.littlesheep.bank.storage.StorageManager;
import org.bukkit.entity.Player;

import java.util.UUID;

public class InterestTask extends BukkitRunnable {
    private final Bank plugin;

    public InterestTask(Bank plugin) {
        this.plugin = plugin;
    }

    private int getRequiredDays(String period) {
        switch (period) {
            case "week": return 7;
            case "month": return 30;
            case "year": return 365;
            default: return 7;
        }
    }

    private double calculateInterest(double amount, double rate, String period) {
        int days;
        switch (period) {
            case "week": days = 7; break;
            case "month": days = 30; break;
            case "year": days = 365; break;
            default: days = 7;
        }
        return amount * (rate / 100.0) * (days / 365.0);
    }

    @Override
    public void run() {
        StorageManager storage = plugin.getStorageManager();
        double demandRate = plugin.getDemandRate("default");
        double minBalance = plugin.getConfig().getDouble("interest.minimum-balance", 1000.0);
        int minDays = plugin.getConfig().getInt("interest.minimum-time-deposit-days", 7);
        
        // 全服公告
        plugin.getServer().broadcastMessage("§b§l✧===============================✧");
        plugin.getServer().broadcastMessage("§e§l            银行日结利息公告");
        plugin.getServer().broadcastMessage("§b§l✧===============================✧");
        plugin.getServer().broadcastMessage("§a▸ 活期存款");
        plugin.getServer().broadcastMessage(String.format("  §f基准利率: §e%.2f%%", demandRate));
        plugin.getServer().broadcastMessage(String.format("  §f最低收益金额: §e%.2f", minBalance));
        plugin.getServer().broadcastMessage("§a▸ 定期存款");
        plugin.getServer().broadcastMessage(String.format("  §f七日年化: §e%.2f%%", plugin.getTimeRate("week")));
        plugin.getServer().broadcastMessage(String.format("  §f月存年化: §e%.2f%%", plugin.getTimeRate("month")));
        plugin.getServer().broadcastMessage(String.format("  §f年存年化: §e%.2f%%", plugin.getTimeRate("year")));
        plugin.getServer().broadcastMessage(String.format("  §f最短存期: §e%d天", minDays));
        plugin.getServer().broadcastMessage("§b§l✧===============================✧");
        
        for (UUID uuid : storage.getAllPlayers()) {
            Player player = plugin.getServer().getPlayer(uuid);
            double balance = storage.getBalance(uuid);
            TimeDeposit timeDeposit = storage.getTimeDeposit(uuid);
            double totalBalance = balance + timeDeposit.getAmount();
            double totalInterest = 0.0;
            
            // 计算会员加息
            String level = plugin.getMembershipLevel(totalBalance);
            double memberBonus = plugin.getMembershipBonus(level);
            
            // 计算活期利息
            if (balance >= minBalance) {
                double baseRate = plugin.getDemandRate("default");
                double totalRate = baseRate + memberBonus;
                double interest = balance * (totalRate / 100.0 / 365);
                storage.setBalance(uuid, balance + interest);
                totalInterest += interest;
                
                if (player != null && player.isOnline()) {
                    player.sendMessage("§d✦ ========================= ✦");
                    player.sendMessage("§e        今日利息结算通知");
                    player.sendMessage("§d✦ ========================= ✦");
                    player.sendMessage("§b▸ 活期存款");
                    player.sendMessage(String.format("  §f本金: §e%.2f", balance));
                    player.sendMessage(String.format("  §f利率: §e%.2f%% §7(基础: %.2f%% + 会员: %.2f%%)",
                        totalRate, baseRate, memberBonus));
                    player.sendMessage(String.format("  §f利息: §e%.2f", interest));
                    player.sendMessage(String.format("  §f余额: §e%.2f", balance + interest));
                }
            }
            
            // 计算定期利息
            if (timeDeposit != null && timeDeposit.getAmount() > 0) {
                int requiredDays = getRequiredDays(timeDeposit.getPeriod());
                long daysPassed = (System.currentTimeMillis() - timeDeposit.getDepositDate()) / (1000 * 60 * 60 * 24);
                
                if (daysPassed >= requiredDays) {
                    double periodRate = plugin.getTimeRate(timeDeposit.getPeriod());
                    double timeInterest = calculateInterest(timeDeposit.getAmount(), periodRate, timeDeposit.getPeriod());
                    double newAmount = timeDeposit.getAmount() + timeInterest;
                    timeDeposit.setAmount(newAmount);
                    storage.setTimeDeposit(uuid, timeDeposit);
                    totalInterest += timeInterest;
                    
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§b▸ 定期存款");
                        player.sendMessage(String.format("  §f本金: §e%.2f", timeDeposit.getAmount()));
                        player.sendMessage(String.format("  §f利率: §e%.2f%%", periodRate));
                        player.sendMessage(String.format("  §f利息: §e%.2f", timeInterest));
                        player.sendMessage(String.format("  §f余额: §e%.2f", newAmount));
                        player.sendMessage(String.format("  §f存期: §e%s", 
                            timeDeposit.getPeriod().equals("week") ? "周存" :
                            timeDeposit.getPeriod().equals("month") ? "月存" : "年存"));
                    }
                }
            }
            
            if (player != null && player.isOnline() && totalInterest > 0) {
                player.sendMessage("§d✦ ========================= ✦");
                player.sendMessage(String.format("§e    总利息收入: %.2f", totalInterest));
                player.sendMessage("§d✦ ========================= ✦");
            }
        }
    }
} 