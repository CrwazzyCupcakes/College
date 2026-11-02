package com.bank.exceptions;

/** Base class for every checked exception in the banking simulator. */
public class BankException extends Exception {
    private static final long serialVersionUID = 1L;

    public BankException(String message) {
        super(message);
    }
}
