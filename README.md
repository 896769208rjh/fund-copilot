# Fund Copilot

Fund Copilot 是一个面向基金业务场景的智能客服与基金分析工作台。当前版本已从 V1 单 Agent 演示升级为 V2 雏形：支持基金搜索、基金详情、东方财富公开数据同步、指标分析、AgentScope 基金分析、任务式 Agent 工作流、阶段报告持久化、SSE 过程展示和 Vue 3 + TypeScript 前端展示。

## 项目定位

本项目用于学习和验证基金智能客服的核心能力：

- 查询基金基础信息、净值、交易状态和历史表现。
- 基于公开数据生成收益、最大回撤、波动率等指标分析。
- 对“能买吗”“适合买入吗”“未来收益多少”等问题做合规改写。
- 借鉴 TradingAgents 的多角色协作思路，构建可追踪、可审计的基金 Agent 工作流。
- 使用 Vue 3 + TypeScript 提供基金研究工作台。

## 当前进度

已完成能力：

- 后端基于 Spring Boot `3.4.5`、Java 17、Spring AI Alibaba `1.0.0.2`、AgentScope Java `1.0.12`。
- 前端基于 Vue 3、Vite、TypeScript、Element Plus、ECharts。
- 默认使用 H2 演示数据库，提供 MySQL/Redis 可选配置。
- 已实现基金搜索、详情、历史净值、指标分析、手动同步接口。
- 已实现本地基金搜索 + 东方财富远程搜索，支持按基金代码、名称或简拼搜索。
- 已实现东方财富/天天基金公开数据 Provider，并提供本地演示数据兜底。
- 已实现搜索不到 6 位基金代码时的一键同步入口。
- 已实现多基金维度对比，支持输入多个基金代码横向比较基金类型、公司、经理、收益、回撤、波动、交易状态和数据来源。
- 已接入 Redis 缓存搜索、详情、净值、分析和对比结果；Redis 不可用时自动降级到数据库和远程数据源。
- 已实现支付宝基金池演示数据，不抓取真实支付宝账号。
- 已实现任务式 Agent 工作流：数据采集、业绩分析、风险分析、同池对比、优势风险讨论、合规审核、回答生成。
- 已将 Agent 工作流拆分为阶段 Handler，并新增显式图工作流执行器；当前支持按数据质量跳过不适合执行的同池对比阶段。
- 已将任务初始化与工作流执行解耦，使用命名线程池后台执行任务，任务创建接口可立即返回任务快照。
- 已新增按任务隔离的可回放 SSE 事件通道，执行中的任务支持实时订阅，已完成任务支持数据库历史回放。
- 已将 SSE 事件持久化到 `agent_task_event`，应用重启后仍可按事件顺序回放。
- 已实现任务幂等创建、任务取消、整体超时、指定阶段重跑和应用启动自动恢复未完成任务。
- 已为核心阶段增加可选 AgentScope 调用：启用 LLM 时由阶段 Agent 生成解读，未启用时走本地确定性分析并在报告中明确标注。
- 已将 AgentScope 模型创建、超时、迭代次数和失败降级收敛到统一调用器。
- 阶段输出包含结构化 DTO、Markdown 报告内容、SSE 事件、阶段输入快照和阶段输出快照。
- 已新增 `agent_task`、`agent_task_stage`、`agent_task_event`、`agent_report_section`、`agent_memory_entry` 持久化表。
- 已支持 Agent 任务创建、任务查询、历史任务列表、SSE 实时执行、历史回放、失败任务恢复和 Markdown 报告导出。
- 已为 Agent 任务保存可反序列化 `state_snapshot`、`next_stage_code`、`retry_count`，用于阶段级恢复和审计。
- 前端 Agent 区域已拆分执行时间线、结构化报告和阶段审计 TypeScript 组件。
- 已补充异步任务流、工作流、合规和指标计算测试。

## 技术栈

后端：

- Java 17
- Spring Boot 3.4.5
- Spring AI Alibaba 1.0.0.2
- AgentScope Java 1.0.12
- MyBatis-Plus 3.5.12
- WebFlux / SSE
- Jsoup / Jackson

前端：

- Vue 3
- Vite
- TypeScript
- Element Plus
- ECharts

数据与基础设施：

- H2：默认本地演示数据库
- MySQL：生产或持久化环境可选
- Redis：搜索、详情、净值、分析和对比缓存；限频、会话能力预留
- Docker Compose：可选启动 MySQL 和 Redis
- 东方财富 / 天天基金公开数据：当前主要数据来源

## 项目结构

```text
fund-copilot
├── frontend                  # Vue 3 + TypeScript 前端工作台
├── src/main/java/fundcopilot
│   ├── agent                 # AgentScope 分析、任务工作流、持久化模型
│   ├── chat                  # 统一聊天入口预留
│   ├── common                # 通用响应与异常处理
│   ├── compliance            # 合规检查与免责声明
│   ├── fund                  # 基金领域模型、接口、服务
│   └── marketdata            # 东方财富数据采集 Provider
├── src/main/resources
│   ├── application.yml       # 默认 H2 演示配置
│   ├── application-mysql.yml # MySQL profile
│   ├── schema.sql            # 表结构
│   └── data.sql              # 演示数据
└── docker-compose.yml        # 可选 MySQL / Redis
```

## 快速启动

启动后端：

```bash
mvnw.cmd spring-boot:run
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

前端默认通过 Vite 代理访问后端 `/api`，后端地址为：

```text
http://localhost:8080
```

## 可选 MySQL 和 Redis

默认 profile 使用 H2，适合快速演示。需要使用 MySQL 和 Redis 时：

```bash
docker compose up -d
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

MySQL 配置见 `src/main/resources/application-mysql.yml`。

如果你已经创建过旧版 MySQL 表，需要先执行：

```bash
src/main/resources/sql/upgrade-v2.5-agent-workflow.sql
```

如果你已经执行过早期的 `upgrade-v2.5-agent-workflow.sql`，但缺少阶段输入/输出审计字段，再执行：

```bash
src/main/resources/sql/upgrade-v2.6-agent-stage-audit.sql
```

需要增加任务控制、截止时间和事件持久化能力时，再执行：

```bash
src/main/resources/sql/upgrade-v2.7-agent-task-control.sql
```

## DashScope / LLM 配置

默认情况下，Agent 使用本地确定性分析，不依赖真实 LLM Key，因此点击“开始分析”会很快返回报告。需要启用真实 AgentScope + DashScope 阶段分析时：

```bash
set DASHSCOPE_API_KEY=your_key
set FUND_AGENT_ENABLE_LLM=true
mvnw.cmd spring-boot:run
```

启用后，数据采集、业绩分析、风险分析、同池对比、优势风险讨论和最终回答阶段会尝试调用 AgentScope；如果调用失败，会自动降级到本地确定性分析，并在结构化报告中标注调用失败原因。

模型名称可通过环境变量配置：

```bash
set DASHSCOPE_CHAT_MODEL=qwen-plus
set FUND_AGENT_STAGE_MAX_ITERATIONS=3
set FUND_AGENT_FINAL_MAX_ITERATIONS=6
set FUND_AGENT_REQUEST_TIMEOUT_SECONDS=60
set FUND_AGENT_TASK_TIMEOUT_SECONDS=300
```

## 已实现接口

基金接口：

- `GET /api/funds/search?keyword=`：搜索基金代码或名称。
- `GET /api/funds/compare?codes=000001,110022`：多基金维度对比。
- `GET /api/funds/{fundCode}`：查询基金详情。
- `GET /api/funds/{fundCode}/nav?limit=120`：查询历史净值。
- `GET /api/funds/{fundCode}/analysis`：查询后端指标分析结果。
- `POST /api/funds/{fundCode}/sync`：手动同步单只基金数据。

支付宝基金池：

- `GET /api/alipay/fund-pool`：查询 V1/V2 演示基金池。

Agent 分析：

- `POST /api/agents/fund-analysis`：兼容旧版非流式 Agent 分析。
- `POST /api/agents/fund-analysis/stream`：创建任务并流式返回阶段事件。
- `POST /api/agents/fund-analysis/tasks`：创建任务式基金分析，立即返回任务快照并在后台执行。
- `GET /api/agents/fund-analysis/tasks/{taskId}`：查询任务详情。
- `GET /api/agents/fund-analysis/tasks/{taskId}/stream`：订阅执行中任务或回放历史任务 SSE 事件。
- `POST /api/agents/fund-analysis/tasks/{taskId}/resume`：后台恢复失败或未完成任务。
- `POST /api/agents/fund-analysis/tasks/{taskId}/resume/stream`：流式恢复失败或未完成任务。
- `POST /api/agents/fund-analysis/tasks/{taskId}/cancel`：取消等待中或执行中的任务。
- `POST /api/agents/fund-analysis/tasks/{taskId}/stages/{stageCode}/rerun`：从指定阶段重新执行。
- `GET /api/agents/fund-analysis/tasks/{taskId}/report`：导出 Markdown 分析报告。
- `GET /api/agents/fund-analysis/tasks?fundCode=`：查询某只基金的历史分析任务。

SSE 事件：

- `TASK_CREATED`
- `PROGRESS`
- `STAGE_STARTED`
- `AGENT_STEP`
- `STAGE_DONE`
- `SECTION`
- `COMPLIANCE_BLOCKED`
- `TASK_CANCELLED`
- `TASK_TIMEOUT`
- `TASK_RERUN_STARTED`
- `CARD`
- `TOKEN`
- `ERROR`
- `DONE`

## 验证命令

后端测试：

```bash
mvn test
```

前端类型检查：

```bash
cd frontend
npm run type-check
```

前端生产构建：

```bash
cd frontend
npm run build
```

## 后续开发方向

V2 完善：

- 将内存任务执行器和 SSE 通道升级为可选的 Redis Stream / 消息队列方案，支持多实例部署。
- 增加事件归档、任务并发配额、模型调用限流、超时分类和可观测指标。
- 为东方财富 Provider 增加更完整的 fixture 测试和数据源失败降级测试。
- 增强前端历史报告检索、报告导出和多基金对比。

V3 数据增强与 RAG：

- 接入基金公告、招募说明书、基金合同、定期报告。
- 引入 Elasticsearch 或向量库，实现基金规则和公告问答。
- 增加数据新鲜度、来源可信度和解析失败监控。

V4 产品化客服：

- 增加用户体系、会话历史、权限控制、审计日志和人工转接。
- 对接真实业务系统的持仓、订单、交易状态查询能力。
- 支持 Web、企微、钉钉或客服系统等多渠道接入。

## 合规说明

本项目当前仅用于学习、演示和技术验证。系统输出内容仅基于公开数据和历史表现做信息分析，不构成任何投资建议、收益承诺或买卖依据。基金有风险，投资需谨慎。
