// 贷款逾期惩罚管理类
package org.littlesheep.bank.loan;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.littlesheep.bank.Bank;
import org.littlesheep.bank.Loan;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 贷款惩罚管理器
 * 负责处理逾期贷款的惩罚机制
 */
public class LoanPenaltyManager {
    private final Bank plugin;
    private final Set<UUID> penalizedPlayers = new HashSet<>();  // 已经被惩罚过的玩家

    /**
     * 初始化惩罚管理器
     * @param plugin 插件实例
     */
    public LoanPenaltyManager(Bank plugin) {
        this.plugin = plugin;
        startPenaltyChecker();
    }

    /**
     * 启动逾期检查任务
     * 每分钟检查一次所有玩家的贷款状态
     */
    private void startPenaltyChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkOverdueLoans();
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // 20 ticks * 60 = 1分钟
    }

    /**
     * 检查所有玩家的逾期贷款
     * 如果发现逾期贷款，对在线玩家执行惩罚
     */
    public void checkOverdueLoans() {
        for (UUID uuid : plugin.getStorageManager().getAllPlayers()) {
            Loan loan = plugin.getStorageManager().getLoan(uuid);
            if (loan != null && !loan.isPaid()) {
                // 使用 getElapsedTime 计算经过的时间
                long elapsedTime = plugin.getElapsedTime(loan.getLoanDate());
                long dueTime = loan.getDays() * 24L * 60L * 60L * 1000L;
                
                if (elapsedTime > dueTime) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        applyPenalty(player);
                    }
                }
            }
        }
    }

    /**
     * 对玩家执行惩罚
     * 支持三种惩罚模式：
     * - continuous: 持续性惩罚
     * - interval: 间隔性惩罚
     * - once: 一次性惩罚
     * @param player 要惩罚的玩家
     */
    private void applyPenalty(Player player) {
        String type = plugin.getConfig().getString("loan.penalty.type", "continuous");
        // 如果是一次性惩罚且玩家已经被惩罚过，则跳过
        if (type.equals("once") && penalizedPlayers.contains(player.getUniqueId())) {
            return;
        }

        // 应用所有配置的效果
        for (String effectStr : plugin.getConfig().getConfigurationSection("loan.penalty.effects").getKeys(false)) {
            String path = "loan.penalty.effects." + effectStr + ".";
            PotionEffectType effectType = PotionEffectType.getByName(
                plugin.getConfig().getString(path + "effect")
            );
            int amplifier = plugin.getConfig().getInt(path + "amplifier");
            int duration = plugin.getConfig().getInt(path + "duration");

            if (effectType != null) {
                player.addPotionEffect(new PotionEffect(effectType, duration, amplifier));
            }
        }

        // 如果是间隔性惩罚，设置下一次惩罚
        if (type.equals("interval")) {
            int interval = plugin.getConfig().getInt("loan.penalty.interval", 300);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        applyPenalty(player);
                    }
                }
            }.runTaskLater(plugin, interval * 20L);
        }

        penalizedPlayers.add(player.getUniqueId());
    }
} 