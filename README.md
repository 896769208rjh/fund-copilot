# Fund Copilot

Fund Copilot 是一个面向基金业务场景的智能客服与分析助手。当前版本聚焦“基金搜索、基金详情、东方财富数据同步、基金指标分析、合规提示、AgentScope 分析、Vue 可视化展示”的 V1 演示闭环，后续会继续演进为可接入真实业务数据和知识库的基金智能客服平台。

## 项目定位

本项目希望解决基金客服与投资者教育场景中的几个核心问题：

- 快速查询基金基础信息、净值、交易状态和历史表现。
- 基于公开数据生成结构化分析，展示收益、回撤、波动率等指标。
- 对“能买吗”“适合买入吗”等问题进行合规拦截，只提供事实分析和风险提示。
- 通过 AgentScope 编排基金分析流程，为后续多工具、多 Agent 协作打基础。
- 使用 Vue 3 + TypeScript 提供一个可演示、可扩展的前端工作台。

## 当前进度

V1 已完成第一版闭环：

- 后端已从 Spring Boot 4 调整为 Spring Boot `3.4.5`。
- 已接入 Spring AI Alibaba `1.0.0.2` 与 AgentScope Java `1.0.12`。
- 已实现基金搜索、详情、历史净值、指标分析、手动同步等接口。
- 已实现东方财富公开数据 Provider，并提供本地兜底数据。
- 已实现支付宝基金池演示数据，目前不涉及真实支付宝账号登录或自动抓取。
- 已实现 AgentScope 基金分析服务，默认使用本地可解释分析；配置 DashScope 后可启用 LLM。
- 已实现 SSE 流式 Agent 分析事件：`PROGRESS`、`AGENT_STEP`、`CARD`、`TOKEN`、`DONE`。
- 前端已迁移为 Vue 3 + TypeScript，使用 Element Plus 和 ECharts 展示基金工作台。
- 默认使用 H2 内存数据库和种子数据，方便本地直接演示。
- 已提供 MySQL/Redis 的可选 `docker-compose.yml`。
- 已补充指标计算与合规拦截单元测试。

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
- Redis：缓存、限频、会话能力预留
- Docker Compose：可选启动 MySQL 与 Redis
- 东方财富 / 天天基金公开数据：当前主要数据来源

## 项目结构

```text
fund-copilot
├── frontend                  # Vue 3 + TypeScript 前端工作台
├── src/main/java
│   └── fundcopilot
│       ├── agent             # AgentScope 分析编排
│       ├── chat              # 统一聊天入口预留
│       ├── common            # 通用响应与异常处理
│       ├── compliance        # 合规检查与免责声明
│       ├── fund              # 基金领域模型、接口、服务
│       └── marketdata        # 东方财富数据采集 Provider
├── src/main/resources
│   ├── application.yml       # 默认 H2 演示配置
│   ├── application-mysql.yml # MySQL profile
│   ├── schema.sql            # 表结构
│   └── data.sql              # V1 演示数据
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

前端开发地址以 Vite 控制台输出为准，通常是：

```text
http://127.0.0.1:5173/
```

## 可选 MySQL 与 Redis

默认 profile 使用 H2 内存数据库，适合快速演示。需要使用 MySQL 和 Redis 时：

```bash
docker compose up -d
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

MySQL 默认配置见 [application-mysql.yml](src/main/resources/application-mysql.yml)。

## DashScope / LLM 配置

默认情况下，Agent 使用本地确定性分析，不依赖真实 LLM Key。需要启用 DashScope 时：

```bash
set DASHSCOPE_API_KEY=your_key
set FUND_AGENT_ENABLE_LLM=true
mvnw.cmd spring-boot:run
```

模型名称可通过环境变量配置：

```bash
set DASHSCOPE_CHAT_MODEL=qwen-plus
```

## 已实现接口

基金接口：

- `GET /api/funds/search?keyword=`：搜索基金代码或名称。
- `GET /api/funds/{fundCode}`：查询基金详情。
- `GET /api/funds/{fundCode}/nav?limit=120`：查询历史净值。
- `GET /api/funds/{fundCode}/analysis`：查询后端指标分析结果。
- `POST /api/funds/{fundCode}/sync`：手动同步单只基金数据。

支付宝基金池：

- `GET /api/alipay/fund-pool`：查询 V1 演示基金池。

Agent 分析：

- `POST /api/agents/fund-analysis`：非流式 Agent 分析。
- `POST /api/agents/fund-analysis/stream`：SSE 流式 Agent 分析。

聊天入口：

- `POST /api/chat/stream`：统一聊天流式入口，目前路由到基金分析 Agent。

## 前端能力

当前前端已经实现：

- 基金代码/名称搜索。
- 支付宝基金池快捷选择。
- 基金详情、净值、交易状态展示。
- 收益、最大回撤、年化波动率等指标卡片。
- ECharts 净值走势图。
- 分析要点与风险提示展示。
- AgentScope 分析问题输入。
- SSE 执行步骤、分析卡片和最终回答展示。

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

短期方向：

- 完善东方财富数据解析，补充基金名称、基金经理、基金公司、规模、费率等真实字段。
- 增加基金搜索索引和更多基金样本，减少对实时网络的依赖。
- 为东方财富 Provider 增加 fixture 测试，避免单元测试依赖外部网络。
- 补齐接口集成测试，覆盖搜索、同步、分析、Agent 失败降级等流程。
- 优化前端图表交互，增加时间区间、指标切换和加载状态。
- 增加统一错误码、请求日志、接口参数校验和限流策略。

中期方向：

- 引入 RAG 知识库，接入基金合同、招募说明书、公告、交易规则等文档。
- 使用 Elasticsearch 或向量数据库实现公告、规则、投教内容检索。
- 增加真实持仓、订单、交易确认等业务工具接口，但不直接抓取个人支付宝账号。
- 建立更细的合规策略，例如收益承诺拦截、适当性提示、敏感问题人工转接。
- 将 AgentScope 拆分为多个专业 Agent，例如数据采集 Agent、指标分析 Agent、合规审核 Agent、客服回复 Agent。
- 引入 Redis 缓存热点基金、Agent 运行状态、SSE 会话和接口限频。

长期方向：

- 支持用户体系、权限控制、审计日志和客服工作台。
- 支持多渠道接入，例如 Web、企业微信、钉钉或客服系统。
- 接入真实业务系统，实现持仓查询、交易状态查询和工单流转。
- 构建基金画像、用户画像和风险偏好匹配能力。
- 增加监控告警、灰度发布、模型评测和回答质量回放。
- 形成可部署的生产架构，包括 MySQL、Redis、对象存储、RAG 服务和模型服务。

## 合规说明

本项目当前仅用于学习、演示和技术验证。系统输出内容仅基于公开数据和历史表现做信息分析，不构成任何投资建议、收益承诺或买卖依据。基金有风险，投资需谨慎。
