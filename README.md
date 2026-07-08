# Fund Copilot

Fund Copilot is an AI-powered customer service system for mutual fund scenarios. It is designed to help users understand fund products, trading rules, NAV data, portfolio status, order progress, investor education content, and compliance-related explanations through traceable and risk-aware conversations.

This project is built on Spring Boot and is planned to integrate Spring AI Alibaba for LLM access, RAG retrieval, tool calling, streaming chat, and domain-specific fund service workflows.

## Goals

- Provide intelligent Q&A for fund products, fees, risks, managers, announcements, and trading rules.
- Support source-grounded answers through retrieval-augmented generation.
- Connect structured business data such as NAV, orders, holdings, and transaction status.
- Keep financial responses compliance-aware, conservative, and traceable.
- Offer a foundation for both end-user service and customer support assistant scenarios.

## Planned Capabilities

- Fund product knowledge base and document retrieval
- Trading rule explanation for subscription, redemption, confirmation, and settlement
- NAV, performance, and announcement lookup
- Portfolio and order query through secure tool calling
- Risk disclosure and investor education responses
- Streaming chat with progress events and source references
- Manual support handoff for sensitive or unresolved issues

## Tech Direction

- Java
- Spring Boot
- Spring AI Alibaba
- Vue 3
- Vite
- DashScope-compatible LLM and embedding models
- RAG and vector search
- MySQL for business data
- Redis for cache and session support
- REST and streaming conversation APIs

## Project Status

The repository currently contains the initial Spring Boot project skeleton. Domain modules, AI integration, retrieval pipelines, and business tools will be added incrementally.

## Quick Start

Backend:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Production build:

```bash
cd frontend
npm run build
```

## Repository

GitHub: https://github.com/896769208rjh/fund-copilot
