package com.bank.model;

import com.bank.exceptions.BankException;

/** Anything that can receive and give out money. */
public interface Transactable {
    void deposit(double amount) throws BankException;

    void withdraw(double amount) throws BankException;
}
