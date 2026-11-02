#!/bin/bash
# Compile and start the original CONSOLE app (Linux / macOS).
mkdir -p out
javac -d out $(find src -name "*.java") && java -cp out com.bank.main.BankApp
