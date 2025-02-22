package org.littlesheep.bank.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.littlesheep.bank.Bank;

public class BankGUIListener implements Listener {
    private final InputManager inputManager;

    public BankGUIListener(Bank plugin) {
        this.inputManager = new InputManager(plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!event.getView().getTitle().equals("§1§l银行系统")) return;
        
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        
        if (event.getCurrentItem() == null) return;
        
        switch (event.getCurrentItem().getType()) {
            case GOLD_INGOT:
                if (event.getClick() == ClickType.LEFT) {
                    player.closeInventory();
                    player.sendMessage("§a请输入存款金额：");
                    inputManager.awaitInput(player, InputType.DEMAND_DEPOSIT);
                } else if (event.getClick() == ClickType.RIGHT) {
                    player.closeInventory();
                    player.sendMessage("§a请输入取款金额：");
                    inputManager.awaitInput(player, InputType.DEMAND_WITHDRAW);
                }
                break;
            case DIAMOND:
                if (event.getClick() == ClickType.LEFT) {
                    player.closeInventory();
                    player.sendMessage("§a请输入定期存款金额：");
                    inputManager.awaitInput(player, InputType.TIME_DEPOSIT_AMOUNT);
                } else if (event.getClick() == ClickType.RIGHT) {
                    player.performCommand("bank timewithdraw");
                }
                break;
            case BOOK:
                player.performCommand("bank rates");
                break;
            default:
                break;
        }
    }
} 