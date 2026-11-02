package com.bank.exceptions;

/** Thrown when a wrong PIN is entered. Carries the attempts left. */
public class InvalidPinException extends BankException {
    private static final long serialVersionUID = 1L;
    private final int attemptsLeft;

    public InvalidPinException(int attemptsLeft) {
        super("Incorrect PIN. Attempts left: " + attemptsLeft);
        this.attemptsLeft = attemptsLeft;
    }

    public int getAttemptsLeft() {
        return attemptsLeft;
    }
}
