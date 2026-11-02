@echo off
rem Compile and run (Windows)
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 goto end
java -cp out com.bank.main.BankApp
:end
del sources.txt
