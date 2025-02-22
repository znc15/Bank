// 利息结算任务类
package org.littlesheep.bank.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.littlesheep.bank.Bank;
import org.littlesheep.bank.TimeDeposit;
import org.littlesheep.bank.Loan;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BankExpansion extends PlaceholderExpansion {
    private final Bank plugin;

    public BankExpansion(Bank plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bank";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        // 获取玩家数据
        double balance = plugin.getStorageManager().getBalance(player.getUniqueId());
        TimeDeposit timeDeposit = plugin.getStorageManager().getTimeDeposit(player.getUniqueId());
        Loan loan = plugin.getStorageManager().getLoan(player.getUniqueId());
        double totalBalance = balance + timeDeposit.getAmount();
        String level = plugin.getMembershipLevel(totalBalance);
        double bonus = plugin.getMembershipBonus(level);

        switch (identifier.toLowerCase()) {
            case "name":
                return player.getName();
            case "balance":
                return String.format("%.2f", balance);
            case "time_deposit":
                return String.format("%.2f", timeDeposit.getAmount());
            case "total_balance":
                return String.format("%.2f", totalBalance);
            case "cash":
                return String.format("%.2f", plugin.getEconomy().getBalance(player));
            case "level":
                return formatMembershipLevel(level);
            case "bonus":
                return String.format("%.1f", bonus);
            case "demand_rate":
                return String.format("%.2f", plugin.getDemandRate("default"));
            case "actual_demand_rate":
                double baseRate = plugin.getDemandRate("default");
                return String.format("%.2f", baseRate + bonus);
            case "week_rate":
                return String.format("%.2f", plugin.getTimeRate("week"));
            case "month_rate":
                return String.format("%.2f", plugin.getTimeRate("month"));
            case "year_rate":
                return String.format("%.2f", plugin.getTimeRate("year"));
            case "time_deposit_days":
                if (timeDeposit.getAmount() <= 0) return "0";
                long daysPassed = (System.currentTimeMillis() - timeDeposit.getDepositDate()) / (1000 * 60 * 60 * 24);
                return String.valueOf(daysPassed);
            case "time_deposit_period":
                if (timeDeposit.getAmount() <= 0) return "无";
                switch (timeDeposit.getPeriod()) {
                    case "week": return "周存";
                    case "month": return "月存";
                    case "year": return "年存";
                    default: return "无";
                }
            case "loan_amount":
                return loan != null && !loan.isPaid() ? String.format("%.2f", loan.getAmount()) : "0";
            case "loan_date":
                return loan != null && !loan.isPaid() ? 
                    new SimpleDateFormat("yyyy-MM-dd").format(new Date(loan.getLoanDate())) : "无";
            case "loan_days":
                if (loan == null || loan.isPaid()) return "0";
                long daysLeft = (loan.getLoanDate() + loan.getDays() * 24L * 60L * 60L * 1000L 
                    - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L);
                return String.valueOf(Math.max(0, daysLeft));
            case "loan_rate":
                if (loan == null || loan.isPaid()) return "0";
                double loanBaseRate = plugin.getLoanRateManager().getCurrentRate();
                double rateDiscount = plugin.getConfig().getDouble("loan.interest-rate." + level, 0);
                return String.format("%.2f", Math.max(0, loanBaseRate + rateDiscount));
            default:
                return null;
        }
    }

    private String formatMembershipLevel(String level) {
        switch (level) {
            case "bronze": return "青铜会员";
            case "silver": return "白银会员";
            case "gold": return "黄金会员";
            case "platinum": return "白金会员";
            case "diamond": return "钻石会员";
            default: return "普通用户";
        }
    }

    @Override
    public boolean persist() {
        return true;
    }
} 