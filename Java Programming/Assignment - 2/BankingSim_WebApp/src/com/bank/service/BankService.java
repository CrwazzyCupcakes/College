package com.bank.service;

import com.bank.exceptions.AccountLockedException;
import com.bank.exceptions.AccountNotFoundException;
import com.bank.exceptions.BankException;
import com.bank.exceptions.InvalidAmountException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.BankData;
import com.bank.model.CurrentAccount;
import com.bank.model.SavingsAccount;
import com.bank.model.TransactionType;
import com.bank.util.FileUtil;
import com.bank.util.Repository;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** The "brain" of the bank: creating accounts, login, transfers, reports and saving. */
public class BankService {
    private static final String ADMIN_PASSWORD = "admin123";

    private Repository<Account> accounts = new Repository<Account>();
    private int nextAccountNumber = 1001;

    public BankService() {
        loadFromDisk();
    }

    // ---------- persistence ----------

    private void loadFromDisk() {
        try {
            BankData data = FileUtil.load();
            if (data != null) {
                accounts = data.getAccounts();
                nextAccountNumber = data.getNextAccountNumber();
                System.out.println("Loaded " + accounts.count() + " account(s) from saved data.");
            }
        } catch (IOException | ClassNotFoundException e) { // multi-catch
            System.out.println("Warning: could not read saved data (" + e.getMessage() + "). Starting fresh.");
        }
    }

    public synchronized void save() {
        try {
            FileUtil.save(new BankData(accounts, nextAccountNumber));
        } catch (IOException e) {
            System.out.println("Warning: could not save data: " + e.getMessage());
        }
    }

    // ---------- accounts ----------

    public synchronized Account createAccount(AccountType type, String name, String pin, double initialDeposit)
            throws BankException {
        if (name == null || name.trim().isEmpty()) {
            throw new BankException("Name cannot be empty.");
        }
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new BankException("PIN must be exactly 4 digits.");
        }
        if (initialDeposit < type.getMinimumOpeningDeposit()) {
            throw new InvalidAmountException(String.format(
                    "A %s account needs an opening deposit of at least Rs. %.2f.",
                    type.getDisplayName(), type.getMinimumOpeningDeposit()));
        }
        String number = "ACC" + nextAccountNumber++;
        Account account = (type == AccountType.SAVINGS)
                ? new SavingsAccount(number, name.trim(), pin, initialDeposit)
                : new CurrentAccount(number, name.trim(), pin, initialDeposit);
        accounts.add(number, account);
        return account;
    }

    public Account findAccount(String accountNumber) throws AccountNotFoundException {
        Account account = accounts.find(accountNumber.trim().toUpperCase());
        if (account == null) {
            throw new AccountNotFoundException(accountNumber);
        }
        return account;
    }

    public Account login(String accountNumber, String pin) throws BankException {
        Account account = findAccount(accountNumber);
        account.verifyPin(pin);
        return account;
    }

    // ---------- transfer ----------

    /**
     * Moves money between two accounts atomically.
     * Both accounts are locked in a fixed order (by account number) so two opposite
     * transfers at the same time can never deadlock each other.
     */
    public void transfer(String fromNumber, String toNumber, double amount) throws BankException {
        Account from = findAccount(fromNumber);
        Account to = findAccount(toNumber);
        if (from == to) {
            throw new InvalidAmountException("Cannot transfer to the same account.");
        }
        Account first = from.getAccountNumber().compareTo(to.getAccountNumber()) < 0 ? from : to;
        Account second = (first == from) ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.isLocked()) {
                    throw new AccountLockedException(from.getAccountNumber());
                }
                if (to.isLocked()) {
                    throw new AccountLockedException(to.getAccountNumber());
                }
                from.debit(amount, TransactionType.TRANSFER_OUT, "Transfer to " + to.getAccountNumber());
                to.credit(amount, TransactionType.TRANSFER_IN, "Transfer from " + from.getAccountNumber());
            }
        }
    }

    // ---------- admin ----------

    public boolean verifyAdmin(String password) {
        return ADMIN_PASSWORD.equals(password);
    }

    public void setLocked(String accountNumber, boolean lock) throws AccountNotFoundException {
        Account account = findAccount(accountNumber);
        if (lock) {
            account.lock();
        } else {
            account.unlock();
        }
    }

    /** Applies one month of interest to every unlocked savings account. Returns how many were paid. */
    public int applyInterestToAll() {
        int paid = 0;
        for (Account account : accounts.findAll()) {
            if (account instanceof SavingsAccount && !account.isLocked()) {
                if (((SavingsAccount) account).applyMonthlyInterest() > 0) {
                    paid++;
                }
            }
        }
        if (paid > 0) {
            save();
        }
        return paid;
    }

    // ---------- reports (Java 8 streams + lambdas) ----------

    public List<Account> getAllAccounts() {
        return accounts.findAll().stream()
                .sorted(Comparator.comparing(Account::getAccountNumber))
                .collect(Collectors.toList());
    }

    public List<Account> getAccountsAbove(double threshold) {
        return accounts.findAll().stream()
                .filter(a -> a.getBalance() > threshold)
                .sorted(Comparator.comparingDouble(Account::getBalance).reversed())
                .collect(Collectors.toList());
    }

    public double getTotalHoldings() {
        return accounts.findAll().stream().mapToDouble(Account::getBalance).sum();
    }

    public Map<String, Long> countByType() {
        return accounts.findAll().stream()
                .collect(Collectors.groupingBy(Account::getAccountType, TreeMap::new, Collectors.counting()));
    }

    public Optional<Account> getRichestAccount() {
        return accounts.findAll().stream().max(Comparator.comparingDouble(Account::getBalance));
    }

    public int getAccountCount() {
        return accounts.count();
    }
}
