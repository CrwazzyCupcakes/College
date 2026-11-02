package com.bank.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** One immutable entry in an account's history. */
public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private final int id;
    private final TransactionType type;
    private final double amount;
    private final double balanceAfter;
    private final String description;
    private final LocalDateTime timestamp;

    public Transaction(int id, TransactionType type, double amount, double balanceAfter, String description) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.timestamp = LocalDateTime.now();
    }

    public int getId() { return id; }
    public TransactionType getType() { return type; }
    public double getAmount() { return amount; }
    public double getBalanceAfter() { return balanceAfter; }
    public String getDescription() { return description; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public String getFormattedTime() {
        return timestamp.format(FORMAT);
    }

    @Override
    public String toString() {
        return String.format("#%-4d %-19s %-13s %10.2f   Bal: %10.2f   %s",
                id, getFormattedTime(), type, amount, balanceAfter, description);
    }
}
