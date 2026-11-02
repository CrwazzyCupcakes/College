#!/bin/bash
# Compile and start the WEB app (Linux / macOS). Run from this folder.
mkdir -p out
javac -d out $(find src -name "*.java") && java -cp out com.bank.web.WebServerApp "$@"
