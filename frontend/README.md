# Fund Copilot Frontend

Vue 3 + TypeScript research workbench for Fund Copilot. The frontend contains fund overview,
multi-fund comparison, and traceable Agent analysis modules.

## Stack

- Vue 3
- Vite
- TypeScript
- Element Plus
- ECharts
- CSS

## Architecture

```text
src
├── api          # HTTP clients and backend endpoint adapters
├── components   # Reusable presentational components
├── composables  # Fund and Agent workflow orchestration
├── constants    # Stable workbench configuration
├── types        # API and domain contracts
├── utils        # Pure guards, formatters, and browser helpers
└── views        # Workbench module views
```

Use the `@/` alias for imports rooted at `src`. Keep API transport, workflow state, and
presentational components in their respective layers.

## Development

```bash
npm install
npm run dev
```

## Quality Checks

```bash
npm run check
```

`npm run check` runs ESLint, TypeScript validation, the production build, and Prettier
verification.
