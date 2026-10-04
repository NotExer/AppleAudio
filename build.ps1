$ErrorActionPreference = 'Stop'

$lib = Join-Path $PSScriptRoot 'lib'
$out = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $lib, $out | Out-Null

$dependencies = @(
    @{ Name = 'jna-5.16.0.jar'; Url = 'https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.16.0/jna-5.16.0.jar' },
    @{ Name = 'jna-platform-5.16.0.jar'; Url = 'https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/5.16.0/jna-platform-5.16.0.jar' },
    @{ Name = 'flatlaf-3.7.jar'; Url = 'https://repo1.maven.org/maven2/com/formdev/flatlaf/3.7/flatlaf-3.7.jar' }
)
foreach ($dependency in $dependencies) {
    $target = Join-Path $lib $dependency.Name
    if (-not (Test-Path $target)) { Invoke-WebRequest -Uri $dependency.Url -OutFile $target }
}

$classpath = (Get-ChildItem (Join-Path $lib '*.jar') | ForEach-Object FullName) -join ';'
javac -encoding UTF-8 -cp $classpath -d $out (Join-Path $PSScriptRoot 'src\LectorPantalla.java')

$manifest = Join-Path $out 'manifest.mf'
@("Main-Class: LectorPantalla", "Class-Path: lib/jna-5.16.0.jar lib/jna-platform-5.16.0.jar lib/flatlaf-3.7.jar", '') | Set-Content -Encoding ascii $manifest
jar cfm (Join-Path $PSScriptRoot 'LectorPantalla.jar') $manifest -C $out .
Write-Host 'Listo: LectorPantalla.jar'
