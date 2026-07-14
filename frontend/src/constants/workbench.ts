import type { AgentThinkingMode } from '@/types'

export type WorkspaceModule = 'overview' | 'compare' | 'agent'

export interface WorkspaceModuleMeta {
  title: string
  subtitle: string
}

export const DEFAULT_FUND_CODE = '000001'
export const DEFAULT_AGENT_QUESTION = '分析一下这只基金的历史表现和风险点'
export const DEFAULT_COMPARE_CODES = ['000001', '110022', '161725'] as const
export const MAX_COMPARE_FUNDS = 6

export const WORKSPACE_MODULE_META: Readonly<Record<WorkspaceModule, WorkspaceModuleMeta>> = {
  overview: {
    title: '基金概览',
    subtitle: '查看基金详情、净值走势、核心指标和风险提示',
  },
  compare: {
    title: '多基金对比',
    subtitle: '按收益、回撤、波动、基金经理和交易状态横向比较',
  },
  agent: {
    title: 'Agent 分析',
    subtitle: '查看工作流阶段、结构化报告和历史任务',
  },
}

export const AGENT_THINKING_MODE_OPTIONS: Array<{
  label: string
  value: AgentThinkingMode
}> = [
  { label: '快速思考', value: 'FAST' },
  { label: '适中思考', value: 'BALANCED' },
  { label: '仔细思考', value: 'DEEP' },
]

export function isWorkspaceModule(value: string): value is WorkspaceModule {
  return value === 'overview' || value === 'compare' || value === 'agent'
}
