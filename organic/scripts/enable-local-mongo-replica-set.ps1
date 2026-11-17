# Run in an elevated PowerShell with the application stopped.
$ErrorActionPreference = 'Stop'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Open PowerShell as Administrator and run this script again.'
}

$service = Get-CimInstance Win32_Service -Filter "Name='MongoDB'"
if (-not $service -or $service.PathName -notmatch '--config\s+"([^"]+)"') {
    throw 'Cannot locate the MongoDB Windows service configuration.'
}
$config = $Matches[1]
$original = [IO.File]::ReadAllText($config)
if ($original -notmatch '(?m)^\s*bindIp:\s*127\.0\.0\.1\s*$' -or
    $original -notmatch '(?m)^\s*port:\s*27017\s*$' -or
    $original -match '(?m)^\s*authorization:\s*enabled\b') {
    throw 'Expected a local MongoDB service on 127.0.0.1:27017 without authorization.'
}
$hasReplication = $original -match '(?m)^replication\s*:'
if ($hasReplication -and $original -notmatch '(?m)^\s+replSetName:\s*rs0\s*$') {
    throw 'The service already has a different replication configuration.'
}

$project = Split-Path $PSScriptRoot -Parent
$scratch = Join-Path ([IO.Path]::GetTempPath()) ('organic-mongo-' + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $scratch | Out-Null
$classpathFile = Join-Path $scratch 'classpath.txt'
$source = Join-Path $scratch 'InitializeLocalReplicaSet.java'
try {
    Push-Location $project
    try {
        & .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=$classpathFile"
        if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the MongoDB Java driver.' }
    } finally {
        Pop-Location
    }

    @"
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClients;
import java.util.List;
import org.bson.Document;

class InitializeLocalReplicaSet {
    public static void main(String[] args) throws Exception {
        String uri = "mongodb://localhost:27017/?directConnection=true&serverSelectionTimeoutMS=5000";
        try (var client = MongoClients.create(uri)) {
            var admin = client.getDatabase("admin");
            try {
                var status = admin.runCommand(new Document("replSetGetStatus", 1));
                if (!"rs0".equals(status.getString("set"))) throw new IllegalStateException("Unexpected replica set.");
            } catch (MongoCommandException error) {
                if (error.getErrorCode() != 94) throw error;
                admin.runCommand(new Document("replSetInitiate", new Document("_id", "rs0")
                        .append("members", List.of(new Document("_id", 0).append("host", "localhost:27017")))));
            }
            for (int attempt = 0; attempt < 30; attempt++) {
                var hello = admin.runCommand(new Document("hello", 1));
                if ("rs0".equals(hello.getString("setName")) && Boolean.TRUE.equals(hello.getBoolean("isWritablePrimary"))) {
                    System.out.println("MongoDB rs0 is PRIMARY.");
                    return;
                }
                Thread.sleep(1000);
            }
            throw new IllegalStateException("Replica set did not elect a primary.");
        }
    }
}
"@ | Set-Content -Encoding Ascii -LiteralPath $source

    if (-not $hasReplication) {
        $backup = "$config.before-rs0.$(Get-Date -Format yyyyMMdd-HHmmss).bak"
        Copy-Item -LiteralPath $config -Destination $backup
        Write-Host "Configuration backup: $backup"
        Stop-Service -Name MongoDB
        try {
            $updated = $original.TrimEnd() + [Environment]::NewLine + [Environment]::NewLine +
                "replication:" + [Environment]::NewLine + "  replSetName: rs0" + [Environment]::NewLine
            [IO.File]::WriteAllText($config, $updated)
            Start-Service -Name MongoDB
        } catch {
            Copy-Item -LiteralPath $backup -Destination $config -Force
            Start-Service -Name MongoDB
            throw
        }
    } else {
        Start-Service -Name MongoDB
    }

    $classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    & java -cp $classpath $source
    if ($LASTEXITCODE -ne 0) { throw 'Replica set initialization failed. Check the MongoDB service log.' }
} finally {
    foreach ($file in @($classpathFile, $source)) {
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    if (Test-Path -LiteralPath $scratch) { Remove-Item -LiteralPath $scratch }
}
