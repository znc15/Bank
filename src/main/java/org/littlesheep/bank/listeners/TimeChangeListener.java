package org.littlesheep.bank.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.World;
import org.littlesheep.bank.Bank;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerBedEnterEvent;

public class TimeChangeListener implements Listener {
    private final Bank plugin;
    private static long lastProcessedDay = 0;  // 改为静态变量，所有实例共享
    private static boolean isProcessing = false;  // 添加处理锁
    private static long lastTimeBeforeSleep = 0; // 记录睡觉前的时间

    public TimeChangeListener(Bank plugin) {
        this.plugin = plugin;
        // 初始化为当前游戏天数
        lastProcessedDay = plugin.getServer().getWorlds().get(0).getFullTime() / 24000L;
    }

    private boolean isTimeChangedByCommand() {
        // 检查是否有正在执行的时间相关命令
        return plugin.getServer().getScheduler().getPendingTasks().stream()
                .anyMatch(task -> {
                    Plugin owner = task.getOwner();
                    return owner != null && (
                            owner.equals(plugin.getServer().getPluginManager().getPlugin("Essentials")) ||
                            owner.equals(plugin.getServer().getPluginManager().getPlugin("EssentialsX")) ||
                            owner.getName().toLowerCase().contains("time")
                    );
                });
    }

    //处理新一天事件
    private void processNewDay(long currentDay, boolean isNaturalSleep) {
        if (isProcessing) return;  // 如果正在处理，直接返回
        
        // 如果是通过睡觉自然到达第二天，则允许计算利息
        if (!isNaturalSleep && isTimeChangedByCommand()) {
            return;
        }
        
        try {
            isProcessing = true;  // 设置处理锁
            
            if (currentDay > lastProcessedDay) {
                plugin.calculateDailyInterest();
                plugin.getLoanPenaltyManager().checkOverdueLoans();
                lastProcessedDay = currentDay;
            }
        } catch (Exception e) {
            e.printStackTrace();  // 打印完整堆栈跟踪
        } finally {
            isProcessing = false;  // 确保处理锁被释放
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!plugin.getConfig().getString("settings.time-type", "REAL").equalsIgnoreCase("GAME")) {
            return;
        }
        // 记录玩家睡觉前的时间
        lastTimeBeforeSleep = event.getPlayer().getWorld().getTime();
    }

    //玩家起床事件
    @EventHandler
    public void onPlayerWakeUp(PlayerBedLeaveEvent event) {
        if (!plugin.getConfig().getString("settings.time-type", "REAL").equalsIgnoreCase("GAME")) {
            return;
        }
        World world = event.getPlayer().getWorld();
        long currentDay = world.getFullTime() / 24000L;
        
        // 检查是否是通过睡觉自然到达第二天
        // Minecraft中，一天的时间是从0到24000
        // 如果睡觉前是晚上(>12000)，醒来是早上(<12000)，说明是自然睡眠
        boolean isNaturalSleep = lastTimeBeforeSleep > 12000 && world.getTime() < 12000;

        processNewDay(currentDay, isNaturalSleep);
    }
} 