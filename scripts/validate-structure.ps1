$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$requiredFiles = @(
  "pom.xml",
  "orbit-gateway/pom.xml",
  "orbit-auth/src/main/java/com/orbitcrm/auth/service/LoginService.java",
  "orbit-common/common-security/src/main/java/com/orbitcrm/common/security/JwtAuthenticationFilter.java",
  "orbit-common/common-log/src/main/java/com/orbitcrm/common/log/OperationLogAspect.java",
  "orbit-tenant/src/main/java/com/orbitcrm/tenant/service/TenantProvisioningService.java",
  "orbit-tenant/src/main/java/com/orbitcrm/tenant/service/TenantDomainService.java",
  "orbit-common/common-datasource/src/main/java/com/orbitcrm/common/datasource/PlatformTenantDomainResolver.java",
  "orbit-billing/src/main/java/com/orbitcrm/billing/service/BillingService.java",
  "orbit-crm-service/src/main/java/com/orbitcrm/crm/service/CustomerService.java",
  "orbit-crm-service/src/main/java/com/orbitcrm/crm/service/ContactService.java",
  "orbit-crm-service/src/main/java/com/orbitcrm/crm/service/FollowRecordService.java",
  "orbit-crm-service/src/main/java/com/orbitcrm/crm/controller/ContactController.java",
  "orbit-crm-service/src/main/java/com/orbitcrm/crm/controller/FollowRecordController.java",
  "orbit-report/src/main/java/com/orbitcrm/report/service/DashboardService.java",
  "orbit-file/src/main/java/com/orbitcrm/file/service/FileService.java",
  "orbit-file/src/main/java/com/orbitcrm/file/controller/FileController.java",
  "orbit-message/src/main/java/com/orbitcrm/message/service/NoticeService.java",
  "orbit-message/src/main/java/com/orbitcrm/message/controller/NoticeController.java",
  "orbit-openapi/src/main/java/com/orbitcrm/openapi/service/OpenApiKeyService.java",
  "orbit-openapi/src/main/java/com/orbitcrm/openapi/service/OpenApiLeadService.java",
  "orbit-openapi/src/main/java/com/orbitcrm/openapi/controller/OpenApiKeyController.java",
  "orbit-openapi/src/main/java/com/orbitcrm/openapi/controller/ExternalLeadController.java",
  "orbit-task/src/main/java/com/orbitcrm/task/service/TaskReminderService.java",
  "orbit-task/src/main/java/com/orbitcrm/task/job/TaskReminderJob.java",
  "orbit-admin/src/main/java/com/orbitcrm/admin/config/PlatformAdminApiKeyFilter.java",
  "orbit-admin/src/main/java/com/orbitcrm/admin/service/PlatformAdminService.java",
  "orbit-admin/src/main/java/com/orbitcrm/admin/controller/PlatformDashboardController.java",
  "orbit-system/src/main/java/com/orbitcrm/system/service/OperationLogService.java",
  "database/platform/001_platform_schema.sql",
  "database/tenant-template/001_tenant_schema.sql",
  "deploy/docker/docker-compose.yml"
)

foreach ($file in $requiredFiles) {
  if (!(Test-Path $file)) {
    throw "Missing required file: $file"
  }
}

Get-ChildItem -Recurse -Filter pom.xml | ForEach-Object {
  [xml](Get-Content -Raw $_.FullName) | Out-Null
}

$java8Forbidden = "readAllBytes|List\.of|Map\.of|Set\.of|\bvar\s+"
$matches = Get-ChildItem -Recurse -Include *.java -File |
  Select-String -Pattern $java8Forbidden
if ($matches) {
  $matches | ForEach-Object {
    Write-Host ("{0}:{1} {2}" -f $_.Path, $_.LineNumber, $_.Line)
  }
  throw "Found Java APIs or syntax that are not compatible with Java 8"
}

Write-Host "GJ OrbitCRM structure validation passed."
