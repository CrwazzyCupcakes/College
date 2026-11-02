package com.bank.util;

import java.util.Scanner;

/** Wraps Scanner so the menus can ask for numbers without crashing on bad input. */
public class InputHelper {
    private final Scanner scanner = new Scanner(System.in);

    /** Prints the prompt and returns the whole line, trimmed. */
    public String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    /** Keeps asking until the user types a valid whole number. */
    public int readInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    /** Keeps asking until the user types a valid number. */
    public double readDouble(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid amount (e.g. 2500 or 2500.50).");
            }
        }
    }
}
