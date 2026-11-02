package com.bank.model;

import com.bank.exceptions.AccountLockedException;
import com.bank.exceptions.BankException;
import com.bank.exceptions.InsufficientFundsException;
import com.bank.exceptions.InvalidAmountException;
import com.bank.exceptions.InvalidPinException;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for all accounts.
 * Every method that touches the balance is synchronized, so several threads
 * (ATMs / customers) can use the same account safely.
 */
public abstract class Account implements Transactable, Reportable, Serializable {
    private static final long serialVersionUID = 1L;
    public static final int MAX_PIN_ATTEMPTS = 3;

    private final String accountNumber;
    private final String holderName;
    private String pin;
    private double balance;
    private boolean locked;
    private int failedAttempts;
    private final List<Transaction> transactions = new ArrayList<Transaction>();

    protected Account(String accountNumber, String holderName, String pin, double openingDeposit) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = openingDeposit;
        if (openingDeposit > 0) {
            addTransaction(TransactionType.DEPOSIT, openingDeposit, "Opening deposit");
        }
    }

    // ---------- subclasses decide these rules (polymorphism) ----------

    /** "Savings" or "Current". */
    public abstract String getAccountType();

    /** Lowest balance the account may reach (negative for overdraft). */
    protected abstract double getMinimumAllowedBalance();

    // ---------- money operations ----------

    @Override
    public synchronized void deposit(double amount) throws BankException {
        ensureNotLocked();
        credit(amount, TransactionType.DEPOSIT, "Cash deposit");
    }

    @Override
    public synchronized void withdraw(double amount) throws BankException {
        ensureNotLocked();
        debit(amount, TransactionType.WITHDRAWAL, "Cash withdrawal");
    }

    /** Adds money and records it. Also used for transfers and interest. */
    public synchronized void credit(double amount, TransactionType type, String description)
            throws BankException {
        validateAmount(amount);
        balance += amount;
        addTransaction(type, amount, description);
    }

    /** Removes money (if the account rules allow) and records it. */
    public synchronized void debit(double amount, TransactionType type, String description)
            throws BankException {
        validateAmount(amount);
        if (balance - amount < getMinimumAllowedBalance()) {
            throw new InsufficientFundsException(String.format(
                    "Insufficient funds. You can withdraw at most Rs. %.2f", getWithdrawableAmount()));
        }
        balance -= amount;
        addTransaction(type, amount, description);
    }

    public synchronized double getWithdrawableAmount() {
        return Math.max(0, balance - getMinimumAllowedBalance());
    }

    public synchronized double getBalance() {
        return balance;
    }

    // ---------- PIN and locking ----------

    /** Checks the PIN. Three wrong attempts in a row lock the account. */
    public synchronized void verifyPin(String entered) throws BankException {
        ensureNotLocked();
        if (pin.equals(entered)) {
            failedAttempts = 0;
            return;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_PIN_ATTEMPTS) {
            locked = true;
            throw new AccountLockedException(accountNumber);
        }
        throw new InvalidPinException(MAX_PIN_ATTEMPTS - failedAttempts);
    }

    public synchronized void changePin(String oldPin, String newPin) throws BankException {
        verifyPin(oldPin);
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new InvalidAmountException("New PIN must be exactly 4 digits.");
        }
        pin = newPin;
    }

    public synchronized void lock() {
        locked = true;
    }

    public synchronized void unlock() {
        locked = false;
        failedAttempts = 0;
    }

    public synchronized boolean isLocked() {
        return locked;
    }

    private void ensureNotLocked() throws AccountLockedException {
        if (locked) {
            throw new AccountLockedException(accountNumber);
        }
    }

    // ---------- history and reports ----------

    public synchronized List<Transaction> getTransactions() {
        return new ArrayList<Transaction>(transactions); // defensive copy
    }

    @Override
    public synchronized String generateStatement() {
        StringBuilder sb = new StringBuilder();
        sb.append("======== ACCOUNT STATEMENT ========\n");
        sb.append("Account : ").append(accountNumber).append(" (").append(getAccountType()).append(")\n");
        sb.append("Holder  : ").append(holderName).append("\n");
        sb.append(String.format("Balance : Rs. %.2f%n", balance));
        sb.append("-----------------------------------\n");
        if (transactions.isEmpty()) {
            sb.append("No transactions yet.\n");
        }
        for (Transaction t : transactions) {
            sb.append(t).append("\n");
        }
        sb.append("===================================");
        return sb.toString();
    }

    public synchronized String getSummary() {
        return String.format("%-9s %-8s %-18s Rs. %10.2f  %s",
                accountNumber, getAccountType(), holderName, balance, locked ? "[LOCKED]" : "");
    }

    // ---------- helpers ----------

    private void addTransaction(TransactionType type, double amount, String description) {
        transactions.add(new Transaction(transactions.size() + 1, type, amount, balance, description));
    }

    private void validateAmount(double amount) throws InvalidAmountException {
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
    }

    public String getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }

    /** Locks this account while it is being serialized so a half-updated state is never saved. */
    private synchronized void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
}
