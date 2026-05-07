param(
    [ValidateSet("patch", "minor", "major")]
    [string] $Part = "patch",

    [string] $Remote = "origin",

    [string] $Branch = "",

    [switch] $Continue,

    [switch] $SkipRemoteTagCheck
)

$ErrorActionPreference = "Stop"

function Invoke-Git {
    & git @args
    if ($LASTEXITCODE -ne 0) {
        throw "git $($args -join ' ') failed"
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
    $output = & git tag --list $Tag
    return -not [string]::IsNullOrWhiteSpace(($output -join "").Trim())
}

function Test-RemoteTagExists {
    param([string] $Tag)
    if ($SkipRemoteTagCheck) {
        return $false
    }
    $output = & git ls-remote --tags $Remote $Tag
    if ($LASTEXITCODE -ne 0) {
        throw "Cannot query remote tags from $Remote. Check GitHub credentials and remote URL."
    }
    return -not [string]::IsNullOrWhiteSpace(($output -join "").Trim())
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

$status = @(& git status --porcelain)
if ($status -and -not $Continue) {
    throw "Working tree is not clean. Commit or discard changes before release."
}
if ($Continue) {
    $unexpected = @($status | Where-Object { $_ -notmatch "^\s*M\s+gradle\.properties$" })
    if ($unexpected.Count -gt 0) {
        throw "Continue mode allows only modified gradle.properties. Current changes: $($unexpected -join '; ')"
    }
}

$before = Read-Version
Write-Output "Current version: $($before.Name) ($($before.Code))"

$after = $before
if (-not $Continue) {
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
} else {
    $after = Read-Version
}

if ($null -eq $after -or (Test-LocalTagExists $after.Tag) -or (Test-RemoteTagExists $after.Tag)) {
    throw "Cannot find a free version tag after 20 attempts"
}

Write-Output "New version: $($after.Name) ($($after.Code))"

Invoke-Git add gradle.properties
$staged = @(& git diff --cached --name-only)
if ($staged -contains "gradle.properties") {
    Invoke-Git commit -m "Bump version to $($after.Name)"
} else {
    Write-Output "gradle.properties is already committed."
}

Invoke-Git tag -a $after.Tag -m "WLTest $($after.Tag)"
Invoke-Git push $Remote $Branch
Invoke-Git push $Remote $after.Tag

Write-Output "Pushed $Branch and $($after.Tag)."
Write-Output "GitHub Actions will build APK and create the release with the APK attached."
