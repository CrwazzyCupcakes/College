package com.bank.main;

import com.bank.exceptions.BankException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.service.BankService;
import com.bank.service.ConcurrencyDemo;
import com.bank.service.InterestScheduler;
import com.bank.util.FileUtil;
import com.bank.util.InputHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Console front-end for the banking simulator. Run this class. */
public class BankApp {
    private static final long INTEREST_INTERVAL_MS = 10_000; // 10 seconds = 1 "month" in the demo

    private final BankService bank = new BankService();
    private final InputHelper in = new InputHelper();
    private final InterestScheduler scheduler = new InterestScheduler(bank, INTEREST_INTERVAL_MS);

    public static void main(String[] args) {
        new BankApp().run();
    }

    // ================= main menu =================

    private void run() {
        System.out.println("==========================================");
        System.out.println("        JAVA BANKING SIMULATOR");
        System.out.println("==========================================");
        try {
            boolean running = true;
            while (running) {
                System.out.println("\n---- MAIN MENU ----");
                System.out.println("1. Create new account");
                System.out.println("2. Customer login");
                System.out.println("3. Admin login");
                System.out.println("4. Exit");
                int choice = in.readInt("Choose: ");
                switch (choice) {
                    case 1: createAccount(); break;
                    case 2: customerLogin(); break;
                    case 3: adminLogin(); break;
                    case 4: running = false; break;
                    default: System.out.println("Invalid choice. Try again.");
                }
            }
        } catch (NoSuchElementException e) {
            System.out.println("\nInput closed.");
        } finally {
            scheduler.stop();
            bank.save();
            System.out.println("Data saved. Goodbye!");
        }
    }

    // ================= account creation =================

    private void createAccount() {
        System.out.println("\n-- Create Account --");
        System.out.println("1. Savings  (min opening Rs. " + AccountType.SAVINGS.getMinimumOpeningDeposit()
                + ", keeps min balance, earns interest)");
        System.out.println("2. Current  (min opening Rs. " + AccountType.CURRENT.getMinimumOpeningDeposit()
                + ", overdraft allowed)");
        int typeChoice = in.readInt("Account type: ");
        if (typeChoice != 1 && typeChoice != 2) {
            System.out.println("Invalid account type.");
            return;
        }
        AccountType type = (typeChoice == 1) ? AccountType.SAVINGS : AccountType.CURRENT;
        String name = in.readLine("Holder name: ");
        String pin = in.readLine("Choose a 4-digit PIN: ");
        double deposit = in.readDouble("Opening deposit: ");
        try {
            Account account = bank.createAccount(type, name, pin, deposit);
            bank.save();
            System.out.println("\nAccount created successfully!");
            System.out.println("Your account number is: " + account.getAccountNumber() + "  (remember it!)");
        } catch (BankException e) {
            System.out.println("Could not create account: " + e.getMessage());
        }
    }

    // ================= customer =================

    private void customerLogin() {
        System.out.println("\n-- Customer Login --");
        String number = in.readLine("Account number: ");
        String pin = in.readLine("PIN: ");
        try {
            Account account = bank.login(number, pin);
            bank.save();
            customerSession(account);
        } catch (BankException e) {
            System.out.println("Login failed: " + e.getMessage());
            bank.save(); // failed attempts / locking are saved too
        }
    }

    private void customerSession(Account account) {
        System.out.println("\nWelcome, " + account.getHolderName() + "!");
        boolean active = true;
        while (active) {
            System.out.println("\n---- ACCOUNT MENU (" + account.getAccountNumber() + ", "
                    + account.getAccountType() + ") ----");
            System.out.println("1. Check balance");
            System.out.println("2. Deposit");
            System.out.println("3. Withdraw");
            System.out.println("4. Transfer to another account");
            System.out.println("5. Mini statement (last 5)");
            System.out.println("6. Full statement (sortable)");
            System.out.println("7. Export statement to CSV");
            System.out.println("8. Change PIN");
            System.out.println("9. Logout");
            int choice = in.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1:
                        System.out.printf("Current balance: Rs. %.2f%n", account.getBalance());
                        break;
                    case 2: {
                        double amount = in.readDouble("Amount to deposit: ");
                        account.deposit(amount);
                        bank.save();
                        System.out.printf("Deposited Rs. %.2f. New balance: Rs. %.2f%n", amount, account.getBalance());
                        break;
                    }
                    case 3: {
                        double amount = in.readDouble("Amount to withdraw: ");
                        account.withdraw(amount);
                        bank.save();
                        System.out.printf("Withdrawn Rs. %.2f. New balance: Rs. %.2f%n", amount, account.getBalance());
                        break;
                    }
                    case 4: {
                        String target = in.readLine("Destination account number: ");
                        double amount = in.readDouble("Amount to transfer: ");
                        bank.transfer(account.getAccountNumber(), target, amount);
                        bank.save();
                        System.out.printf("Transferred Rs. %.2f to %s. New balance: Rs. %.2f%n",
                                amount, target.trim().toUpperCase(), account.getBalance());
                        break;
                    }
                    case 5:
                        showMiniStatement(account);
                        break;
                    case 6:
                        showFullStatement(account);
                        break;
                    case 7: {
                        Path file = FileUtil.exportStatementCsv(account);
                        System.out.println("Statement exported to: " + file.toAbsolutePath());
                        break;
                    }
                    case 8: {
                        String oldPin = in.readLine("Current PIN: ");
                        String newPin = in.readLine("New 4-digit PIN: ");
                        account.changePin(oldPin, newPin);
                        bank.save();
                        System.out.println("PIN changed successfully.");
                        break;
                    }
                    case 9:
                        active = false;
                        System.out.println("Logged out. Thank you for banking with us!");
                        break;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            } catch (BankException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("File error: " + e.getMessage());
            }
        }
    }

    private void showMiniStatement(Account account) {
        List<Transaction> all = account.getTransactions();
        System.out.println("\n-- Last 5 transactions --");
        if (all.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        int from = Math.max(0, all.size() - 5);
        for (Transaction t : all.subList(from, all.size())) {
            System.out.println(t);
        }
    }

    private void showFullStatement(Account account) {
        System.out.println("Sort by: 1) Newest first  2) Oldest first  3) Highest amount first");
        int sort = in.readInt("Choose: ");

        // Comparators built with lambdas / method references
        Comparator<Transaction> byTime = Comparator.comparing(Transaction::getTimestamp)
                .thenComparingInt(Transaction::getId);
        Comparator<Transaction> comparator;
        switch (sort) {
            case 1: comparator = byTime.reversed(); break;
            case 3: comparator = Comparator.comparingDouble(Transaction::getAmount).reversed(); break;
            default: comparator = byTime;
        }

        List<Transaction> sorted = new ArrayList<Transaction>(account.getTransactions());
        sorted.sort(comparator);

        System.out.println("\n======== FULL STATEMENT: " + account.getAccountNumber() + " ========");
        if (sorted.isEmpty()) {
            System.out.println("No transactions yet.");
        }
        sorted.forEach(System.out::println);
        System.out.printf("Closing balance: Rs. %.2f%n", account.getBalance());
    }

    // ================= admin =================

    private void adminLogin() {
        String password = in.readLine("\nAdmin password: ");
        if (!bank.verifyAdmin(password)) {
            System.out.println("Wrong password.");
            return;
        }
        adminSession();
    }

    private void adminSession() {
        boolean active = true;
        while (active) {
            System.out.println("\n---- ADMIN MENU ----");
            System.out.println("1. List all accounts");
            System.out.println("2. Find accounts with balance above an amount");
            System.out.println("3. Bank summary");
            System.out.println("4. Lock / unlock an account");
            System.out.println("5. " + (scheduler.isRunning() ? "Stop" : "Start")
                    + " interest scheduler (10 sec = 1 month)");
            System.out.println("6. Apply one month of interest now");
            System.out.println("7. Run thread-synchronization demo");
            System.out.println("8. Backup data");
            System.out.println("9. Back to main menu");
            int choice = in.readInt("Choose: ");
            try {
                switch (choice) {
                    case 1: listAccounts(); break;
                    case 2: {
                        double threshold = in.readDouble("Show accounts with balance above Rs.: ");
                        List<Account> found = bank.getAccountsAbove(threshold);
                        if (found.isEmpty()) {
                            System.out.println("No accounts found.");
                        }
                        found.forEach(a -> System.out.println(a.getSummary()));
                        break;
                    }
                    case 3: showSummary(); break;
                    case 4: {
                        String number = in.readLine("Account number: ");
                        String action = in.readLine("Type L to lock or U to unlock: ");
                        if (action.equalsIgnoreCase("L")) {
                            bank.setLocked(number, true);
                            System.out.println("Account locked.");
                        } else if (action.equalsIgnoreCase("U")) {
                            bank.setLocked(number, false);
                            System.out.println("Account unlocked.");
                        } else {
                            System.out.println("Invalid option.");
                        }
                        bank.save();
                        break;
                    }
                    case 5:
                        if (scheduler.isRunning()) {
                            scheduler.stop();
                            System.out.println("Interest scheduler stopped.");
                        } else {
                            scheduler.start();
                            System.out.println("Interest scheduler started (runs in the background).");
                        }
                        break;
                    case 6: {
                        int paid = bank.applyInterestToAll();
                        System.out.println("Interest paid to " + paid + " savings account(s).");
                        break;
                    }
                    case 7: ConcurrencyDemo.run(); break;
                    case 8: {
                        bank.save();
                        Path backup = FileUtil.backup();
                        System.out.println("Backup created: " + backup.toAbsolutePath());
                        break;
                    }
                    case 9: active = false; break;
                    default: System.out.println("Invalid choice. Try again.");
                }
            } catch (BankException e) {
                System.out.println("Error: " + e.getMessage());
            } catch (IOException e) {
                System.out.println("File error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Demo interrupted.");
            }
        }
    }

    private void listAccounts() {
        List<Account> all = bank.getAllAccounts();
        if (all.isEmpty()) {
            System.out.println("No accounts yet.");
            return;
        }
        System.out.println("\n" + all.size() + " account(s):");
        all.forEach(a -> System.out.println(a.getSummary()));
    }

    private void showSummary() {
        System.out.println("\n-- Bank Summary --");
        System.out.println("Total accounts : " + bank.getAccountCount());
        System.out.printf("Total holdings : Rs. %.2f%n", bank.getTotalHoldings());
        for (Map.Entry<String, Long> entry : bank.countByType().entrySet()) {
            System.out.println(entry.getKey() + " accounts : " + entry.getValue());
        }
        Optional<Account> richest = bank.getRichestAccount();
        if (richest.isPresent()) {
            System.out.println("Highest balance: " + richest.get().getSummary());
        }
    }
}
