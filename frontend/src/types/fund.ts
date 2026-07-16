export interface FundSearchItem {
  fundCode: string
  fundName: string
  fundType: string | null
  riskLevel: string | null
  displayTag: string | null
}

export interface FundDetail {
  fundCode: string
  fundName: string
  fundType: string | null
  fundCompany: string | null
  fundManager: string | null
  riskLevel: string | null
  purchaseStatus: string | null
  redeemStatus: string | null
  latestNav: number | null
  latestNavDate: string | null
  sourceUrl: string | null
  stale: boolean | null
  lastSyncAt: string | null
}

export interface FundNavPoint {
  navDate: string
  unitNav: number | null
  accumulatedNav: number | null
  dailyGrowthRate: number | null
}

export interface FundMetric {
  oneMonthReturn: number | null
  threeMonthReturn: number | null
  sixMonthReturn: number | null
  oneYearReturn: number | null
  maxDrawdown: number | null
  volatility: number | null
  statisticDate: string | null
  sampleSize: number
  sampleStartDate: string | null
  sampleEndDate: string | null
  observationDays: number
  sampleBoundary: string
}

export interface FundAnalysisResult {
  detail: FundDetail
  metrics: FundMetric
  navPoints: FundNavPoint[]
  highlights: string[]
  risks: string[]
  riskNotice: string
  dataSource: string
  generatedAt: string
}

export interface FundCompareColumn {
  fundCode: string
  fundName: string
  fundType: string | null
  riskLevel: string | null
}

export interface FundCompareRow {
  dimension: string
  values: string[]
}

export interface FundCompareResult {
  columns: FundCompareColumn[]
  rows: FundCompareRow[]
  summary: string
  generatedAt: string
}
