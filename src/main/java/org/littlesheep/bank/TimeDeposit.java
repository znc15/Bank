// 定期存款类
package org.littlesheep.bank;

public class TimeDeposit {
    private double amount;
    private long depositDate;
    private String period; // "week", "month", "year"

    public TimeDeposit() {
        this.amount = 0.0;
        this.depositDate = 0L;
    }

    public TimeDeposit(double amount, long depositDate, String period) {
        this.amount = amount;
        this.depositDate = depositDate;
        this.period = period;
    }

    public TimeDeposit(double amount, String period, long depositDate) {
        this.amount = amount;
        this.period = period;
        this.depositDate = depositDate;
    }

    public double getAmount() { return amount; }
    public long getDepositDate() { return depositDate; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setDepositDate(long date) { this.depositDate = date; }
    public String getPeriod() { return period; }
} 