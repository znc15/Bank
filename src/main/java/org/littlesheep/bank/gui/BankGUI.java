// 银行 GUI 类
package org.littlesheep.bank.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.littlesheep.bank.Bank;
import org.littlesheep.bank.Loan;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.YamlConfiguration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

public class BankGUI implements Listener {
    private final Bank plugin;
    private final InputManager inputManager;
    private FileConfiguration guiConfig;

    public BankGUI(Bank plugin) {
        this.plugin = plugin;
        this.inputManager = new InputManager(plugin);
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!configFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    public void openMainMenu(Player player) {
        ConfigurationSection menuConfig = guiConfig.getConfigurationSection("main-menu");
        if (menuConfig == null) return;

        // 从语言文件获取标题
        String title = menuConfig.getString("title", plugin.getMessage("gui.title"));
        int size = menuConfig.getInt("size", 54);
        Inventory gui = Bukkit.createInventory(null, size, title);

        // 设置装饰
        ConfigurationSection decorations = guiConfig.getConfigurationSection("decorations");
        if (decorations != null) {
            setDecorations(gui, decorations);
        }

        // 设置物品
        ConfigurationSection items = menuConfig.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ItemStack item = createItem(player, guiConfig, key);
                int slot = items.getInt(key + ".slot", 0);
                gui.setItem(slot, item);
            }
        }

        player.openInventory(gui);
    }

    public ItemStack createItem(Player player, FileConfiguration config, String path) {
        ConfigurationSection itemSection = config.getConfigurationSection("main-menu.items." + path);
        if (itemSection == null) {
            plugin.getLogger().warning("Could not find item section: main-menu.items." + path);
            return new ItemStack(Material.BARRIER);
        }

        String materialName = itemSection.getString("material");
        if (materialName == null || materialName.isEmpty()) {
            plugin.getLogger().warning("No material specified for item: " + path);
            return new ItemStack(Material.BARRIER);
        }
        
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material name: " + materialName + " in path: " + path);
            return new ItemStack(Material.BARRIER);
        }
        
        ItemStack item;
        if (material == Material.PLAYER_HEAD) {
            item = createPlayerHead(player);
        } else {
            item = new ItemStack(material);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = itemSection.getString("name", "");
            meta.setDisplayName(processPlaceholders(player, name));
            
            List<String> lore = itemSection.getStringList("lore");
            lore = lore.stream()
                .map(line -> processPlaceholders(player, line))
                .collect(Collectors.toList());
            meta.setLore(lore);
            
            if (itemSection.getBoolean("glow", false)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    @SuppressWarnings("unused")
    private void setDecorations(Inventory inv, ConfigurationSection decorations) {
        ConfigurationSection border = decorations.getConfigurationSection("border");
        if (border != null) {
            Material material = Material.valueOf(border.getString("material", "BLACK_STAINED_GLASS_PANE"));
            String name = border.getString("name", " ");
            List<Integer> slots = border.getIntegerList("slots");
            
            ItemStack decoration = new ItemStack(material);
            ItemMeta meta = decoration.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name);
                decoration.setItemMeta(meta);
            }
            
            for (int slot : slots) {
                inv.setItem(slot, decoration);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiConfig.getString("main-menu.title"))) return;
        
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();
        
        switch (slot) {
            case 20: // 活期存款
                handleDemandDeposit(player, event.getClick());
                break;
            case 22: // 定期存款
                handleTimeDeposit(player, event.getClick());
                break;
            case 24: // 利率查询
                player.performCommand("bank rates");
                break;
            case 31: // 贷款
                handleLoan(player, event.getClick());
                break;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(guiConfig.getString("main-menu.title"))) {
            event.setCancelled(true);
        }
    }

    private String processPlaceholders(Player player, String text) {
        if (!plugin.isPapiEnabled()) {
            // 手动替换变量
            text = text.replace("{player_name}", player.getName());
            text = text.replace("{balance}", String.format("%.2f", plugin.getStorageManager().getBalance(player.getUniqueId())));
            text = text.replace("{time_deposit}", String.format("%.2f", plugin.getStorageManager().getTimeDeposit(player.getUniqueId()).getAmount()));
            text = text.replace("{cash}", String.format("%.2f", plugin.getEconomy().getBalance(player)));
            
            double totalBalance = plugin.getStorageManager().getBalance(player.getUniqueId()) 
                + plugin.getStorageManager().getTimeDeposit(player.getUniqueId()).getAmount();
            text = text.replace("{total_balance}", String.format("%.2f", totalBalance));
            
            String level = plugin.getMembershipLevel(totalBalance);
            text = text.replace("{level}", formatLevel(level));
            
            double bonus = plugin.getMembershipBonus(level);
            text = text.replace("{bonus}", String.format("%.1f", bonus));
            
            // 贷款相关变量
            Loan loan = plugin.getStorageManager().getLoan(player.getUniqueId());
            text = text.replace("{loan_amount}", loan != null && !loan.isPaid() ? 
                String.format("%.2f", loan.getAmount()) : "0");
            text = text.replace("{loan_date}", loan != null && !loan.isPaid() ? 
                new SimpleDateFormat("yyyy-MM-dd").format(new Date(loan.getLoanDate())) : "无");
            text = text.replace("{loan_days}", loan != null && !loan.isPaid() ? 
                String.valueOf(Math.max(0, (loan.getLoanDate() + loan.getDays() * 24L * 60L * 60L * 1000L 
                - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L))) : "0");
            
            double loanRate = plugin.getLoanRateManager().getCurrentRate();
            text = text.replace("{loan_rate}", String.format("%.2f", loanRate));
            
            return text;
        } else {
            // 先替换所有PAPI变量
            String processed = PlaceholderAPI.setPlaceholders(player, text);
            
            // 处理 %变量%+%变量% 格式的运算
            Pattern pattern = Pattern.compile("(\\d+\\.?\\d*)(\\+|-)(\\d+\\.?\\d*)");
            Matcher matcher = pattern.matcher(processed);
            StringBuffer result = new StringBuffer();
            
            while (matcher.find()) {
                try {
                    double num1 = Double.parseDouble(matcher.group(1));
                    double num2 = Double.parseDouble(matcher.group(3));
                    String operator = matcher.group(2);
                    double value = operator.equals("+") ? num1 + num2 : num1 - num2;
                    matcher.appendReplacement(result, String.format("%.2f", value));
                } catch (NumberFormatException e) {
                    matcher.appendReplacement(result, matcher.group());
                }
            }
            matcher.appendTail(result);
            return result.toString();
        }
    }

    private String formatLevel(String level) {
        return plugin.getMessage("membership.levels." + level.toLowerCase());
    }

    private void handleDemandDeposit(Player player, ClickType click) {
        if (click.isLeftClick()) {
            player.closeInventory();
            player.sendMessage(plugin.getMessage("deposit.input-amount"));
            inputManager.awaitInput(player, InputType.DEMAND_DEPOSIT);
        } else if (click.isRightClick()) {
            player.closeInventory();
            player.sendMessage(plugin.getMessage("withdraw.input-amount"));
            inputManager.awaitInput(player, InputType.DEMAND_WITHDRAW);
        }
    }

    private void handleTimeDeposit(Player player, ClickType click) {
        if (click.isLeftClick()) {
            player.closeInventory();
            player.sendMessage(plugin.getMessage("time-deposit.input-amount"));
            inputManager.awaitInput(player, InputType.TIME_DEPOSIT_AMOUNT);
        } else if (click.isRightClick()) {
            if (!plugin.getStorageManager().canWithdrawTimeDeposit(player.getUniqueId())) {
                player.sendMessage(plugin.getMessage("time-deposit.early-withdraw"));
                player.sendMessage(plugin.getMessage("time-deposit.early-confirm"));
            } else {
                player.sendMessage(plugin.getMessage("time-deposit.mature"));
                player.sendMessage(plugin.getMessage("time-deposit.withdraw-confirm"));
            }
            player.closeInventory();
        }
    }

    private void handleLoan(Player player, ClickType click) {
        if (click.isLeftClick()) {
            // 申请贷款
            player.closeInventory();
            player.sendMessage(plugin.getMessage("loan.input-amount"));
            inputManager.awaitInput(player, InputType.LOAN_AMOUNT);
        } else if (click.isRightClick()) {
            // 还款
            player.closeInventory();
            handleRepay(player);
        }
    }

    private void handleRepay(Player player) {
        Loan loan = plugin.getStorageManager().getLoan(player.getUniqueId());
        if (loan == null || loan.isPaid()) {
            player.sendMessage(plugin.getMessage("loan.no-loan"));
            return;
        }

        double rate = plugin.getLoanRateManager().getCurrentRate();
        long days = (System.currentTimeMillis() - loan.getLoanDate()) / (1000 * 60 * 60 * 24);
        double interest = loan.getAmount() * (rate/100) * (days/365.0);
        double totalDue = loan.getAmount() + interest;

        if (plugin.getEconomy().getBalance(player) < totalDue) {
            player.sendMessage(plugin.getMessage("loan.repay-insufficient"));
            player.sendMessage(plugin.getMessage("loan.repay-amount", totalDue, 
                plugin.getEconomy().getBalance(player)));
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, totalDue);
        loan.setPaid(true);
        plugin.getStorageManager().setLoan(player.getUniqueId(), loan);

        player.sendMessage(plugin.getMessage("loan.repay-success"));
        player.sendMessage(plugin.getMessage("loan.repay-principal", loan.getAmount()));
        player.sendMessage(plugin.getMessage("loan.repay-interest", interest));
        player.sendMessage(plugin.getMessage("loan.repay-total", totalDue));
    }

    /**
     * 重新加载GUI配置
     */
    public void reloadConfig() {
        File configFile = new File(plugin.getDataFolder(), "gui.yml");
        if (!configFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(configFile);
    }
} 