[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $projectRoot "src"
$resourceRoot = Join-Path $projectRoot "assets"
$buildRoot = Join-Path $projectRoot "build"
$outputDir = Join-Path $projectRoot "bin"

# Scope the mutex to this checkout so unrelated OpenSpine trees can build concurrently.
$normalizedRoot = [IO.Path]::GetFullPath($projectRoot).ToUpperInvariant()
$hasher = [Security.Cryptography.SHA256]::Create()
try {
    $rootBytes = [Text.Encoding]::UTF8.GetBytes($normalizedRoot)
    $rootHash = [BitConverter]::ToString($hasher.ComputeHash($rootBytes)).Replace("-", "")
} finally {
    $hasher.Dispose()
}

$mutex = [Threading.Mutex]::new($false, "Local\OpenSpine.Build.$rootHash")
$lockTaken = $false
$exitCode = 0
$uniqueSuffix = "$PID-$([Guid]::NewGuid().ToString('N'))"
$temporaryClasses = Join-Path $buildRoot "classes-$uniqueSuffix"
$sourceList = Join-Path $buildRoot "sources-$uniqueSuffix.args"
$backupDir = Join-Path $buildRoot "bin-backup-$uniqueSuffix"

foreach ($managedPath in @($outputDir, $temporaryClasses, $backupDir)) {
    $absolute = [IO.Path]::GetFullPath($managedPath)
    $workspace = [IO.Path]::GetFullPath($projectRoot).TrimEnd('\')
    if (-not $absolute.StartsWith("$workspace\", [StringComparison]::OrdinalIgnoreCase)) {
        throw "Build output path escaped the workspace: $absolute"
    }
}

try {
    try {
        $lockTaken = $mutex.WaitOne(0)
        if (-not $lockTaken) {
            Write-Host "Another OpenSpine build is running; waiting for it to finish..."
            $lockTaken = $mutex.WaitOne()
        }
    } catch [Threading.AbandonedMutexException] {
        # The previous owner exited unexpectedly; ownership is still granted to this process.
        $lockTaken = $true
    }

    if (-not (Get-Command "javac" -ErrorAction SilentlyContinue)) {
        throw "Required JDK tool was not found on PATH: javac"
    }

    New-Item -ItemType Directory -Path $buildRoot -Force | Out-Null
    New-Item -ItemType Directory -Path $temporaryClasses | Out-Null

    $sources = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter "*.java" -File |
        Sort-Object -Property FullName)
    if ($sources.Count -eq 0) {
        throw "No Java sources were found under $sourceRoot."
    }

    $sourceArguments = @($sources | ForEach-Object {
        $forwardSlashPath = $_.FullName.Replace('\', '/')
        '"{0}"' -f $forwardSlashPath.Replace('"', '\"')
    })
    [IO.File]::WriteAllLines(
        $sourceList,
        [string[]] $sourceArguments,
        [Text.UTF8Encoding]::new($false)
    )

    Write-Host "Compiling..."
    $classPath = Join-Path $projectRoot "lib\*"
    & javac -encoding UTF-8 -source 17 -target 17 -cp $classPath `
        -d $temporaryClasses "@$sourceList"
    if ($LASTEXITCODE -ne 0) {
        throw "javac exited with code $LASTEXITCODE."
    }

    if (Test-Path -LiteralPath $resourceRoot) {
        Copy-Item -LiteralPath $resourceRoot -Destination $temporaryClasses -Recurse -Force
    }

    # Stamp the inputs that actually produced these classes, including uncommitted source changes.
    $inputs = @($sources) + @(Get-ChildItem -LiteralPath $resourceRoot -Recurse -File) +
        @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'lib') -Filter '*.jar' -File) +
        @(Get-Item -LiteralPath $PSCommandPath, (Join-Path $PSScriptRoot 'publish.ps1'))
    $fingerprints = @($inputs | Sort-Object FullName | ForEach-Object {
        $stream = [IO.File]::OpenRead($_.FullName)
        $fileHasher = [Security.Cryptography.SHA256]::Create()
        try {
            $fileHash = [BitConverter]::ToString($fileHasher.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
        } finally {
            $fileHasher.Dispose()
            $stream.Dispose()
        }
        [ordered]@{
            path = $_.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
            sha256 = $fileHash
        }
    })
    $inputJson = ConvertTo-Json -InputObject $fingerprints -Compress
    $sourceHasher = [Security.Cryptography.SHA256]::Create()
    try {
        $sourceHash = [BitConverter]::ToString($sourceHasher.ComputeHash(
            [Text.Encoding]::UTF8.GetBytes($inputJson))).Replace('-', '').ToLowerInvariant()
    } finally { $sourceHasher.Dispose() }
    $gitCommit = 'unknown'
    $worktreeState = 'unknown'
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $commit = & git -C $projectRoot rev-parse HEAD 2>$null
        if ($LASTEXITCODE -eq 0) {
            $gitCommit = [string]$commit
            $changes = @(& git -C $projectRoot status --porcelain --untracked-files=normal)
            if ($LASTEXITCODE -eq 0) {
                $worktreeState = if ($changes.Count -gt 0) { 'dirty' } else { 'clean' }
            }
        }
    }
    $builtAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ss.fffZ', [Globalization.CultureInfo]::InvariantCulture)
    [IO.File]::WriteAllLines((Join-Path $temporaryClasses 'openspine-build.properties'), [string[]]@(
        "built_at=$builtAt", "source_sha256=$sourceHash", "git_commit=$gitCommit", "worktree_state=$worktreeState"
    ), [Text.Encoding]::ASCII)

    # Keep the last successful output available until the new tree is ready to install.
    if (Test-Path -LiteralPath $outputDir) {
        Move-Item -LiteralPath $outputDir -Destination $backupDir
    }
    try {
        Move-Item -LiteralPath $temporaryClasses -Destination $outputDir
        $temporaryClasses = $null
    } catch {
        $replaceError = $_
        if ((Test-Path -LiteralPath $backupDir) -and
            -not (Test-Path -LiteralPath $outputDir)) {
            Move-Item -LiteralPath $backupDir -Destination $outputDir
        }
        throw $replaceError
    }

    if (Test-Path -LiteralPath $backupDir) {
        try {
            Remove-Item -LiteralPath $backupDir -Recurse -Force
        } catch {
            Write-Warning "The build succeeded, but the previous output could not be removed: $backupDir"
        }
    }

    Write-Host "BUILD OK"
    Write-Host "Build source SHA-256: $sourceHash ($builtAt)"
} catch {
    Write-Host "BUILD FAILED: $($_.Exception.Message)" -ForegroundColor Red
    $exitCode = 1
} finally {
    if ($sourceList -and (Test-Path -LiteralPath $sourceList)) {
        Remove-Item -LiteralPath $sourceList -Force -ErrorAction SilentlyContinue
    }
    if ($temporaryClasses -and (Test-Path -LiteralPath $temporaryClasses)) {
        Remove-Item -LiteralPath $temporaryClasses -Recurse -Force -ErrorAction SilentlyContinue
    }
    if ($lockTaken) {
        $mutex.ReleaseMutex()
    }
    $mutex.Dispose()
}

exit $exitCode
