// 贷款利率管理类
package org.littlesheep.bank.loan;

import org.bukkit.scheduler.BukkitRunnable;
import org.littlesheep.bank.Bank;

import java.util.Random;

/**
 * 贷款利率管理器
 * 负责管理和更新贷款利率，支持动态浮动利率
 */
public class LoanRateManager {
    private final Bank plugin;
    private double currentRate;  // 当前利率
    private final Random random = new Random();

    /**
     * 初始化贷款利率管理器
     * @param plugin 插件实例
     */
    public LoanRateManager(Bank plugin) {
        this.plugin = plugin;
        this.currentRate = plugin.getConfig().getDouble("loan.base-rate");
        if (plugin.getConfig().getBoolean("loan.dynamic-rate.enabled")) {
            startRateUpdater();
        }
    }

    /**
     * 启动利率更新任务
     * 根据配置的时间间隔定期更新利率
     */
    private void startRateUpdater() {
        // 将小时转换为tick (20 ticks/s * 60s * 60min * 配置的小时数)
        long interval = plugin.getConfig().getLong("loan.dynamic-rate.update-interval") * 20 * 60 * 60;
        new BukkitRunnable() {
            @Override
            public void run() {
                updateRate();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    /**
     * 更新利率
     * 在最低利率和最高利率之间随机生成新的利率
     */
    private void updateRate() {
        double minRate = plugin.getConfig().getDouble("loan.dynamic-rate.min-rate");
        double maxRate = plugin.getConfig().getDouble("loan.dynamic-rate.max-rate");
        double range = maxRate - minRate;
        currentRate = minRate + random.nextDouble() * range;
    }

    /**
     * 获取当前利率
     * @return 当前贷款利率
     */
    public double getCurrentRate() {
        return currentRate;
    }
} 