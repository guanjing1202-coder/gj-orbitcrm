# GJ OrbitCRM

GJ OrbitCRM 是一个用于个人作品展示的多租户 CRM SaaS 后端项目。项目目标不是做一个简单 CRUD Demo，而是尽量按照可商业化 SaaS 系统的方式组织工程：多租户开通、独立租户数据库、套餐订阅、权限控制、CRM 核心业务、文件、消息、报表、开放 API 和平台运营后台都被拆成独立模块。

这个项目适合用来展示 Java 微服务架构设计、SaaS 多租户建模、Spring Cloud 工程拆分、租户数据隔离、RBAC 权限、计费限制、业务模块落地和平台化后台能力。

## 项目定位

GJ OrbitCRM 面向中小企业、销售团队、教育培训机构、咨询公司、装修公司和企业服务团队，提供一套 CRM SaaS 的基础能力。

核心目标：

- 支持多个企业租户独立注册和开通
- 支持平台库加租户独立业务库的数据隔离模式
- 支持套餐、订阅、订单、支付记录和功能限制
- 支持租户级用户、角色、菜单和权限控制
- 支持线索、客户、联系人、商机、销售漏斗和任务管理
- 支持文件附件、消息通知、操作日志和数据看板
- 支持租户自定义域名和第三方 OpenAPI 线索写入
- 支持平台运营后台管理租户、套餐、订阅和日志

## 技术栈

后端与微服务：

```text
Java 8
Spring Boot 2.7.x
Spring Cloud 2021.x
Spring Cloud Alibaba 2021.x
Spring Cloud Gateway
Spring Security
JWT
MyBatis-Plus
Maven
```

数据库与中间件：

```text
MySQL 8.0
Redis
Nacos
RabbitMQ
MinIO
```

部署与运维：

```text
Docker
Docker Compose
Nginx
Prometheus / Grafana 预留
SkyWalking 预留
```

## 总体架构

```text
前端 Web / 第三方系统
  ↓
Nginx
  ↓
Spring Cloud Gateway
  ↓
认证鉴权 / 租户识别 / 路由转发
  ↓
业务微服务集群
  ↓
MySQL / Redis / RabbitMQ / MinIO / Nacos
```

租户请求进入系统后，会先完成租户识别，再进入对应业务服务。业务服务通过公共租户上下文找到当前租户，并从平台库读取租户数据库连接信息，最终访问该租户独立业务库。

## 多租户设计

项目采用：

```text
平台库 orbit_platform + 租户独立业务库 orbit_tenant_xxx
```

平台库保存 SaaS 平台级数据：

- 租户基础信息
- 租户数据库连接信息
- 租户自定义域名
- 套餐与套餐功能
- 订阅、订单、支付记录
- 平台管理员
- 平台操作日志

租户业务库保存该租户自己的业务数据：

- 用户、角色、菜单、权限
- 线索、客户、联系人
- 商机、销售管道、销售阶段
- 跟进记录、任务、标签
- 文件元数据
- 站内通知
- OpenAPI Key
- 租户操作日志

租户识别优先级：

```text
自定义域名 > 子域名 > X-Tenant-Code 请求头 > 登录参数中的 tenantCode
```

## 项目结构

```text
gj-orbitcrm
├── database
│   ├── platform                 平台库 SQL
│   └── tenant-template           租户业务库初始化 SQL
├── deploy
│   └── docker                    本地中间件 Docker Compose
├── docs
│   ├── architecture              架构说明文档
│   └── frontend                  前端展示文案规范
├── orbit-common                  公共能力模块
│   ├── common-core               通用返回、租户上下文、事件模型
│   ├── common-web                租户识别过滤器
│   ├── common-security           JWT、当前用户、权限注解
│   ├── common-datasource         动态租户数据源
│   ├── common-redis              Redis 公共模块预留
│   └── common-log                操作日志切面
├── orbit-gateway                 API 网关
├── orbit-auth                    认证服务
├── orbit-tenant                  租户服务
├── orbit-billing                 套餐订阅与计费服务
├── orbit-system                  租户系统管理服务
├── orbit-crm-service             CRM 核心业务服务
├── orbit-task                    任务与提醒服务
├── orbit-report                  报表服务
├── orbit-file                    文件服务
├── orbit-message                 消息通知服务
├── orbit-openapi                 开放 API 服务
├── orbit-admin                   平台运营后台服务
└── scripts                       项目结构校验脚本
```

## 服务模块说明

| 模块 | 说明 |
| --- | --- |
| `orbit-gateway` | Spring Cloud Gateway 网关，统一转发 `/api/**`、`/openapi/**` 请求 |
| `orbit-auth` | 租户登录、BCrypt 密码校验、JWT 签发、登录日志 |
| `orbit-tenant` | 租户注册、数据库开通、租户管理员初始化、自定义域名绑定与校验 |
| `orbit-billing` | 套餐、功能限制、订阅、订单、支付确认、到期冻结 |
| `orbit-system` | 租户级用户、角色、权限、操作日志查询 |
| `orbit-crm-service` | 线索、客户、商机、销售管道等 CRM 核心能力 |
| `orbit-task` | 任务创建、完成、提醒扫描、RabbitMQ 通知 |
| `orbit-report` | Dashboard 汇总、线索状态统计、销售漏斗、任务统计 |
| `orbit-file` | 文件上传、下载、删除、MinIO 存储、文件空间限制 |
| `orbit-message` | 站内通知、未读数量、已读状态、RabbitMQ 消息消费 |
| `orbit-openapi` | OpenAPI Key 管理、第三方系统线索写入 |
| `orbit-admin` | 平台运营后台，管理租户、套餐、订阅、订单和平台日志 |
| `orbit-common` | 公共 API 返回、租户上下文、数据源、安全、日志等基础能力 |

## 功能模块与具体功能点

### 1. 租户注册与开通

- 租户注册接口
- 创建平台租户记录
- 创建租户独立数据库
- 执行租户库初始化 SQL
- 初始化租户管理员账号
- 初始化角色、菜单、权限
- 初始化默认部门
- 初始化默认销售管道和销售阶段
- 创建试用订阅
- 保存租户数据库连接信息

### 2. 租户识别与数据隔离

- 支持 `X-Tenant-Code` 请求头识别租户
- 支持 `company.orbitcrm.com` 子域名识别租户
- 支持自定义域名识别租户
- 每个租户使用独立业务数据库
- 公共模块维护当前请求租户上下文
- 业务 SQL 自动进入当前租户数据库
- 请求结束后清理租户上下文，避免线程复用污染

### 3. 认证与权限

- 租户账号登录
- BCrypt 密码校验
- JWT Token 签发
- Token 中包含租户编码和用户身份
- 登录租户与请求租户不一致时拒绝访问
- `@RequiresPermission` 注解式权限控制
- 租户级角色包括 `Tenant Admin`、`Sales Manager`、`Sales`、`Viewer`
- 支持用户、角色、权限关系表
- 支持登录日志和操作日志

### 4. 套餐订阅与计费

- Starter、Professional、Enterprise 三档套餐
- 套餐功能通过 `platform_plan_feature` 配置，不写死在代码里
- 支持最大用户数、最大客户数、最大线索数、文件空间、自定义域名等限制
- 支持创建订单
- 支持支付确认
- 支持续费和套餐变更
- 支持试用期
- 支持订阅状态：`TRIAL`、`ACTIVE`、`PAST_DUE`、`EXPIRED`、`CANCELED`、`FROZEN`
- 定时扫描过期订阅
- 过期、取消、冻结状态会限制登录或 OpenAPI 访问

### 5. CRM 核心业务

线索管理：

- 线索列表
- 新增线索
- 线索详情
- 编辑线索资料
- 分配线索负责人
- 线索状态更新
- 线索软删除
- 线索回收站
- 恢复已删除线索
- 线索转客户
- 线索来源记录
- 负责人字段
- 线索套餐上限检查

客户管理：

- 客户列表
- 新增客户
- 客户详情
- 编辑客户资料
- 转移客户负责人
- 客户软删除
- 客户回收站
- 恢复已删除客户
- 客户基础信息
- 客户类型、联系方式、地址
- 客户负责人
- 客户套餐上限检查
- 客户联系人列表
- 新增联系人
- 编辑联系人
- 设置主联系人
- 客户标签列表
- 新增标签
- 替换客户标签
- 单个添加或移除客户标签

商机管理：

- 新增商机
- 销售管道
- 销售阶段
- 商机看板
- 阶段移动
- 预计成交金额
- 预计成交日期
- 赢率字段

销售管道：

- 默认销售管道
- 默认阶段：`Qualified`、`Proposal`、`Negotiation`、`Won`
- 销售管道列表
- 新增销售管道
- 设置默认销售管道
- 阶段列表
- 新增阶段
- 编辑阶段名称、赢率和排序
- 阶段排序
- 阶段赢率

跟进记录：

- 支持线索、客户、商机三类对象
- 查询业务对象跟进记录
- 新增跟进内容
- 记录下次跟进时间
- 自动记录创建人
- 通过操作日志记录写入动作

### 6. 任务与提醒

- 任务列表
- 新增任务
- 完成任务
- 关联客户、线索或商机
- 负责人
- 截止时间
- 提醒时间
- 提醒状态
- 定时扫描待提醒任务
- RabbitMQ 发布任务提醒消息
- 手动触发提醒扫描接口

### 7. 报表与看板

- Dashboard 汇总数据
- 线索数量
- 客户数量
- 商机数量
- 任务数量
- 线索状态分布
- 销售漏斗统计
- 任务状态统计
- 面向租户工作台的数据概览接口

### 8. 文件服务

- 文件上传
- 文件下载
- 文件软删除
- 业务对象关联
- 文件元数据保存到租户库
- MinIO 对象存储
- 根据套餐限制文件空间
- 记录上传用户和文件大小

### 9. 消息通知

- 创建站内通知
- 查询我的通知
- 查询未读数量
- 标记已读
- RabbitMQ 消息消费
- 可接收任务提醒等业务事件
- 支持通知接收人表

### 10. 自定义域名

- 租户提交自定义域名
- 生成域名校验 token
- 支持 CNAME / TXT 校验思路
- 校验通过后启用域名
- Gateway / Web 过滤器可根据域名解析租户
- 自定义域名能力受套餐限制

### 11. OpenAPI 开放接口

- 租户管理员创建 OpenAPI Key
- OpenAPI Key 明文只在创建时返回一次
- 数据库只保存 SHA-256 Hash
- 支持 Key 前缀展示
- 支持禁用 Key
- 支持 Scope 权限，例如 `crm:lead:write`
- 第三方官网、广告表单或落地页可写入线索
- OpenAPI 请求同时校验租户、Key、Scope 和订阅状态

外部线索写入示例：

```http
POST /openapi/v1/leads
X-Tenant-Code: demo-company
X-OpenAPI-Key: orb_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Content-Type: application/json

{
  "leadName": "张三",
  "companyName": "示例公司",
  "phone": "13800000000",
  "email": "zhangsan@example.com",
  "source": "Landing Page"
}
```

### 12. 平台运营后台

- 平台 API Key 保护后台接口
- 平台概览数据
- 租户列表
- 修改租户状态
- 套餐列表
- 新增或更新套餐
- 配置套餐功能项
- 查询订阅
- 查询订单
- 查询平台操作日志

## 数据库设计

平台库：`orbit_platform`

主要表：

```text
platform_tenant
platform_tenant_domain
platform_tenant_database
platform_plan
platform_plan_feature
platform_subscription
platform_order
platform_payment
platform_operation_log
platform_admin_user
```

租户库模板：`database/tenant-template/001_tenant_schema.sql`

主要表：

```text
sys_user
sys_role
sys_menu
sys_permission
sys_user_role
sys_role_menu
sys_role_permission
sys_dept
sys_login_log
sys_operation_log

crm_lead
crm_customer
crm_contact
crm_pipeline
crm_pipeline_stage
crm_deal
crm_task
crm_follow_record
crm_tag
crm_customer_tag

sys_file
sys_notice
sys_notice_receiver
sys_openapi_key
```

## 主要接口示例

```text
POST   /api/tenants/register                 租户注册
GET    /api/tenants/domains                  查询租户域名
POST   /api/tenants/domains                  绑定域名
POST   /api/tenants/domains/{id}/verify      校验域名

POST   /api/auth/login                       租户登录

GET    /api/billing/plans                    套餐列表
GET    /api/billing/subscriptions/current    当前订阅
GET    /api/billing/orders                   订单列表
POST   /api/billing/orders                   创建订单
POST   /api/billing/orders/{id}/payments     支付确认

GET    /api/crm/leads                        线索列表
GET    /api/crm/leads/deleted                线索回收站
GET    /api/crm/leads/{id}                   线索详情
POST   /api/crm/leads                        创建线索
PATCH  /api/crm/leads/{id}                   编辑线索
PATCH  /api/crm/leads/{id}/status            更新线索状态
PATCH  /api/crm/leads/{id}/owner             分配线索负责人
POST   /api/crm/leads/{id}/convert           线索转客户
DELETE /api/crm/leads/{id}                   删除线索
PATCH  /api/crm/leads/{id}/restore           恢复线索

GET    /api/crm/customers                    客户列表
GET    /api/crm/customers/deleted            客户回收站
GET    /api/crm/customers/{id}               客户详情
POST   /api/crm/customers                    创建客户
PATCH  /api/crm/customers/{id}               编辑客户
PATCH  /api/crm/customers/{id}/owner         转移客户负责人
DELETE /api/crm/customers/{id}               删除客户
PATCH  /api/crm/customers/{id}/restore       恢复客户
GET    /api/crm/contacts                     联系人列表
POST   /api/crm/contacts                     创建联系人
PATCH  /api/crm/contacts/{id}                编辑联系人
PATCH  /api/crm/contacts/{id}/primary        设置主联系人
GET    /api/crm/tags                         标签列表
POST   /api/crm/tags                         创建标签
GET    /api/crm/customers/{id}/tags          客户标签列表
PUT    /api/crm/customers/{id}/tags          替换客户标签
POST   /api/crm/customers/{id}/tags/{tagId}  添加客户标签
DELETE /api/crm/customers/{id}/tags/{tagId}  移除客户标签

GET    /api/crm/follow-records               跟进记录列表
POST   /api/crm/follow-records               创建跟进记录

POST   /api/crm/deals                        创建商机
GET    /api/crm/deals/board                  商机看板
PATCH  /api/crm/deals/{id}/stage             移动商机阶段
GET    /api/crm/pipelines                    销售管道列表
POST   /api/crm/pipelines                    创建销售管道
PATCH  /api/crm/pipelines/{id}/default       设置默认销售管道
GET    /api/crm/pipelines/{id}/stages        阶段列表
POST   /api/crm/pipelines/{id}/stages        创建阶段
PATCH  /api/crm/pipelines/stages/{stageId}   编辑阶段

GET    /api/tasks                            任务列表
POST   /api/tasks                            创建任务
PATCH  /api/tasks/{id}/complete              完成任务
POST   /api/tasks/reminders/dispatch         手动触发提醒

GET    /api/reports/dashboard/summary        Dashboard 汇总

GET    /api/files                            文件列表
POST   /api/files                            上传文件
GET    /api/files/{id}/download              下载文件
DELETE /api/files/{id}                       删除文件

GET    /api/messages/notices/mine            我的通知
GET    /api/messages/notices/mine/unread-count 未读数量
PATCH  /api/messages/notices/{id}/read       标记已读
POST   /api/messages/notices                 创建通知
POST   /api/messages/notices/events          发布通知事件

GET    /api/system/users                     用户列表
POST   /api/system/users                     创建用户
PATCH  /api/system/users/{id}/status         修改用户状态
GET    /api/system/roles                     角色列表
POST   /api/system/roles                     创建角色
GET    /api/system/permissions               权限列表
GET    /api/system/operation-logs            操作日志

GET    /api/openapi/keys                     OpenAPI Key 列表
POST   /api/openapi/keys                     创建 OpenAPI Key
PATCH  /api/openapi/keys/{id}/disable        禁用 OpenAPI Key
POST   /openapi/v1/leads                     外部线索写入

GET    /api/admin/dashboard/summary          平台概览
GET    /api/admin/tenants                    平台租户列表
PATCH  /api/admin/tenants/{id}/status        修改租户状态
GET    /api/admin/plans                      平台套餐列表
GET    /api/admin/billing/subscriptions      平台订阅列表
GET    /api/admin/billing/orders             平台订单列表
```

## 本地中间件

项目提供 Docker Compose 用于启动本地依赖：

```powershell
docker compose -f deploy/docker/docker-compose.yml up -d
```

包含服务：

```text
MySQL      3306
Redis      6379
RabbitMQ   5672 / 15672
Nacos      8848 / 9848
MinIO      9000 / 9001
```

## 常用环境变量

```text
MYSQL_HOST
MYSQL_PORT
MYSQL_USER
MYSQL_PASSWORD
NACOS_ADDR
JWT_SECRET
JWT_TTL_MILLIS
RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
MINIO_ENDPOINT
MINIO_ACCESS_KEY
MINIO_SECRET_KEY
ORBIT_ADMIN_API_KEY
```

## 项目校验

当前仓库提供结构校验脚本：

```powershell
.\scripts\validate-structure.ps1
```

校验内容包括：

- 核心文件是否存在
- Maven POM XML 是否可解析
- 是否出现明显不兼容 Java 8 的 API 或语法

## 展示说明

本项目对外展示名为 **GJ OrbitCRM**。为了保持工程稳定性，Maven artifact、Java 包名、服务发现名、数据库名仍保留 `orbit-*` / `orbitcrm` 命名。这种做法可以让展示层有个人品牌，同时避免运行时服务名和模块依赖频繁变动。

## 当前完成度

已完成后端主体骨架和多个核心业务链路：

- 多租户注册开通
- 独立租户数据库初始化
- 租户登录和 JWT 鉴权
- 租户级 RBAC 权限
- 套餐订阅和功能限制
- CRM 线索、客户、商机
- 任务和提醒
- Dashboard 报表
- 文件和消息通知
- 自定义域名绑定
- OpenAPI Key 和外部线索写入
- 平台运营后台

后续可继续扩展：

- 前端管理界面
- 在线支付渠道
- 自动 SSL 证书签发
- 发票模块
- Webhook
- 高级审计日志
- Prometheus / Grafana 监控面板
- SkyWalking 链路追踪
