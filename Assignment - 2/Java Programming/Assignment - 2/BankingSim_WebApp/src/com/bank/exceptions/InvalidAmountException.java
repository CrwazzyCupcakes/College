package com.bank.exceptions;

/** Thrown for zero, negative or otherwise invalid amounts / inputs. */
public class InvalidAmountException extends BankException {
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }
}
