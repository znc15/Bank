package org.littlesheep.bank.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.littlesheep.bank.Bank;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InputManager implements Listener {
    private final Bank plugin;
    private final Map<UUID, InputType> awaitingInput = new HashMap<>();
    private final Map<UUID, String> pendingInputs = new HashMap<>();

    public InputManager(Bank plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void awaitInput(Player player, InputType type) {
        awaitingInput.put(player.getUniqueId(), type);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        InputType type = awaitingInput.get(player.getUniqueId());
        
        if (type == null) return;
        
        event.setCancelled(true);
        String input = event.getMessage();
        
        // 移除等待状态
        awaitingInput.remove(player.getUniqueId());
        
        // 在主线程处理输入
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                switch (type) {
                    case DEMAND_DEPOSIT:
                        handleDemandDeposit(player, input);
                        break;
                    case DEMAND_WITHDRAW:
                        handleDemandWithdraw(player, input);
                        break;
                    case TIME_DEPOSIT_AMOUNT:
                        handleTimeDepositAmount(player, input);
                        break;
                    case TIME_DEPOSIT_PERIOD:
                        handleTimeDepositPeriod(player, input);
                        break;
                    case LOAN_APPLY:
                        handleLoanApply(player, input);
                        break;
                    case LOAN_REPAY:
                        handleLoanRepay(player, input);
                        break;
                    case LOAN_AMOUNT:
                        handleLoanAmount(player, input);
                        break;
                    case LOAN_DAYS:
                        handleLoanDays(player, input);
                        break;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getMessage("common.invalid-amount"));
            }
        });
    }

    private void handleDemandDeposit(Player player, String input) {
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) {
                player.sendMessage(plugin.getMessage("common.amount-positive"));
                return;
            }
            // 处理存款逻辑
            plugin.getStorageManager().deposit(player.getUniqueId(), amount);
            player.sendMessage(plugin.getMessage("deposit.success"));
            player.sendMessage(plugin.getMessage("deposit.amount", amount));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("common.invalid-amount"));
        }
    }

    private void handleDemandWithdraw(Player player, String input) {
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) {
                player.sendMessage(plugin.getMessage("common.amount-positive"));
                return;
            }
            // 处理取款逻辑
            if (plugin.getStorageManager().withdraw(player.getUniqueId(), amount)) {
                player.sendMessage(plugin.getMessage("withdraw.success"));
                player.sendMessage(plugin.getMessage("withdraw.amount", amount));
            } else {
                player.sendMessage(plugin.getMessage("withdraw.insufficient"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("common.invalid-amount"));
        }
    }

    private void handleTimeDepositAmount(Player player, String input) {
        try {
            double amount = Double.parseDouble(input);
            if (amount <= 0) {
                player.sendMessage(plugin.getMessage("common.amount-positive"));
                return;
            }
            
            // 临时存储金额
            plugin.getStorageManager().setTempTimeDepositAmount(player.getUniqueId(), amount);
            
            // 提示输入期限
            player.sendMessage(plugin.getMessage("time-deposit.input-period"));
            awaitInput(player, InputType.TIME_DEPOSIT_PERIOD);
            
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("common.invalid-amount"));
        }
    }

    private void handleTimeDepositPeriod(Player player, String input) {
        String period = input.toLowerCase();
        if (!period.equals("week") && !period.equals("month") && !period.equals("year")) {
            player.sendMessage(plugin.getMessage("common.invalid-period"));
            return;
        }
        
        double amount = plugin.getStorageManager().getTempTimeDepositAmount(player.getUniqueId());
        if (plugin.getStorageManager().createTimeDeposit(player.getUniqueId(), amount, period)) {
            player.sendMessage(plugin.getMessage("time-deposit.success", amount, period));
            // 清除临时存储的金额
            plugin.getStorageManager().clearTempTimeDepositAmount(player.getUniqueId());
        } else {
            player.sendMessage(plugin.getMessage("time-deposit.exists"));
        }
    }

    private void handleLoanApply(Player player, String input) {
        double amount = Double.parseDouble(input);
        if (amount <= 0) {
            player.sendMessage(plugin.getMessage("common.amount-positive"));
            return;
        }
        // 处理贷款申请逻辑
        if (plugin.getStorageManager().createLoan(player.getUniqueId(), amount)) {
            player.sendMessage(plugin.getMessage("loan.success"));
            player.sendMessage(plugin.getMessage("loan.amount", amount));
        } else {
            player.sendMessage(plugin.getMessage("loan.exists"));
        }
    }

    private void handleLoanRepay(Player player, String input) {
        double amount = Double.parseDouble(input);
        if (amount <= 0) {
            player.sendMessage(plugin.getMessage("common.amount-positive"));
            return;
        }
        // 处理还款逻辑
        if (plugin.getStorageManager().repayLoan(player.getUniqueId(), amount)) {
            player.sendMessage(plugin.getMessage("loan.repay-success"));
            player.sendMessage(plugin.getMessage("loan.repay-amount", amount));
        } else {
            player.sendMessage(plugin.getMessage("loan.repay-insufficient"));
        }
    }

    public void handleLoanAmount(Player player, String input) {
        try {
            double amount = Double.parseDouble(input);
            player.sendMessage(plugin.getMessage("loan.input-days", 
                plugin.getConfig().getInt("loan.max-days", 30)));
            awaitInput(player, InputType.LOAN_DAYS);
            pendingInputs.put(player.getUniqueId(), String.valueOf(amount));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("common.invalid-amount"));
        }
    }

    public void handleLoanDays(Player player, String input) {
        try {
            int days = Integer.parseInt(input);
            double amount = Double.parseDouble(pendingInputs.get(player.getUniqueId()));
            String[] args = new String[]{"loan", String.valueOf(amount), String.valueOf(days)};
            plugin.getBankCommand().handleLoan(player, args);
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("common.invalid-days"));
        }
    }
}

enum InputType {
    DEMAND_DEPOSIT,
    DEMAND_WITHDRAW,
    TIME_DEPOSIT_AMOUNT,
    TIME_DEPOSIT_PERIOD,
    LOAN_APPLY,
    LOAN_REPAY,
    LOAN_AMOUNT,
    LOAN_DAYS
} 