# Java Banking Simulator (console)

## Run
Needs JDK 8 or newer.

- Linux / macOS: `./run.sh`
- Windows: `run.bat`
- Manual: `mkdir out` then `javac -d out $(find src -name "*.java")` then `java -cp out com.bank.main.BankApp`

Run it from the project folder: saved data goes into `data/` there.
Admin password: `admin123` (defined in `BankService`).

## Project layout
```
src/com/bank/
  exceptions/  BankException (base), InsufficientFunds, InvalidAmount, InvalidPin,
               AccountLocked, AccountNotFound
  model/       Account (abstract), SavingsAccount, CurrentAccount, Transaction,
               TransactionType, AccountType, Transactable + Reportable (interfaces), BankData
  util/        Repository<T> (generic), FileUtil (serialization, NIO, CSV), InputHelper (Scanner)
  service/     BankService, InterestScheduler (thread), ConcurrencyDemo
  main/        BankApp (menus, entry point)
data/          created at runtime: bank.dat, backups/, exports/
```

## Features
- Create Savings / Current accounts (4-digit PIN, opening deposit rules)
- Login with PIN; 3 wrong attempts lock the account
- Deposit, withdraw, transfer, balance, mini statement, sortable full statement
- Export statement to CSV, change PIN
- Admin: list accounts, filter by balance, bank summary, lock/unlock, backup,
  interest scheduler (background thread), thread-synchronization demo
- All data saved automatically (serialization) and reloaded on start

## Syllabus map (for the viva)
| Unit | Where it shows up |
|------|-------------------|
| I   | Classes, constructors + overloading (`CurrentAccount`), static/final constants, Scanner (`InputHelper`), String.format, arrays/collections |
| II  | Inheritance (`Account` -> `Savings`/`Current`), abstract class, method overriding + dynamic dispatch, interfaces (`Transactable`, `Reportable`), packages, enums |
| III | Custom exceptions, try/catch/finally, throw/throws, multithreading (`InterestScheduler`, `ConcurrencyDemo`), `synchronized`, generics (`Repository<T>`) |
| IV  | `HashMap`, `ArrayList`, `Comparator`, lambdas, method references, streams (`BankService` reports) |
| V   | Serialization (`bank.dat`), `BufferedWriter` CSV export, NIO `Path` / `Files` for save, backup, export |

## Good things to point out
- `Account.debit()` is `synchronized`, so 10 ATMs can't overdraw an account (Admin -> option 7 shows it live).
- `BankService.transfer()` locks both accounts in a fixed order to avoid deadlock.
- `Account.writeObject()` locks the account while it is saved, so a half-updated balance is never written.

## Known simplifications
- Money uses `double` for readability; real banks use `BigDecimal`.
- PINs are stored as plain text; real systems store a salted hash.
