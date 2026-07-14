# Fund Copilot

[中文](README.md) | **English**

Fund Copilot is an intelligent customer service and fund research workspace built for mutual fund scenarios. The project has evolved from a V1 single-agent demo into an early V2 system with fund search, fund details, Eastmoney public data synchronization, quantitative metrics, AgentScope-powered analysis, persistent task-based agent workflows, SSE progress streaming, and a Vue 3 + TypeScript frontend.

## Project Overview

This project explores and validates the core capabilities of an AI-powered fund service:

- Query fund profiles, net asset values, trading status, and historical performance.
- Calculate returns, maximum drawdown, volatility, and other metrics from public data.
- Reframe questions such as "Should I buy this fund?" or "How much will it earn?" into compliant risk and suitability analysis.
- Apply the multi-role collaboration concepts of TradingAgents to a traceable and auditable fund agent workflow.
- Provide a fund research workspace built with Vue 3 and TypeScript.

## Current Status

Implemented capabilities:

- Backend built with Spring Boot `3.4.5`, Java 17, Spring AI Alibaba `1.0.0.2`, and AgentScope Java `1.0.12`.
- Frontend built with Vue 3, Vite, TypeScript, Element Plus, and ECharts.
- H2 is used by default for local demos, with optional MySQL and Redis configurations.
- Fund search, details, historical NAV, metric analysis, and manual synchronization APIs.
- Local and Eastmoney remote search by fund code, name, or name abbreviation.
- Eastmoney and Tiantian Fund public data providers with deterministic local demo fallback data.
- One-click synchronization when a six-digit fund code is not found locally.
- Multi-fund comparison across fund type, company, manager, return, drawdown, volatility, trading status, and data source.
- Redis caching for search, details, NAV, analysis, and comparison results, with automatic fallback when Redis is unavailable.
- A demo Alipay fund pool without access to real Alipay accounts.
- A task-based agent workflow covering data collection, performance analysis, risk analysis, peer comparison, factor discussion, compliance review, and answer composition.
- Stage handlers and an explicit graph workflow executor, including conditional peer-comparison skipping based on data quality.
- An explicit directed acyclic state graph: after data collection, performance, risk, and peer analysis run in parallel before joining at factor synthesis, compliance review, and answer composition.
- Startup validation for duplicate nodes, missing dependencies, and cycles. Recovery resumes from the persisted successful-node set, while stage reruns clear only the target node and its graph descendants.
- Decoupled task initialization and workflow execution with a named background thread pool, allowing task creation APIs to return immediately.
- Replayable, task-isolated SSE channels for live subscriptions and persisted event history.
- Persistent SSE events in `agent_task_event`, allowing ordered replay after an application restart.
- Idempotent task creation, cancellation, global timeout control, stage reruns, and automatic recovery of unfinished tasks at startup.
- Optional AgentScope invocation in core stages. When LLM access is disabled, the system uses deterministic local analysis and clearly labels the report mode.
- Centralized AgentScope model construction, timeout control, iteration limits, and fallback handling.
- An OpenAI-compatible `gpt-5.4` integration with fast, balanced, and deep reasoning modes.
- Java record structured output for stage narratives and final answers, with required-field validation, one corrective retry, and deterministic fallback.
- Role-based reasoning effort: data auditing and compliance use fast reasoning, while factor synthesis and final answers use at least balanced reasoning.
- Persistent `agent_model_call` telemetry covering stage, model, reasoning effort, prompt version, output schema, tokens, latency, retries, and fallback reasons.
- NAV-derived annualized return, downside volatility, return-to-drawdown ratio, sample size, and observation span, with explicit short-sample limitations.
- Structured DTOs, Markdown report content, SSE events, and input/output snapshots for each stage.
- Persistent `agent_task`, `agent_task_stage`, `agent_task_event`, `agent_report_section`, and `agent_memory_entry` tables.
- Task creation, task details, history, live SSE execution, event replay, failed-task recovery, and Markdown report export.
- Serializable `state_snapshot`, `next_stage_code`, and `retry_count` fields for stage-level recovery and auditing.
- Separate TypeScript components for the agent timeline, structured report, and stage audit views.
- Tests covering asynchronous task execution, workflows, compliance rules, and metric calculations.

## Technology Stack

Backend:

- Java 17
- Spring Boot 3.4.5
- Spring AI Alibaba 1.0.0.2
- AgentScope Java 1.0.12
- MyBatis-Plus 3.5.12
- WebFlux / SSE
- Jsoup / Jackson

Frontend:

- Vue 3
- Vite
- TypeScript
- Element Plus
- ECharts

Data and infrastructure:

- H2: default local demo database
- MySQL: optional persistent or production database
- Redis: cache for search, details, NAV, analysis, and comparison; rate limiting and session support are reserved for future work
- Docker Compose: optional MySQL and Redis startup
- Eastmoney / Tiantian Fund public data: primary market data sources

## Project Structure

```text
fund-copilot
├── frontend                  # Vue 3 + TypeScript research workspace
├── src/main/java/fundcopilot
│   ├── agent                 # AgentScope analysis, workflows, and persistence
│   ├── chat                  # Reserved unified chat entry point
│   ├── common                # Shared responses and exception handling
│   ├── compliance            # Compliance checks and disclaimers
│   ├── fund                  # Fund domain models, APIs, and services
│   └── marketdata            # Eastmoney market data providers
├── src/main/resources
│   ├── application.yml       # Default H2 demo configuration
│   ├── application-mysql.yml # MySQL profile
│   ├── schema.sql            # Database schema
│   └── data.sql              # Demo data
└── docker-compose.yml        # Optional MySQL / Redis services
```

## Quick Start

Start the backend:

```bash
mvnw.cmd spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

The frontend proxies `/api` requests to the backend through Vite. The default backend URL is:

```text
http://localhost:8080
```

## Optional MySQL and Redis

The default profile uses H2 and is suitable for quick demos. To use MySQL and Redis:

```bash
docker compose up -d
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

See `src/main/resources/application-mysql.yml` for the MySQL profile.

For an existing database created with an earlier version, first run:

```bash
src/main/resources/sql/upgrade-v2.5-agent-workflow.sql
```

If the early V2.5 migration has already been applied but stage audit columns are missing, run:

```bash
src/main/resources/sql/upgrade-v2.6-agent-stage-audit.sql
```

To add task control, deadline, and persistent event support, run:

```bash
src/main/resources/sql/upgrade-v2.7-agent-task-control.sql
```

To persist the agent reasoning mode, run:

```bash
src/main/resources/sql/upgrade-v2.8-agent-thinking-mode.sql
```

To add state-graph model call telemetry, run:

```bash
src/main/resources/sql/upgrade-v2.9-agent-graph-observability.sql
```

## OpenAI-Compatible LLM Configuration

By default, the agent uses deterministic local analysis and does not require an API key. To enable AgentScope with an OpenAI-compatible model, create `config/application-private.yml` in the project root:

```yaml
fund-copilot:
  agent:
    base-url: https://your-openai-compatible-service/v1
    api-key: your_key
    model-name: gpt-5.4
    enable-llm: true
    stage-max-iterations: 3
    final-max-iterations: 6
    request-timeout-seconds: 60
    task-timeout-seconds: 300
```

`application.yml` imports this file through `optional:file:`. The `config/application-private.yml` path is ignored by Git. Never place real service URLs or API keys in tracked configuration files. When enabled, the analysis stages invoke AgentScope and automatically fall back to deterministic local analysis if a model call fails.

Frontend reasoning modes map to OpenAI `reasoning_effort` values as follows:

- Fast: `low`
- Balanced: `medium`, the default
- Deep: `high`

## Verification

Backend tests:

```bash
mvn test
```

Frontend type checking:

```bash
cd frontend
npm run type-check
```

Frontend production build:

```bash
cd frontend
npm run build
```

## Roadmap

V2 completion:

- Add optional Redis Streams or message queue execution for multi-instance deployments.
- Add event archiving, per-user task quotas, model rate limiting, cost accounting, and timeout classification.
- Expand fixture and data-source failure tests for the Eastmoney provider.
- Improve historical report search, model-call filtering, and multi-fund comparison in the frontend.

V3 data enrichment and RAG:

- Ingest fund announcements, prospectuses, contracts, and periodic reports.
- Introduce Elasticsearch or a vector database for fund rule and announcement Q&A.
- Monitor data freshness, source reliability, and parsing failures.

V4 customer service productization:

- Add users, conversation history, access control, audit logs, and human handoff.
- Integrate holdings, orders, and transaction-status queries from real business systems.
- Support Web, WeCom, DingTalk, and customer service platform channels.

## Compliance Notice

This project is intended for learning, demonstration, and technical validation only. All output is based on public data and historical performance. It does not constitute investment advice, a return guarantee, or a basis for buying or selling any fund. Investing in funds involves risk.
