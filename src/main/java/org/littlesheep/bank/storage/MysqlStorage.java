// Mysql存储类
package org.littlesheep.bank.storage;

import org.littlesheep.bank.Bank;
import org.littlesheep.bank.TimeDeposit;
import org.littlesheep.bank.Loan;

import java.sql.*;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class MysqlStorage implements StorageManager {
    private Connection connection;
    private final Bank plugin;
    private final Map<UUID, Double> tempTimeDepositAmounts = new HashMap<>();

    public MysqlStorage(Bank plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        try {
            String host = plugin.getConfig().getString("mysql.host");
            int port = plugin.getConfig().getInt("mysql.port");
            String database = plugin.getConfig().getString("mysql.database");
            String username = plugin.getConfig().getString("mysql.username");
            String password = plugin.getConfig().getString("mysql.password");
            String table = plugin.getConfig().getString("mysql.table");

            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + database, 
                    username, password);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS " + table + "_demand (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "balance DOUBLE DEFAULT 0" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + table + "_time (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "amount DOUBLE DEFAULT 0," +
                        "deposit_date BIGINT DEFAULT 0," +
                        "period VARCHAR(10) DEFAULT 'week'" +
                        ")");
                stmt.execute("CREATE TABLE IF NOT EXISTS " + table + "_loans (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "amount DOUBLE DEFAULT 0," +
                        "loan_date BIGINT DEFAULT 0," +
                        "days INT DEFAULT 0," +
                        "paid BOOLEAN DEFAULT FALSE" +
                        ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT balance FROM " + plugin.getConfig().getString("mysql.table") + 
                " WHERE uuid = ?")) {
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
                "INSERT INTO " + plugin.getConfig().getString("mysql.table") + 
                " (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = ?")) {
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
                "SELECT amount, deposit_date, period FROM " + plugin.getConfig().getString("mysql.table") + "_time WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new TimeDeposit(rs.getDouble("amount"), 
                                     rs.getLong("deposit_date"),
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
                "INSERT INTO " + plugin.getConfig().getString("mysql.table") + "_time (uuid, amount, deposit_date) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = ?, deposit_date = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, deposit.getAmount());
            stmt.setLong(3, deposit.getDepositDate());
            stmt.setDouble(4, deposit.getAmount());
            stmt.setLong(5, deposit.getDepositDate());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getTimeDepositDate(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT time_deposit_date FROM " + plugin.getConfig().getString("mysql.table") + 
                " WHERE uuid = ?")) {
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
                "INSERT INTO " + plugin.getConfig().getString("mysql.table") + 
                " (uuid, time_deposit_date) VALUES (?, ?) ON DUPLICATE KEY UPDATE time_deposit_date = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setLong(2, date);
            stmt.setLong(3, date);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
             ResultSet rs = stmt.executeQuery("SELECT uuid FROM " + plugin.getConfig().getString("mysql.table"))) {
            while (rs.next()) {
                players.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return players;
    }

    @Override
    public Loan getLoan(UUID playerUUID) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT amount, loan_date, days, paid FROM " + 
                plugin.getConfig().getString("mysql.table") + "_loans WHERE uuid = ?")) {
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
        String table = plugin.getConfig().getString("mysql.table") + "_loans";
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO " + table + " (uuid, amount, loan_date, days, paid) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "amount = ?, loan_date = ?, days = ?, paid = ?")) {
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
                "SELECT paid FROM " + plugin.getConfig().getString("mysql.table") + 
                "_loans WHERE uuid = ?")) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next() && !rs.getBoolean("paid");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean repayLoan(UUID playerUUID, double amount) {
        Loan loan = getLoan(playerUUID);
        if (loan == null || loan.isPaid()) {
            return false;
        }
        
        loan.repay(amount);
        setLoan(playerUUID, loan);
        return true;
    }

    @Override
    public boolean withdraw(UUID playerUUID, double amount) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("mysql.table") + "_demand " +
                "SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            stmt.setDouble(3, amount);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("mysql.table") + "_demand " +
                "SET balance = balance + ? WHERE uuid = ?")) {
            stmt.setDouble(1, amount);
            stmt.setString(2, playerUUID.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean createTimeDeposit(UUID playerUUID, double amount, String period) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO " + plugin.getConfig().getString("mysql.table") + "_time " +
                "(uuid, amount, deposit_date, period) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE amount = ?, deposit_date = ?, period = ?")) {
            long now = System.currentTimeMillis();
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, amount);
            stmt.setLong(3, now);
            stmt.setString(4, period);
            stmt.setDouble(5, amount);
            stmt.setLong(6, now);
            stmt.setString(7, period);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean createLoan(UUID playerUUID, double amount) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO " + plugin.getConfig().getString("mysql.table") + "_loans " +
                "(uuid, amount, loan_date, days, paid) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setDouble(2, amount);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setInt(4, 30);
            stmt.setBoolean(5, false);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
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