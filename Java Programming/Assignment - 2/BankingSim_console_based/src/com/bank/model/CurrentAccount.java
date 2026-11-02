package com.bank.model;

/** Current account: no minimum balance, but may go into overdraft up to a limit. */
public class CurrentAccount extends Account {
    private static final long serialVersionUID = 1L;

    public static final double DEFAULT_OVERDRAFT_LIMIT = 10000.0;

    private final double overdraftLimit;

    public CurrentAccount(String accountNumber, String holderName, String pin, double openingDeposit) {
        this(accountNumber, holderName, pin, openingDeposit, DEFAULT_OVERDRAFT_LIMIT);
    }

    // constructor overloading
    public CurrentAccount(String accountNumber, String holderName, String pin,
                          double openingDeposit, double overdraftLimit) {
        super(accountNumber, holderName, pin, openingDeposit);
        this.overdraftLimit = overdraftLimit;
    }

    @Override
    public String getAccountType() {
        return "Current";
    }

    @Override
    protected double getMinimumAllowedBalance() {
        return -overdraftLimit;
    }

    public double getOverdraftLimit() {
        return overdraftLimit;
    }
}
