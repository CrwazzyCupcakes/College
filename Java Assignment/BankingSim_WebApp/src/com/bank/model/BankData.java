package com.bank.model;

import com.bank.util.Repository;

import java.io.Serializable;

/** Everything that gets saved to disk: all accounts plus the next account number. */
public class BankData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Repository<Account> accounts;
    private final int nextAccountNumber;

    public BankData(Repository<Account> accounts, int nextAccountNumber) {
        this.accounts = accounts;
        this.nextAccountNumber = nextAccountNumber;
    }

    public Repository<Account> getAccounts() { return accounts; }
    public int getNextAccountNumber() { return nextAccountNumber; }
}
