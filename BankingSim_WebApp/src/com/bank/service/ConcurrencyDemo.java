package com.bank.service;

import com.bank.exceptions.BankException;
import com.bank.model.CurrentAccount;
import com.bank.model.SavingsAccount;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Live demos of why thread synchronization matters. Uses throw-away accounts only. */
public final class ConcurrencyDemo {
    private ConcurrencyDemo() { }

    /** A deliberately BROKEN account: deposit() is not synchronized. */
    private static class UnsafeAccount {
        double balance = 0;

        void deposit(double amount) {
            double current = balance;   // read
            Thread.yield();             // another thread may sneak in here
            balance = current + amount; // write (overwrites the other thread's update!)
        }
    }

    /** Prints the demo to the console. */
    public static void run() throws InterruptedException {
        run(System.out);
    }

    /** Prints the demo to any PrintStream (the web app captures it and shows it in the browser). */
    public static void run(PrintStream out) throws InterruptedException {
        depositRace(out);
        out.println();
        atmWithdrawals(out);
    }

    /** 10 threads x 1000 deposits of Rs. 1 each: the final balance should be 10000. */
    private static void depositRace(PrintStream out) throws InterruptedException {
        final int threads = 10;
        final int perThread = 1000;
        out.println("--- DEMO 1: Deposit race (" + threads + " threads x " + perThread + " deposits of Rs. 1) ---");

        final UnsafeAccount unsafe = new UnsafeAccount();
        runAndWait(threads, "Unsafe-", () -> {
            for (int i = 0; i < perThread; i++) {
                unsafe.deposit(1);
            }
        });

        final SavingsAccount safe = new SavingsAccount("DEMO-1", "Demo", "0000", 0);
        runAndWait(threads, "Safe-", () -> {
            for (int i = 0; i < perThread; i++) {
                try {
                    safe.deposit(1);
                } catch (BankException e) {
                    // not expected in this demo
                }
            }
        });

        out.println("Expected balance            : " + threads * perThread);
        out.println("WITHOUT synchronized        : " + (long) unsafe.balance + "   <-- lost updates!");
        out.println("WITH synchronized (Account) : " + (long) safe.getBalance());
    }

    /** 10 ATMs each try to withdraw Rs. 200 from Rs. 1000: only 5 may succeed. */
    private static void atmWithdrawals(PrintStream out) throws InterruptedException {
        out.println("--- DEMO 2: 10 ATMs withdraw Rs. 200 each from an account holding Rs. 1000 ---");
        final CurrentAccount account = new CurrentAccount("DEMO-2", "Demo", "0000", 1000, 0);
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();

        runAndWait(10, "ATM-", () -> {
            try {
                account.withdraw(200);
                success.incrementAndGet();
            } catch (BankException e) {
                failed.incrementAndGet();
            }
        });

        out.println("Successful withdrawals : " + success.get() + "  (expected 5)");
        out.println("Rejected withdrawals   : " + failed.get() + "  (expected 5, insufficient funds)");
        out.println("Final balance          : Rs. " + account.getBalance() + "  (never negative)");
    }

    private static void runAndWait(int count, String namePrefix, Runnable task) throws InterruptedException {
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 1; i <= count; i++) {
            threads.add(new Thread(task, namePrefix + i));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}
