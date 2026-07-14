import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fundApi } from '@/api/fundApi'
import { DEFAULT_COMPARE_CODES, DEFAULT_FUND_CODE } from '@/constants/workbench'
import type {
  FundAnalysisResult,
  FundCompareResult,
  FundDetail,
  FundNavPoint,
  FundSearchItem,
} from '@/types'
import { getErrorMessage, isSixDigitFundCode, parseFundCodes } from '@/utils/fundFormatters'

interface FundWorkbenchLoadingState {
  search: boolean
  detail: boolean
  sync: boolean
  compare: boolean
}

export function useFundWorkbench() {
  const searchKeyword = ref(DEFAULT_FUND_CODE)
  const selectedFundCode = ref(DEFAULT_FUND_CODE)
  const fundOptions = ref<FundSearchItem[]>([])
  const alipayFundPool = ref<FundSearchItem[]>([])
  const detail = ref<FundDetail | null>(null)
  const navPoints = ref<FundNavPoint[]>([])
  const analysis = ref<FundAnalysisResult | null>(null)
  const comparison = ref<FundCompareResult | null>(null)
  const compareInput = ref(DEFAULT_COMPARE_CODES.join(', '))
  const loading = reactive<FundWorkbenchLoadingState>({
    search: false,
    detail: false,
    sync: false,
    compare: false,
  })

  let fundRequestVersion = 0

  async function initializeWorkbench(): Promise<void> {
    await Promise.all([loadAlipayFundPool(), searchFunds(false)])

    const poolCodes = alipayFundPool.value.map((fund) => fund.fundCode).slice(0, 3)
    if (poolCodes.length > 0) {
      compareInput.value = poolCodes.join(', ')
    }

    const firstFundCode = alipayFundPool.value[0]?.fundCode ?? DEFAULT_FUND_CODE
    await selectFund(firstFundCode)
    await runFundComparison(false)
  }

  async function loadAlipayFundPool(): Promise<void> {
    try {
      alipayFundPool.value = await fundApi.alipayFundPool()
    } catch (error) {
      ElMessage.warning(getErrorMessage(error, '支付宝基金池加载失败'))
    }
  }

  async function searchFunds(autoSelectFirst = true): Promise<void> {
    loading.search = true
    try {
      const results = await fundApi.search(searchKeyword.value.trim())
      fundOptions.value = results
      if (autoSelectFirst && results.length > 0) {
        await selectFund(results[0].fundCode)
      }
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '基金搜索失败'))
    } finally {
      loading.search = false
    }
  }

  async function selectFund(fundCode: string): Promise<void> {
    selectedFundCode.value = fundCode
    await loadFundData(fundCode)
  }

  async function loadFundData(fundCode: string): Promise<void> {
    const requestVersion = ++fundRequestVersion
    loading.detail = true

    try {
      const [detailResult, navResult, analysisResult] = await Promise.all([
        fundApi.detail(fundCode),
        fundApi.nav(fundCode),
        fundApi.analysis(fundCode),
      ])

      if (requestVersion !== fundRequestVersion) {
        return
      }

      detail.value = detailResult
      navPoints.value = navResult
      analysis.value = analysisResult
    } catch (error) {
      if (requestVersion === fundRequestVersion) {
        ElMessage.error(getErrorMessage(error, '基金数据加载失败'))
      }
    } finally {
      if (requestVersion === fundRequestVersion) {
        loading.detail = false
      }
    }
  }

  async function syncCurrentFund(): Promise<void> {
    loading.sync = true
    try {
      await fundApi.sync(selectedFundCode.value)
      await loadFundData(selectedFundCode.value)
      ElMessage.success('基金数据已同步')
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '基金同步失败'))
    } finally {
      loading.sync = false
    }
  }

  async function syncSearchKeywordFund(): Promise<void> {
    const fundCode = searchKeyword.value.trim()
    if (!isSixDigitFundCode(fundCode)) {
      ElMessage.warning('请输入6位基金代码')
      return
    }

    loading.sync = true
    try {
      await fundApi.sync(fundCode)
      await searchFunds(false)
      await selectFund(fundCode)
      ElMessage.success('已按基金代码同步东方财富数据')
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '基金同步失败'))
    } finally {
      loading.sync = false
    }
  }

  async function runFundComparison(showSuccessMessage = true): Promise<void> {
    const fundCodes = parseFundCodes(compareInput.value)
    if (fundCodes.length === 0) {
      ElMessage.warning('请输入至少一个基金代码')
      return
    }

    loading.compare = true
    try {
      comparison.value = await fundApi.compare(fundCodes)
      if (showSuccessMessage) {
        ElMessage.success('基金对比已更新')
      }
    } catch (error) {
      ElMessage.error(getErrorMessage(error, '基金对比失败'))
    } finally {
      loading.compare = false
    }
  }

  function updateAnalysis(nextAnalysis: FundAnalysisResult): void {
    analysis.value = nextAnalysis
  }

  return {
    alipayFundPool,
    analysis,
    compareInput,
    comparison,
    detail,
    fundOptions,
    loading,
    navPoints,
    searchKeyword,
    selectedFundCode,
    initializeWorkbench,
    runFundComparison,
    searchFunds,
    selectFund,
    syncCurrentFund,
    syncSearchKeywordFund,
    updateAnalysis,
  }
}
