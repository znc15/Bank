// 贷款类
package org.littlesheep.bank;

/**
 * 贷款实体类
 * 存储贷款的基本信息
 */
public class Loan {
    private double amount;      // 贷款金额
    private long loanDate;      // 贷款日期（毫秒时间戳）
    private int days;          // 贷款期限（天）
    private boolean paid;      // 是否已还清

    /**
     * 创建新的贷款
     * @param amount 贷款金额
     * @param loanDate 贷款日期
     * @param days 贷款期限
     */
    public Loan(double amount, long loanDate, int days) {
        this.amount = amount;
        this.loanDate = loanDate;  // 使用传入的时间戳，而不是重新获取
        this.days = days;
        this.paid = false;
    }

    // Getters and Setters
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public long getLoanDate() { return loanDate; }
    public void setLoanDate(long loanDate) { this.loanDate = loanDate; }
    public int getDays() { return days; }
    public void setDays(int days) { this.days = days; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    /**
     * 还款
     * @param amount 还款金额
     */
    public void repay(double amount) {
        this.amount -= amount;
        if (this.amount <= 0) {
            this.paid = true;
        }
    }
} 