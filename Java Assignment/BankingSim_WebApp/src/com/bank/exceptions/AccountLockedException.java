package com.bank.exceptions;

/** Thrown when an operation is attempted on a locked account. */
public class AccountLockedException extends BankException {
    private static final long serialVersionUID = 1L;

    public AccountLockedException(String accountNumber) {
        super("Account " + accountNumber + " is locked. Please contact the bank admin.");
    }
}
