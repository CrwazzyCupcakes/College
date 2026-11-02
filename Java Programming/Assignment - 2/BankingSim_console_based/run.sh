#!/bin/bash
# Compile and run (Linux / macOS)
mkdir -p out
javac -d out $(find src -name "*.java") && java -cp out com.bank.main.BankApp
