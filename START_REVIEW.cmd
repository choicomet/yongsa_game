@echo off
setlocal
set "review_file=%~dp0index.html"
if not exist "%review_file%" (
  echo Missing index.html. Extract the full review package first.
  pause
  exit /b 1
)
set "review_edge=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not exist "%review_edge%" set "review_edge=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"
if not exist "%review_edge%" (
  echo Microsoft Edge was not found. Open index.html with your web browser.
  pause
  exit /b 1
)
start "" "%review_edge%" --app="file:///%review_file:\=/%"
