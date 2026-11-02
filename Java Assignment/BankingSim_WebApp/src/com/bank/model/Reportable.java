package com.bank.model;

/** Anything that can produce a printable statement. */
public interface Reportable {
    String generateStatement();
}
