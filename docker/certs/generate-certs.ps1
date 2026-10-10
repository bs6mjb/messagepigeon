# Generates the test certificates for the Docker queue manager and the client key/trust stores for MessagePigeon.
# Needs Docker (openssl runs in a container) and keytool from a JDK (found via -KeytoolDir, JAVA_HOME or the PATH).
# Safe to re-run: it replaces everything.
#
# Output (all in this folder, all git-ignored):
#   server\tls.key, server\tls.crt   queue manager certificate (CN=localhost)
#   server-trust\client.crt          client certificate the queue manager trusts
#   truststore.p12 / truststore.jks  contains the server certificate  -> MessagePigeon trust store
#   client.p12 / client.jks          client key + certificate         -> MessagePigeon key store (mutual TLS)
#   store-password.txt               the password for all four stores
param(
    [string]$KeytoolDir = ''
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if ($KeytoolDir) { $keytool = Join-Path $KeytoolDir 'keytool.exe' }
elseif ($env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe' }
else { $keytool = (Get-Command keytool -ErrorAction Stop).Source }

$pw = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
Set-Content -Path store-password.txt -Value $pw -NoNewline

New-Item -ItemType Directory -Force server, server-trust | Out-Null
foreach ($f in 'server\tls.key','server\tls.crt','server-trust\client.crt','client.key','client.crt',
                'truststore.p12','truststore.jks','client.p12','client.jks') {
    if (Test-Path $f) { Remove-Item $f -Force }
}

function Invoke-OpenSsl {
    docker run --rm -v "${PSScriptRoot}:/work" -w /work alpine/openssl @args
    if ($LASTEXITCODE -ne 0) { throw "openssl failed: $args" }
}

# Server certificate: SAN covers localhost and 127.0.0.1 so hostname checks pass.
Invoke-OpenSsl req -x509 -newkey rsa:2048 -nodes -days 825 -keyout server/tls.key -out server/tls.crt `
    -subj '/CN=localhost' -addext 'subjectAltName=DNS:localhost,DNS:pigeon-test,IP:127.0.0.1'

# Client certificate for mutual TLS.
Invoke-OpenSsl req -x509 -newkey rsa:2048 -nodes -days 825 -keyout client.key -out client.crt `
    -subj '/CN=messagepigeon'
Invoke-OpenSsl pkcs12 -export -in client.crt -inkey client.key -name pigeon-client -out client.p12 -passout "pass:$pw"
Copy-Item client.crt server-trust\client.crt

# openssl ran as root; the MQ container runs as a non-root user and must be able to read the server key.
docker run --rm -v "${PSScriptRoot}:/work" --entrypoint sh alpine/openssl -c 'chmod -R a+rX /work/server /work/server-trust'

# Stores for MessagePigeon, in both PKCS12 and JKS.
& $keytool -importcert -noprompt -alias pigeon-server -file server\tls.crt -keystore truststore.p12 -storetype PKCS12 -storepass $pw
& $keytool -importcert -noprompt -alias pigeon-server -file server\tls.crt -keystore truststore.jks -storetype JKS -storepass $pw
& $keytool -importkeystore -noprompt -srckeystore client.p12 -srcstoretype PKCS12 -srcstorepass $pw `
    -destkeystore client.jks -deststoretype JKS -deststorepass $pw -destkeypass $pw

Remove-Item client.key, client.crt -Force
Write-Host "Done. Store password is in $PSScriptRoot\store-password.txt"
