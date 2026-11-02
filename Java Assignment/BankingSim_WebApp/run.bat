@echo off
rem Compile and start the WEB app (Windows). Run from this folder.
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -d out @sources.txt
if errorlevel 1 goto end
java -cp out com.bank.web.WebServerApp %*
:end
del sources.txt
