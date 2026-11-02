# Java Bank - Web App

The banking simulator with a browser front-end. The Java core (accounts, threads,
exceptions, serialization) is the same as the console project; a thin web layer sits on top.
No frameworks and no libraries: only the JDK and plain HTML/CSS/JavaScript.

## Run
Needs JDK 8 or newer.

- Linux / macOS: `./run.sh`
- Windows: `run.bat`
- Then open **http://localhost:8080**
- Different port: `./run.sh 9000`
- Stop with Ctrl+C (data is saved on exit)

Always start it from this folder, because the server looks for `web/` and writes `data/` here.
Admin password: `admin123` (in `BankService.java`).
The original console version still works: `./run-console.sh` / `run-console.bat`.

## How it fits together
```
Browser (web/index.html, style.css, app.js)
   |  fetch()  -> /api/deposit, /api/login ...   (form-encoded request, JSON response)
   v
com.bank.web.WebServerApp   JDK HttpServer + thread pool (8 threads = 8 requests at once)
   |-- StaticFileHandler    serves the web/ files (NIO Path + Files, blocks ../ tricks)
   |-- ApiHandler           reads request -> calls BankService / Account -> writes JSON
   |-- SessionManager       login token <-> account (ConcurrentHashMap, 30 min idle timeout)
   |-- Json                 tiny hand-written JSON writer
   v
com.bank.service / model / exceptions / util     <- unchanged core from the console project
```
Login returns a random token. The browser keeps it in `sessionStorage` and sends it in the
`X-Auth-Token` header on every request. A customer token can only touch its own account;
admin endpoints need an admin token.

## API
| Method | Path | Needs | What it does |
|--------|------|-------|--------------|
| POST | /api/register | - | type, name, pin, deposit -> new account |
| POST | /api/login | - | accountNumber, pin -> token + account |
| POST | /api/logout | token | ends the session |
| GET  | /api/account | customer | balance, type, withdrawable, locked |
| POST | /api/deposit, /api/withdraw | customer | amount |
| POST | /api/transfer | customer | to, amount |
| GET  | /api/transactions?sort=newest\|oldest\|amount | customer | history (sorted with Comparators) |
| POST | /api/change-pin | customer | oldPin, newPin |
| GET  | /api/statement.csv | customer | CSV download |
| POST | /api/admin/login | - | password -> admin token |
| GET  | /api/admin/summary | admin | totals, counts, richest, scheduler state |
| GET  | /api/admin/accounts?above=N | admin | all accounts, optional balance filter (streams) |
| POST | /api/admin/lock | admin | accountNumber, lock=true/false |
| POST | /api/admin/interest | admin | pay one month of interest now |
| POST | /api/admin/scheduler | admin | action=start/stop (background thread) |
| POST | /api/admin/backup | admin | copies data/bank.dat to data/backups/ |
| POST | /api/admin/demo | admin | runs the synchronized vs unsynchronized thread demo |

Errors come back as `{"error": "..."}` with a fitting status: 400 bad input / insufficient funds,
401 wrong PIN or expired session, 403 locked account or wrong role, 404 unknown account.
Your custom exceptions map straight onto these in `ApiHandler.handle()`.

## Files
```
src/com/bank/web/    WebServerApp, ApiHandler, StaticFileHandler, SessionManager, Json, ApiException
src/com/bank/...     model, service, util, exceptions, main (console)  - the Java core
web/                 index.html, style.css, app.js
data/                created at runtime (bank.dat, backups/, exports/)
```
Small changes made to the core for the web: `ConcurrencyDemo.run(PrintStream)` (so the demo output
can be sent to the browser) and `FileUtil.buildCsv()` (so the CSV can be downloaded).

## Syllabus map
| Unit | Where |
|------|-------|
| I   | Classes, constructors, static/final, strings, StringBuilder, arrays |
| II  | Inheritance + abstract `Account`, interfaces, packages, anonymous class (shutdown hook) |
| III | Custom exceptions, `synchronized`, threads (server pool, interest scheduler, demo), generics `Repository<T>` |
| IV  | HashMap / ArrayList / ConcurrentHashMap, Comparator, lambdas, streams, method references |
| V   | Serialization, `BufferedWriter`, NIO `Path` / `Files` (save, backup, static file serving), streams |

## Viva talking points
- Why is it safe when two browser tabs spend from the same account? `Account.debit()` is `synchronized`.
- Why two nested `synchronized` blocks in `BankService.transfer()`? Fixed lock order prevents deadlock.
- Why `ConcurrentHashMap` in `SessionManager`? Many request threads read/write sessions at once.
- Why escape text in `app.js`? Names come from users; without `esc()` a name like `<script>` would run (XSS).

## Honest limitations (say these before they ask)
- Plain HTTP, bound to localhost only. A real bank needs HTTPS.
- PINs are stored in plain text, money is `double`, and there is no CSRF protection.
- Sessions live in memory, so restarting the server logs everyone out (account data is kept).
