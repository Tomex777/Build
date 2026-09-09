$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Out = Join-Path $Root "app\src\main\assets\models\maia3-5m.fp16.onnx"
$Url = "https://huggingface.co/bqrio/maia3-onnx/resolve/main/maia3-5m.fp16.onnx?download=true"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Out) | Out-Null
Invoke-WebRequest -Uri $Url -OutFile $Out
$Hash = (Get-FileHash -Algorithm SHA256 $Out).Hash.ToLower()
Write-Host "SHA-256: $Hash"
if ($Hash -ne "ca22fc3031975932e693f9758149302efc177749165443ed52de828add8864fa") {
    throw "Model checksum mismatch."
}
Write-Host "Maia-3 model installed at $Out"
