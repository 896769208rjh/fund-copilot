export type FundCategory = 'ACTIVE_EQUITY' | 'EQUITY_HYBRID' | 'BOND' | 'INDEX'

export interface ObservationFund {
  rank: number
  fundCode: string
  fundName: string
  scaleInBillions: number | null
  oneMonthReturn: number | null
  threeMonthReturn: number | null
  sixMonthReturn: number | null
  oneYearReturn: number | null
  maxDrawdown: number | null
  volatility: number | null
  totalScore: number | null
  membershipStatus: 'NEW' | 'STABLE'
  metricDate: string
}

export interface ObservationCategory {
  category: FundCategory
  categoryName: string
  rankDate: string | null
  universeSize: number
  funds: ObservationFund[]
}

export interface ObservationBoard {
  categories: ObservationCategory[]
  methodology: string
  disclaimer: string
  generatedAt: string
}

export interface FundSyncJob {
  jobId: number
  jobType: 'FULL_SYNC' | 'RANKING'
  triggerType: string
  status: 'RUNNING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED'
  totalCount: number
  successCount: number
  failedCount: number
  startedAt: string
  completedAt: string | null
  errorMessage: string | null
}
