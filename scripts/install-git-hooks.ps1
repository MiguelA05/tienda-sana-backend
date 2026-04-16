Param()

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

Push-Location $repoRoot
try {
	git config core.hooksPath .githooks
	if ($LASTEXITCODE -ne 0) {
		throw 'No se pudo configurar core.hooksPath.'
	}

	Write-Host 'Hooks de Git activados correctamente.' -ForegroundColor Green
	Write-Host 'Ruta configurada: .githooks' -ForegroundColor Green
	Write-Host 'Hook activo: .githooks/pre-push' -ForegroundColor Green
}
finally {
	Pop-Location
}

