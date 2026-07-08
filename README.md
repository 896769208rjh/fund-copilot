# Fund Copilot

Fund Copilot is an AI-powered mutual fund customer service workspace. V1 provides fund search, Eastmoney data sync, NAV trend charts, metric analysis, compliance guardrails, and an AgentScope-based fund analysis flow.

## Tech Stack

- Backend: Java 17, Spring Boot 3.4.5, Spring AI Alibaba 1.0.0.2, AgentScope Java 1.0.12, MyBatis-Plus
- Data: H2 for local demo, MySQL profile for persistence, Redis reserved for cache/session use
- Frontend: Vue 3, Vite, TypeScript, Element Plus, ECharts
- Data source: Eastmoney/Tiantian Fund public fund data

## V1 Capabilities

- Search fund code/name and load detail data
- Sync a single fund from Eastmoney with local fallback data
- Show latest NAV, trading status, historical NAV curve, return, drawdown, and volatility
- Keep an Alipay-focused fund pool for demo scenarios
- Run non-streaming and SSE Agent analysis with compliance reminders
- Avoid buy/sell recommendations and future return promises

## Quick Start

Backend:

```bash
mvnw.cmd spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Open the frontend URL printed by Vite. The frontend dev server proxies `/api` to `http://localhost:8080`.

## Optional MySQL And Redis

```bash
docker compose up -d
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mysql
```

The default profile uses H2 in-memory tables and seed data, so MySQL/Redis are not required for the first demo.

## LLM Configuration

Local deterministic analysis is enabled by default. To call DashScope through AgentScope:

```bash
set DASHSCOPE_API_KEY=your_key
set FUND_AGENT_ENABLE_LLM=true
mvnw.cmd spring-boot:run
```

## Verification

```bash
mvn test
cd frontend
npm run type-check
npm run build
```

## Public APIs

- `GET /api/funds/search?keyword=`
- `GET /api/funds/{fundCode}`
- `GET /api/funds/{fundCode}/nav?limit=120`
- `GET /api/funds/{fundCode}/analysis`
- `POST /api/funds/{fundCode}/sync`
- `GET /api/alipay/fund-pool`
- `POST /api/agents/fund-analysis`
- `POST /api/agents/fund-analysis/stream`
