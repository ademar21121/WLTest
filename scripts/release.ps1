param(
    [ValidateSet("patch", "minor", "major")]
    [string] $Part = "patch",

    [string] $Remote = "origin",

    [string] $Branch = ""
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Arguments)

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed"
    }
}

function Read-Version {
    $propertiesPath = Join-Path $PSScriptRoot "..\gradle.properties"
    $properties = Get-Content -LiteralPath $propertiesPath
    $versionName = ($properties | Where-Object { $_ -match "^VERSION_NAME=" }) -replace "^VERSION_NAME=", ""
    $versionCode = [int](($properties | Where-Object { $_ -match "^VERSION_CODE=" }) -replace "^VERSION_CODE=", "")

    [pscustomobject]@{
        Name = $versionName
        Code = $versionCode
        Tag = "v$versionName"
    }
}

function Test-LocalTagExists {
    param([string] $Tag)
    return -not [string]::IsNullOrWhiteSpace((& git tag --list $Tag).Trim())
}

function Test-RemoteTagExists {
    param([string] $Tag)
    return -not [string]::IsNullOrWhiteSpace((& git ls-remote --tags $Remote $Tag))
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $repoRoot

Invoke-Git rev-parse --is-inside-work-tree | Out-Null

if ([string]::IsNullOrWhiteSpace($Branch)) {
    $Branch = (& git branch --show-current).Trim()
}
if ([string]::IsNullOrWhiteSpace($Branch)) {
    throw "Cannot determine current branch"
}

$status = (& git status --porcelain)
if ($status) {
    throw "Working tree is not clean. Commit or discard changes before release."
}

$before = Read-Version
Write-Output "Current version: $($before.Name) ($($before.Code))"

$after = $null
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    & (Join-Path $PSScriptRoot "bump-version.ps1") $Part
    if ($LASTEXITCODE -ne 0) {
        throw "Version bump failed"
    }

    $after = Read-Version
    if ((Test-LocalTagExists $after.Tag) -or (Test-RemoteTagExists $after.Tag)) {
        Write-Output "Tag $($after.Tag) already exists, bumping again."
        continue
    }

    break
}

if ($null -eq $after -or (Test-LocalTagExists $after.Tag) -or (Test-RemoteTagExists $after.Tag)) {
    throw "Cannot find a free version tag after 20 attempts"
}

Write-Output "New version: $($after.Name) ($($after.Code))"

Invoke-Git add gradle.properties
Invoke-Git commit -m "Bump version to $($after.Name)"
Invoke-Git tag -a $after.Tag -m "WLTest $($after.Tag)"
Invoke-Git push $Remote $Branch
Invoke-Git push $Remote $after.Tag

Write-Output "Pushed $Branch and $($after.Tag)."
Write-Output "GitHub Actions will build APK and create the release with the APK attached."
