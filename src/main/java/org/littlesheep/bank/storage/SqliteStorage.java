// 数据库存储类
package org.littlesheep.bank.storage;

import org.littlesheep.bank.Bank;
import org.littlesheep.bank.TimeDeposit;
import org.littlesheep.bank.Loan;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import java.util.HashMap;
import java.util.Map;

public class SqliteStorage implements StorageManager {
    private Connection connection;
    private final Bank plugin;
    private final Map<UUID, Double> tempTimeDepositAmounts = new HashMap<>();

    public SqliteStorage(Bank plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            // 确保数据库文件目录存在
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // 建立数据库连接
            connection = DriverManager.getConnection("jdbc:sqlite:" + 
                new File(plugin.getDataFolder(), "bank.db").getAbsolutePath());

            // 创建必要的表
            try (Statement stmt = connection.createStatement()) {
                // 账户表
                stmt.execute("CREATE TABLE IF NOT EXISTS accounts (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "balance DOUBLE DEFAULT 0" +
                        ")");

                // 定期存款表
                stmt.execute("CREATE TABLE IF NOT EXISTS time_deposits (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "amount DOUBLE DEFAULT 0," +
                        "start_time BIGINT DEFAULT 0," +
                        "period VARCHAR(10) DEFAULT ''" +
                        ")");

                // 贷款表
                stmt.execute("CREATE TABLE IF NOT EXISTS loans (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "amount DOUBLE DEFAULT 0," +
                        "loan_date BIGINT DEFAULT 0," +
                        "days INT DEFAULT 0," +
                        "paid BOOLEAN DEFAULT 0" +
                        ")");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("初始化数据库失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT balance FROM accounts WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    @Override
    public void setBalance(UUID playerUUID, double amount) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO accounts (uuid, balance) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET balance = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, amount);
            stmt.setDouble(3, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public TimeDeposit getTimeDeposit(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT amount, start_time, period FROM time_deposits WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TimeDeposit(rs.getDouble("amount"), 
                                     rs.getLong("start_time"),
                                     rs.getString("period"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new TimeDeposit(0, 0, "");
    }

    @Override
    public void setTimeDeposit(UUID playerUUID, TimeDeposit deposit) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO time_deposits (uuid, amount, start_time, period) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET amount = ?, start_time = ?, period = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, deposit.getAmount());
            stmt.setLong(3, deposit.getDepositDate());
            stmt.setString(4, deposit.getPeriod());
            stmt.setDouble(5, deposit.getAmount());
            stmt.setLong(6, deposit.getDepositDate());
            stmt.setString(7, deposit.getPeriod());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getTimeDepositDate(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT time_deposit_date FROM accounts WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("time_deposit_date");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0L;
    }

    @Override
    public void setTimeDepositDate(UUID playerUUID, long date) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR REPLACE INTO accounts (uuid, time_deposit_date) VALUES (?, ?)")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setLong(2, date);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Loan getLoan(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT amount, loan_date, days, paid FROM loans WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Loan loan = new Loan(rs.getDouble("amount"), 
                                   rs.getLong("loan_date"),
                                   rs.getInt("days"));
                loan.setPaid(rs.getBoolean("paid"));
                return loan;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void setLoan(UUID playerUUID, Loan loan) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO loans (uuid, amount, loan_date, days, paid) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET amount = ?, loan_date = ?, days = ?, paid = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, loan.getAmount());
            stmt.setLong(3, loan.getLoanDate());
            stmt.setInt(4, loan.getDays());
            stmt.setBoolean(5, loan.isPaid());
            stmt.setDouble(6, loan.getAmount());
            stmt.setLong(7, loan.getLoanDate());
            stmt.setInt(8, loan.getDays());
            stmt.setBoolean(9, loan.isPaid());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasUnpaidLoan(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT paid FROM loans WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next() && !rs.getBoolean("paid");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Set<UUID> getAllPlayers() {
        Set<UUID> players = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid FROM accounts")) {
            while (rs.next()) {
                players.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        if (amount <= 0) return false;
        try {
            // 检查玩家是否有足够的现金
            if (plugin.getEconomy().getBalance(Bukkit.getPlayer(playerUUID)) < amount) {
                return false;
            }
            
            // 扣除现金
            plugin.getEconomy().withdrawPlayer(Bukkit.getPlayer(playerUUID), amount);
            
            // 增加存款
            double currentBalance = getBalance(playerUUID);
            setBalance(playerUUID, currentBalance + amount);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID playerUUID, double amount) {
        if (amount <= 0) return false;
        try {
            // 检查存款是否足够
            double currentBalance = getBalance(playerUUID);
            if (currentBalance < amount) {
                return false;
            }
            
            // 扣除存款
            setBalance(playerUUID, currentBalance - amount);
            
            // 增加现金
            plugin.getEconomy().depositPlayer(Bukkit.getPlayer(playerUUID), amount);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean createTimeDeposit(UUID playerUUID, double amount, String period) {
        if (amount <= 0) return false;
        try {
            // 检查玩家是否有足够的现金
            if (plugin.getEconomy().getBalance(Bukkit.getPlayer(playerUUID)) < amount) {
                return false;
            }
            
            // 扣除现金
            plugin.getEconomy().withdrawPlayer(Bukkit.getPlayer(playerUUID), amount);
            
            // 创建定期存款
            TimeDeposit deposit = new TimeDeposit(amount, System.currentTimeMillis(), period);
            setTimeDeposit(playerUUID, deposit);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean createLoan(UUID playerUUID, double amount) {
        if (amount <= 0) return false;
        try {
            // 检查是否有未还清的贷款
            if (hasUnpaidLoan(playerUUID)) {
                return false;
            }
            
            // 创建贷款
            Loan loan = new Loan(amount, System.currentTimeMillis(), 30); // 30天期限
            setLoan(playerUUID, loan);
            
            // 增加现金
            plugin.getEconomy().depositPlayer(Bukkit.getPlayer(playerUUID), amount);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean repayLoan(UUID playerUUID, double amount) {
        if (amount <= 0) return false;
        try {
            // 获取贷款信息
            Loan loan = getLoan(playerUUID);
            if (loan == null || loan.isPaid()) {
                return false;
            }
            
            // 检查玩家是否有足够的现金
            if (plugin.getEconomy().getBalance(Bukkit.getPlayer(playerUUID)) < amount) {
                return false;
            }
            
            // 扣除现金
            plugin.getEconomy().withdrawPlayer(Bukkit.getPlayer(playerUUID), amount);
            
            // 更新贷款状态
            double remainingAmount = loan.getAmount() - amount;
            if (remainingAmount <= 0) {
                loan.setPaid(true);
            } else {
                loan.setAmount(remainingAmount);
            }
            setLoan(playerUUID, loan);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void setTempTimeDepositAmount(UUID uuid, double amount) {
        tempTimeDepositAmounts.put(uuid, amount);
    }

    @Override
    public double getTempTimeDepositAmount(UUID uuid) {
        return tempTimeDepositAmounts.getOrDefault(uuid, 0.0);
    }

    @Override
    public void clearTempTimeDepositAmount(UUID uuid) {
        tempTimeDepositAmounts.remove(uuid);
    }

    @Override
    public boolean canWithdrawTimeDeposit(UUID uuid) {
        TimeDeposit deposit = getTimeDeposit(uuid);
        if (deposit == null || deposit.getAmount() <= 0) return false;
        
        long depositEndTime = deposit.getDepositDate();
        String period = deposit.getPeriod();
        
        switch (period.toLowerCase()) {
            case "week": depositEndTime += 7 * 24 * 60 * 60 * 1000L;  break;
            case "month": depositEndTime += 30 * 24 * 60 * 60 * 1000L; break;
            case "year": depositEndTime += 365 * 24 * 60 * 60 * 1000L; break;
            default: return false;
        }
        
        return System.currentTimeMillis() >= depositEndTime;
    }
} 