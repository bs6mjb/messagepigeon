# Creates docker\.env with random passwords for the test queue manager's users. Does nothing if .env exists.
Set-Location $PSScriptRoot
if (Test-Path .env) { Write-Host '.env already exists; leaving it alone.'; return }
function New-Pw { -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 20 | ForEach-Object { [char]$_ }) }
Set-Content .env -Value @("MQ_APP_PASSWORD=$(New-Pw)", "MQ_ADMIN_PASSWORD=$(New-Pw)")
Write-Host 'Created .env'
