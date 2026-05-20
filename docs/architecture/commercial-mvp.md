# GJ OrbitCRM Commercial MVP

This repository is scaffolded for GJ OrbitCRM, a commercial-style SaaS CRM demo, not a single-tenant toy project.

## First Six Blocks

1. Tenant registration and provisioning: `orbit-tenant`
2. Platform database contract: `database/platform/001_platform_schema.sql`
3. Tenant template database contract: `database/tenant-template/001_tenant_schema.sql`
4. Authentication and JWT issuing: `orbit-auth`
5. Plan and subscription foundation: `orbit-billing`
6. Tenant-aware GJ OrbitCRM API surface: `orbit-crm-service`

## Request Tenant Resolution

Tenant resolution is centralized in `common-web`:

1. Custom domain lookup from `platform_tenant_domain`
2. Subdomain under `orbit.tenant.root-domain`
3. `X-Tenant-Code` request header
4. Login account fallback where the auth flow explicitly provides tenant code

## Database Model

The platform database stores tenants, domains, database connection records, plans, subscriptions, orders, payments, and platform operation logs.

Each tenant database is created from `database/tenant-template/001_tenant_schema.sql` and stores RBAC, CRM business data, tasks, and operation logs.

## Implemented Surface

1. Real tenant provisioning in `TenantProvisioningService`.
2. Dynamic tenant datasource lookup from `platform_tenant_database`.
3. Auth with tenant `sys_user` lookup and BCrypt password verification.
4. RBAC permission checks through shared security aspects.
5. Plan feature checks from `platform_plan_feature`.
6. CRUD and workflow APIs for leads, customers, deals, and tasks.
7. Dashboard, files, notices, OpenAPI keys, custom domains, and platform admin APIs.
