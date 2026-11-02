package com.bank.model;

/** The kinds of account the bank offers, with the minimum opening deposit for each. */
public enum AccountType {
    SAVINGS("Savings", SavingsAccount.MIN_BALANCE),
    CURRENT("Current", 1000.0);

    private final String displayName;
    private final double minimumOpeningDeposit;

    AccountType(String displayName, double minimumOpeningDeposit) {
        this.displayName = displayName;
        this.minimumOpeningDeposit = minimumOpeningDeposit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getMinimumOpeningDeposit() {
        return minimumOpeningDeposit;
    }
}
