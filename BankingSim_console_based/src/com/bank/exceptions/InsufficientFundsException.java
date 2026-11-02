package com.bank.exceptions;

/** Thrown when a withdrawal/transfer would break the account's balance rules. */
public class InsufficientFundsException extends BankException {
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super(message);
    }
}
