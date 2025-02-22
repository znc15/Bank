package org.littlesheep.bank.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.World;
import org.littlesheep.bank.Bank;

public class TimeChangeListener implements Listener {
    private final Bank plugin;
    private static long lastProcessedDay = 0;  // 改为静态变量，所有实例共享
    private static boolean isProcessing = false;  // 添加处理锁

    public TimeChangeListener(Bank plugin) {
        this.plugin = plugin;
        // 初始化为当前游戏天数
        lastProcessedDay = plugin.getServer().getWorlds().get(0).getFullTime() / 24000L;
    }
    //处理新一天事件
    private void processNewDay(long currentDay) {
        if (isProcessing) return;  // 如果正在处理，直接返回
        
        try {
            isProcessing = true;  // 设置处理锁
            
            if (currentDay > lastProcessedDay) {
                plugin.getLogger().info("[调试] 检测到新的一天，开始计算利息和检查贷款");
                plugin.calculateDailyInterest();
                plugin.getLoanPenaltyManager().checkOverdueLoans();
                lastProcessedDay = currentDay;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("处理新一天事件时出错: " + e.getMessage());
            e.printStackTrace();  // 打印完整堆栈跟踪
        } finally {
            isProcessing = false;  // 确保处理锁被释放
        }
    }
    //玩家起床事件
    @EventHandler
    public void onPlayerWakeUp(PlayerBedLeaveEvent event) {
        if (!plugin.getConfig().getString("settings.time-type", "REAL").equalsIgnoreCase("GAME")) {
            return;
        }
        World world = event.getPlayer().getWorld();
        long currentDay = world.getFullTime() / 24000L;
        processNewDay(currentDay);
    }
} 