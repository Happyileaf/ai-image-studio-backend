@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Windows Batch 脚本
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0.."

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

echo [ERROR] Maven 不可用：请先安装 Maven 3.9+ 或运行 mvn wrapper:wrapper
exit /b 1
