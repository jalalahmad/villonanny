@echo off

REM If you experience problems running the program, set the 
REM following variable to the full path of the VilloNanny directory, with a final \
set APP_HOME=%~p0

title VilloNanny

REM Do not edit below

cd %APP_HOME%
set CP=%APP_HOME%config;%APP_HOME%classes

REM # Set VilloNanny jars in classpath
for %%i in ("%APP_HOME%dist\*.jar") do call :appendCP %%~si

REM # Set others libraries in classpath
for %%i in ("%APP_HOME%lib\*.jar") do call :appendCP %%~si

REM # Launch the program ...
java -cp "%CP%" -DAPP_HOME="%APP_HOME%\" net.villonanny.VilloNanny -utf8 %*
pause
goto :eof

REM # function to add a jar file to classpath
:appendCP
set CP=%CP%;%1
goto :eof