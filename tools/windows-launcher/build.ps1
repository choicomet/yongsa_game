$ErrorActionPreference = 'Stop'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework\v4.0.30319\csc.exe'
$target = Join-Path $PSScriptRoot '..\..\용사가 다한다.exe'
& $compiler /nologo /target:winexe /platform:anycpu /optimize+ /codepage:65001 "/out:$target" "/win32icon:$PSScriptRoot\hero.ico" /reference:System.Windows.Forms.dll /reference:System.Drawing.dll "$PSScriptRoot\Launcher.cs"
exit $LASTEXITCODE
