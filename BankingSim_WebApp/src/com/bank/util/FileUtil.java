package com.bank.util;

import com.bank.model.Account;
import com.bank.model.BankData;
import com.bank.model.Transaction;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** All file handling: serialization (save/load), backups (NIO) and CSV export. */
public final class FileUtil {
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path DATA_FILE = DATA_DIR.resolve("bank.dat");
    private static final Path BACKUP_DIR = DATA_DIR.resolve("backups");
    private static final Path EXPORT_DIR = DATA_DIR.resolve("exports");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private FileUtil() { }

    /** Object serialization: writes the whole bank to data/bank.dat. */
    public static void save(BankData data) throws IOException {
        Files.createDirectories(DATA_DIR);
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(DATA_FILE)))) {
            out.writeObject(data);
        }
    }

    /** Deserialization: returns null if there is no saved file yet. */
    public static BankData load() throws IOException, ClassNotFoundException {
        if (!Files.exists(DATA_FILE)) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(DATA_FILE)))) {
            return (BankData) in.readObject();
        }
    }

    /** Copies the data file into data/backups/ with a timestamp. */
    public static Path backup() throws IOException {
        if (!Files.exists(DATA_FILE)) {
            throw new IOException("Nothing to back up yet - no data has been saved.");
        }
        Files.createDirectories(BACKUP_DIR);
        Path target = BACKUP_DIR.resolve("bank_" + LocalDateTime.now().format(STAMP) + ".dat");
        return Files.copy(DATA_FILE, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Builds the CSV text for an account's transactions. */
    public static String buildCsv(Account account) {
        StringBuilder sb = new StringBuilder("Id,DateTime,Type,Amount,BalanceAfter,Description\n");
        for (Transaction t : account.getTransactions()) {
            sb.append(String.format("%d,%s,%s,%.2f,%.2f,\"%s\"%n",
                    t.getId(), t.getFormattedTime(), t.getType(),
                    t.getAmount(), t.getBalanceAfter(), t.getDescription()));
        }
        return sb.toString();
    }

    /** Writes an account's transactions to data/exports/<acc>_<time>.csv */
    public static Path exportStatementCsv(Account account) throws IOException {
        Files.createDirectories(EXPORT_DIR);
        Path file = EXPORT_DIR.resolve(account.getAccountNumber() + "_" + LocalDateTime.now().format(STAMP) + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(buildCsv(account));
        }
        return file;
    }
}
