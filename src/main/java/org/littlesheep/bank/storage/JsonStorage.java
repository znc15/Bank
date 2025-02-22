// Json存储类
package org.littlesheep.bank.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.littlesheep.bank.Bank;
import org.littlesheep.bank.TimeDeposit;
import org.littlesheep.bank.Loan;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class JsonStorage implements StorageManager {
    private final File dataFile;
    private final Gson gson;
    private Map<UUID, AccountData> accounts;
    private final Map<UUID, Double> tempTimeDepositAmounts = new HashMap<>();

    private static class AccountData {
        double balance = 0.0;
        TimeDeposit timeDeposit = new TimeDeposit(0, 0, "");
        Loan loan = null;
    }

    public JsonStorage(Bank plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "accounts.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.accounts = new HashMap<>();
    }

    @Override
    public void init() {
        if (!dataFile.exists()) {
            save();
        } else {
            load();
        }
    }

    @Override
    public double getBalance(UUID playerUUID) {
        return accounts.computeIfAbsent(playerUUID, k -> new AccountData()).balance;
    }

    @Override
    public void setBalance(UUID playerUUID, double amount) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.balance = amount;
        save();
    }

    @Override
    public TimeDeposit getTimeDeposit(UUID playerUUID) {
        return accounts.computeIfAbsent(playerUUID, k -> new AccountData()).timeDeposit;
    }

    @Override
    public void setTimeDeposit(UUID playerUUID, TimeDeposit deposit) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.timeDeposit = deposit;
        save();
    }

    @Override
    public long getTimeDepositDate(UUID playerUUID) {
        return accounts.computeIfAbsent(playerUUID, k -> new AccountData()).timeDeposit.getDepositDate();
    }

    @Override
    public void setTimeDepositDate(UUID playerUUID, long date) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.timeDeposit.setDepositDate(date);
        save();
    }

    @Override
    public Loan getLoan(UUID playerUUID) {
        return accounts.computeIfAbsent(playerUUID, k -> new AccountData()).loan;
    }

    @Override
    public void setLoan(UUID playerUUID, Loan loan) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.loan = loan;
        save();
    }

    @Override
    public boolean hasUnpaidLoan(UUID playerUUID) {
        AccountData account = accounts.get(playerUUID);
        return account != null && account.loan != null && !account.loan.isPaid();
    }

    @Override
    public boolean deposit(UUID playerUUID, double amount) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.balance += amount;
        save();
        return true;
    }

    @Override
    public boolean withdraw(UUID playerUUID, double amount) {
        AccountData account = accounts.get(playerUUID);
        if (account == null || account.balance < amount) {
            return false;
        }
        account.balance -= amount;
        save();
        return true;
    }

    @Override
    public boolean createTimeDeposit(UUID playerUUID, double amount, String period) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.timeDeposit = new TimeDeposit(amount, period, System.currentTimeMillis());
        save();
        return true;
    }

    @Override
    public boolean createLoan(UUID playerUUID, double amount) {
        AccountData account = accounts.computeIfAbsent(playerUUID, k -> new AccountData());
        account.loan = new Loan(amount, System.currentTimeMillis(), 30);
        save();
        return true;
    }

    @Override
    public boolean repayLoan(UUID playerUUID, double amount) {
        AccountData account = accounts.get(playerUUID);
        if (account == null || account.loan == null || account.loan.isPaid()) {
            return false;
        }
        account.loan.repay(amount);
        save();
        return true;
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

    private void load() {
        try (Reader reader = new FileReader(dataFile)) {
            TypeToken<HashMap<UUID, AccountData>> type = new TypeToken<HashMap<UUID, AccountData>>() {};
            accounts = gson.fromJson(reader, type.getType());
            if (accounts == null) accounts = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void save() {
        try (Writer writer = new FileWriter(dataFile)) {
            gson.toJson(accounts, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        save();
    }

    @Override
    public Set<UUID> getAllPlayers() {
        return new HashSet<>(accounts.keySet());
    }
} 