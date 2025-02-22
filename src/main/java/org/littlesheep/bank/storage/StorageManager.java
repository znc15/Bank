// 存储管理接口
package org.littlesheep.bank.storage;

import java.util.UUID;
import java.util.Set;
import org.littlesheep.bank.TimeDeposit;
import org.littlesheep.bank.Loan;

public interface StorageManager {
    void init();
    double getBalance(UUID playerUUID);
    void setBalance(UUID playerUUID, double amount);
    TimeDeposit getTimeDeposit(UUID playerUUID);
    void setTimeDeposit(UUID playerUUID, TimeDeposit deposit);
    long getTimeDepositDate(UUID playerUUID);
    void setTimeDepositDate(UUID playerUUID, long date);
    void close();
    Set<UUID> getAllPlayers();
    Loan getLoan(UUID playerUUID);
    void setLoan(UUID playerUUID, Loan loan);
    boolean hasUnpaidLoan(UUID playerUUID);
    boolean deposit(UUID playerUUID, double amount);
    boolean withdraw(UUID playerUUID, double amount);
    boolean createTimeDeposit(UUID playerUUID, double amount, String period);
    boolean createLoan(UUID playerUUID, double amount);
    boolean repayLoan(UUID playerUUID, double amount);
    boolean canWithdrawTimeDeposit(UUID uuid);

    void setTempTimeDepositAmount(UUID uuid, double amount);
    double getTempTimeDepositAmount(UUID uuid);
    void clearTempTimeDepositAmount(UUID uuid);
} 