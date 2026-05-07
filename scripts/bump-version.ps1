param(
    [ValidateSet("patch", "minor", "major")]
    [string] $Part = "patch"
)

$propertiesPath = Join-Path $PSScriptRoot "..\gradle.properties"
$properties = Get-Content -LiteralPath $propertiesPath

$versionName = ($properties | Where-Object { $_ -match "^VERSION_NAME=" }) -replace "^VERSION_NAME=", ""
$versionCode = [int](($properties | Where-Object { $_ -match "^VERSION_CODE=" }) -replace "^VERSION_CODE=", "")

$parts = $versionName.Split(".")
if ($parts.Count -ne 3) {
    throw "VERSION_NAME must use major.minor.patch format"
}

$major = [int]$parts[0]
$minor = [int]$parts[1]
$patch = [int]$parts[2]

switch ($Part) {
    "major" {
        $major++
        $minor = 0
        $patch = 0
    }
    "minor" {
        $minor++
        $patch = 0
    }
    default {
        $patch++
    }
}

$newVersionName = "$major.$minor.$patch"
$newVersionCode = $versionCode + 1

$updated = $properties |
    ForEach-Object {
        if ($_ -match "^VERSION_NAME=") {
            "VERSION_NAME=$newVersionName"
        } elseif ($_ -match "^VERSION_CODE=") {
            "VERSION_CODE=$newVersionCode"
        } else {
            $_
        }
    }

Set-Content -LiteralPath $propertiesPath -Value $updated
Write-Output "VERSION_NAME=$newVersionName"
Write-Output "VERSION_CODE=$newVersionCode"
