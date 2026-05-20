# GJ OrbitCRM

GJ OrbitCRM is a portfolio-grade multi-tenant CRM SaaS demo built to show commercial backend architecture, not a single-page toy demo.

The project demonstrates tenant provisioning, independent tenant databases, subscription and plan limits, RBAC, CRM workflows, files, messages, reports, OpenAPI access, and a platform admin backend.

## Product Positioning

GJ OrbitCRM targets small and midsize sales teams, consulting teams, training organizations, and service businesses that need a clean CRM foundation with SaaS-grade isolation.

Core capabilities:

- Multi-tenant registration and provisioning
- Platform database plus independent tenant business databases
- JWT authentication and tenant-aware RBAC
- Plan, subscription, order, payment, and quota checks
- Leads, customers, deals, sales pipelines, tasks, and dashboard reports
- MinIO-backed file metadata and storage controls
- RabbitMQ-based notices and task reminders
- Tenant custom domain binding
- API key based OpenAPI lead intake
- Platform admin APIs for tenant, billing, plan, and operation management

## Architecture

```text
Web Frontend
  -> Nginx
  -> Spring Cloud Gateway
  -> Auth / Tenant Resolution / Route Forwarding
  -> Microservices
  -> MySQL / Redis / RabbitMQ / MinIO / Nacos
```

Service modules:

```text
orbit-gateway        API gateway
orbit-auth           Login and JWT issuing
orbit-tenant         Tenant registration, provisioning, and custom domains
orbit-billing        Plans, subscriptions, orders, payments, and expiry jobs
orbit-system         Tenant users, roles, permissions, and operation logs
orbit-crm-service    Leads, customers, deals, and pipelines
orbit-task           Tasks and reminders
orbit-report         Dashboard summary and CRM metrics
orbit-file           File metadata and MinIO storage access
orbit-message        Notices and RabbitMQ message intake
orbit-openapi        API key management and external lead intake
orbit-admin          Platform operation backend
orbit-common         Shared core, web, security, datasource, Redis, and logging modules
```

## Tech Stack

- Java 8
- Spring Boot 2.7.x
- Spring Cloud 2021.x
- Spring Cloud Alibaba 2021.x
- MyBatis-Plus
- Spring Security and JWT
- MySQL 8.0
- Redis
- Nacos
- RabbitMQ
- MinIO
- Docker Compose

## Database Model

The platform database is `orbit_platform`. It stores tenant records, database connection records, custom domains, plans, plan features, subscriptions, orders, payments, platform admins, and platform operation logs.

Each tenant gets an independent database such as `orbit_tenant_10001`. Tenant databases store users, roles, permissions, leads, customers, deals, tasks, files, notices, OpenAPI keys, and operation logs.

## OpenAPI Example

Internal tenant admins create an API key from:

```text
POST /api/openapi/keys
```

External systems can then write website or ad leads into GJ OrbitCRM:

```text
POST /openapi/v1/leads
X-Tenant-Code: demo-company
X-OpenAPI-Key: orb_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## Local Infrastructure

Start supporting middleware:

```powershell
docker compose -f deploy/docker/docker-compose.yml up -d
```

Validate project structure:

```powershell
.\scripts\validate-structure.ps1
```

## Portfolio Notes

The public-facing product name is **GJ OrbitCRM**. Runtime service IDs, Maven artifact IDs, package names, and database names intentionally keep the stable `orbit-*` / `orbitcrm` naming to avoid unnecessary infrastructure churn.
