package com.bank.service;

/**
 * Background thread that pays interest on all savings accounts at a fixed interval.
 * For the demo, one "month" passes every few seconds.
 */
public class InterestScheduler implements Runnable {
    private final BankService bank;
    private final long intervalMillis;
    private volatile boolean running = false;
    private Thread thread;

    public InterestScheduler(BankService bank, long intervalMillis) {
        this.bank = bank;
        this.intervalMillis = intervalMillis;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this, "Interest-Scheduler");
        thread.setDaemon(true); // will not stop the program from exiting
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                break; // stop() was called
            }
            int paid = bank.applyInterestToAll();
            System.out.println("\n[Interest Scheduler] One month passed - interest paid to " + paid + " account(s).");
        }
    }
}
