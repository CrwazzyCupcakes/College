package com.bank.web;

import com.bank.exceptions.AccountLockedException;
import com.bank.exceptions.AccountNotFoundException;
import com.bank.exceptions.BankException;
import com.bank.exceptions.InvalidPinException;
import com.bank.model.Account;
import com.bank.model.AccountType;
import com.bank.model.Transaction;
import com.bank.service.BankService;
import com.bank.service.ConcurrencyDemo;
import com.bank.service.InterestScheduler;
import com.bank.util.FileUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles every /api/... request. It never contains banking rules itself:
 * it reads the request, calls BankService / Account, and writes the result as JSON.
 */
public class ApiHandler implements HttpHandler {
    private static final String TOKEN_HEADER = "X-Auth-Token";

    private final BankService bank;
    private final SessionManager sessions;
    private final InterestScheduler scheduler;

    public ApiHandler(BankService bank, SessionManager sessions, InterestScheduler scheduler) {
        this.bank = bank;
        this.sessions = sessions;
        this.scheduler = scheduler;
    }

    // =============== entry point: one call per HTTP request (on a pool thread) ===============

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            Map<String, String> params = readParams(ex);
            route(ex, ex.getRequestMethod(), ex.getRequestURI().getPath(), params);
        } catch (ApiException e) {
            sendJson(ex, e.getStatus(), Json.obj("error", e.getMessage()));
        } catch (InvalidPinException e) {
            sendJson(ex, 401, Json.obj("error", e.getMessage()));
        } catch (AccountLockedException e) {
            sendJson(ex, 403, Json.obj("error", e.getMessage()));
        } catch (AccountNotFoundException e) {
            sendJson(ex, 404, Json.obj("error", e.getMessage()));
        } catch (BankException e) { // InsufficientFunds, InvalidAmount, ...
            sendJson(ex, 400, Json.obj("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, Json.obj("error", "Something went wrong on the server."));
        } finally {
            ex.close();
        }
    }

    // =============== routing ===============

    private void route(HttpExchange ex, String method, String path, Map<String, String> p)
            throws Exception {
        switch (path) {
            // ---------- public ----------
            case "/api/register": {
                requireMethod(method, "POST");
                AccountType type = parseType(p.get("type"));
                Account account = bank.createAccount(type, p.get("name"), p.get("pin"), number(p, "deposit"));
                bank.save();
                sendJson(ex, 201, Json.obj("accountNumber", account.getAccountNumber(),
                        "message", "Account created. Your account number is " + account.getAccountNumber()));
                return;
            }
            case "/api/login": {
                requireMethod(method, "POST");
                Account account;
                try {
                    account = bank.login(need(p, "accountNumber"), need(p, "pin"));
                } finally {
                    bank.save(); // wrong-PIN counters and locks must survive a restart
                }
                String token = sessions.create(SessionManager.ROLE_CUSTOMER, account.getAccountNumber());
                Map<String, Object> body = accountInfo(account);
                body.put("token", token);
                sendJson(ex, 200, body);
                return;
            }
            case "/api/admin/login": {
                requireMethod(method, "POST");
                if (!bank.verifyAdmin(need(p, "password"))) {
                    throw new ApiException(401, "Wrong admin password.");
                }
                sendJson(ex, 200, Json.obj("token", sessions.create(SessionManager.ROLE_ADMIN, null)));
                return;
            }
            case "/api/logout": {
                requireMethod(method, "POST");
                sessions.remove(ex.getRequestHeaders().getFirst(TOKEN_HEADER));
                sendJson(ex, 200, Json.obj("ok", true));
                return;
            }
            default:
                break;
        }

        // ---------- everything below needs a logged-in session ----------
        if (path.startsWith("/api/admin/")) {
            requireAdmin(ex);
            adminRoute(ex, method, path, p);
        } else {
            customerRoute(ex, method, path, p);
        }
    }

    private void customerRoute(HttpExchange ex, String method, String path, Map<String, String> p)
            throws Exception {
        Account account = currentCustomer(ex);
        switch (path) {
            case "/api/account":
                requireMethod(method, "GET");
                sendJson(ex, 200, accountInfo(account));
                return;
            case "/api/deposit": {
                requireMethod(method, "POST");
                account.deposit(number(p, "amount"));
                bank.save();
                sendJson(ex, 200, accountInfo(account));
                return;
            }
            case "/api/withdraw": {
                requireMethod(method, "POST");
                account.withdraw(number(p, "amount"));
                bank.save();
                sendJson(ex, 200, accountInfo(account));
                return;
            }
            case "/api/transfer": {
                requireMethod(method, "POST");
                bank.transfer(account.getAccountNumber(), need(p, "to"), number(p, "amount"));
                bank.save();
                sendJson(ex, 200, accountInfo(account));
                return;
            }
            case "/api/transactions": {
                requireMethod(method, "GET");
                sendJson(ex, 200, transactionList(account, p.get("sort")));
                return;
            }
            case "/api/change-pin": {
                requireMethod(method, "POST");
                account.changePin(need(p, "oldPin"), need(p, "newPin"));
                bank.save();
                sendJson(ex, 200, Json.obj("message", "PIN changed successfully."));
                return;
            }
            case "/api/statement.csv": {
                requireMethod(method, "GET");
                byte[] csv = FileUtil.buildCsv(account).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
                ex.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"" + account.getAccountNumber() + "_statement.csv\"");
                ex.sendResponseHeaders(200, csv.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(csv);
                }
                return;
            }
            default:
                throw new ApiException(404, "Unknown endpoint: " + path);
        }
    }

    private void adminRoute(HttpExchange ex, String method, String path, Map<String, String> p)
            throws Exception {
        switch (path) {
            case "/api/admin/summary": {
                requireMethod(method, "GET");
                Optional<Account> richest = bank.getRichestAccount();
                sendJson(ex, 200, Json.obj(
                        "totalAccounts", bank.getAccountCount(),
                        "totalHoldings", bank.getTotalHoldings(),
                        "byType", bank.countByType(),
                        "richest", richest.isPresent() ? accountInfo(richest.get()) : null,
                        "schedulerRunning", scheduler.isRunning()));
                return;
            }
            case "/api/admin/accounts": {
                requireMethod(method, "GET");
                List<Account> list = (p.get("above") != null && !p.get("above").isEmpty())
                        ? bank.getAccountsAbove(number(p, "above"))
                        : bank.getAllAccounts();
                sendJson(ex, 200, list.stream().map(this::accountInfo).collect(Collectors.toList()));
                return;
            }
            case "/api/admin/lock": {
                requireMethod(method, "POST");
                boolean lock = Boolean.parseBoolean(need(p, "lock"));
                bank.setLocked(need(p, "accountNumber"), lock);
                bank.save();
                sendJson(ex, 200, Json.obj("message", lock ? "Account locked." : "Account unlocked."));
                return;
            }
            case "/api/admin/interest": {
                requireMethod(method, "POST");
                int paid = bank.applyInterestToAll();
                sendJson(ex, 200, Json.obj("paid", paid,
                        "message", "One month of interest paid to " + paid + " savings account(s)."));
                return;
            }
            case "/api/admin/scheduler": {
                requireMethod(method, "POST");
                if ("start".equals(need(p, "action"))) {
                    scheduler.start();
                } else {
                    scheduler.stop();
                }
                sendJson(ex, 200, Json.obj("schedulerRunning", scheduler.isRunning()));
                return;
            }
            case "/api/admin/backup": {
                requireMethod(method, "POST");
                bank.save();
                Path backup = FileUtil.backup();
                sendJson(ex, 200, Json.obj("message", "Backup created: " + backup.getFileName()));
                return;
            }
            case "/api/admin/demo": {
                requireMethod(method, "POST");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                PrintStream capture = new PrintStream(buffer, true, "UTF-8");
                ConcurrencyDemo.run(capture); // same demo as the console version, output captured
                sendJson(ex, 200, Json.obj("output", buffer.toString("UTF-8")));
                return;
            }
            default:
                throw new ApiException(404, "Unknown endpoint: " + path);
        }
    }

    // =============== authentication helpers ===============

    private SessionManager.Session currentSession(HttpExchange ex) {
        SessionManager.Session s = sessions.get(ex.getRequestHeaders().getFirst(TOKEN_HEADER));
        if (s == null) {
            throw new ApiException(401, "Session expired. Please log in again.");
        }
        return s;
    }

    private Account currentCustomer(HttpExchange ex) throws AccountNotFoundException {
        SessionManager.Session s = currentSession(ex);
        if (!SessionManager.ROLE_CUSTOMER.equals(s.getRole())) {
            throw new ApiException(403, "This action is for customers only.");
        }
        return bank.findAccount(s.getAccountNumber());
    }

    private void requireAdmin(HttpExchange ex) {
        if (!SessionManager.ROLE_ADMIN.equals(currentSession(ex).getRole())) {
            throw new ApiException(403, "Admin access required.");
        }
    }

    // =============== JSON shaping ===============

    private Map<String, Object> accountInfo(Account a) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("accountNumber", a.getAccountNumber());
        m.put("holderName", a.getHolderName());
        m.put("type", a.getAccountType());
        m.put("balance", a.getBalance());
        m.put("withdrawable", a.getWithdrawableAmount());
        m.put("locked", a.isLocked());
        return m;
    }

    private List<Map<String, Object>> transactionList(Account account, String sort) {
        Comparator<Transaction> byTime = Comparator.comparing(Transaction::getTimestamp)
                .thenComparingInt(Transaction::getId);
        Comparator<Transaction> order;
        if ("oldest".equals(sort)) {
            order = byTime;
        } else if ("amount".equals(sort)) {
            order = Comparator.comparingDouble(Transaction::getAmount).reversed();
        } else {
            order = byTime.reversed(); // default: newest first
        }
        List<Transaction> sorted = new ArrayList<Transaction>(account.getTransactions());
        sorted.sort(order);

        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        for (Transaction t : sorted) {
            out.add(Json.obj("id", t.getId(), "time", t.getFormattedTime(), "type", t.getType().name(),
                    "amount", t.getAmount(), "balanceAfter", t.getBalanceAfter(),
                    "description", t.getDescription()));
        }
        return out;
    }

    // =============== request parsing ===============

    private void requireMethod(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw new ApiException(405, "Use " + expected + " for this endpoint.");
        }
    }

    private String need(Map<String, String> p, String key) {
        String value = p.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new ApiException(400, "Missing field: " + key);
        }
        return value.trim();
    }

    private double number(Map<String, String> p, String key) {
        try {
            return Double.parseDouble(need(p, key));
        } catch (NumberFormatException e) {
            throw new ApiException(400, "'" + key + "' must be a number.");
        }
    }

    private AccountType parseType(String text) {
        if ("SAVINGS".equalsIgnoreCase(text)) return AccountType.SAVINGS;
        if ("CURRENT".equalsIgnoreCase(text)) return AccountType.CURRENT;
        throw new ApiException(400, "Account type must be SAVINGS or CURRENT.");
    }

    /** Reads query-string parameters plus a form-encoded body (key=value&key2=value2). */
    private Map<String, String> readParams(HttpExchange ex) throws IOException {
        Map<String, String> params = new LinkedHashMap<String, String>();
        parseForm(ex.getRequestURI().getRawQuery(), params);
        parseForm(readBody(ex.getRequestBody()), params);
        return params;
    }

    private String readBody(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
            if (buffer.size() > 16 * 1024) {
                throw new ApiException(413, "Request too large.");
            }
        }
        return buffer.toString("UTF-8");
    }

    private void parseForm(String text, Map<String, String> into) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            into.put(decode(key), decode(value));
        }
    }

    private String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always available
        }
    }

    // =============== response writing ===============

    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = Json.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
