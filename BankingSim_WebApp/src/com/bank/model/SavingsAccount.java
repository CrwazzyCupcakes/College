package com.bank.model;

import com.bank.exceptions.BankException;

/** Savings account: must keep a minimum balance and earns monthly interest. */
public class SavingsAccount extends Account {
    private static final long serialVersionUID = 1L;

    public static final double MIN_BALANCE = 500.0;
    public static final double ANNUAL_INTEREST_RATE = 4.0; // percent

    public SavingsAccount(String accountNumber, String holderName, String pin, double openingDeposit) {
        super(accountNumber, holderName, pin, openingDeposit);
    }

    @Override
    public String getAccountType() {
        return "Savings";
    }

    @Override
    protected double getMinimumAllowedBalance() {
        return MIN_BALANCE;
    }

    /** Credits one month of interest. Returns the amount credited (0 if none). */
    public synchronized double applyMonthlyInterest() {
        double interest = Math.round(getBalance() * ANNUAL_INTEREST_RATE / 12 / 100 * 100.0) / 100.0;
        if (interest <= 0) {
            return 0;
        }
        try {
            credit(interest, TransactionType.INTEREST, "Monthly interest @ " + ANNUAL_INTEREST_RATE + "% p.a.");
        } catch (BankException e) {
            return 0;
        }
        return interest;
    }
}
