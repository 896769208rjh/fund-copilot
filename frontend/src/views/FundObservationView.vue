<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ObservationCategoryTable from '@/components/observation/ObservationCategoryTable.vue'
import { fundApi } from '@/api/fundApi'
import type { FundSyncJob, ObservationBoard } from '@/types'

const emit = defineEmits<{
  openFund: [fundCode: string]
}>()

const board = ref<ObservationBoard | null>(null)
const latestJob = ref<FundSyncJob | null>(null)
const loading = ref(false)
const startingSync = ref(false)
const adminAvailable = ref(true)
let pollTimer: number | undefined

const syncing = computed(() => latestJob.value?.status === 'RUNNING')
const jobSummary = computed(() => {
  const job = latestJob.value
  if (job === null) return '尚无同步记录'
  const jobName = job.jobType === 'RANKING' ? '观察榜排名' : '基金池同步'
  if (job.status === 'RUNNING') return `${jobName}中 ${job.successCount}/${job.totalCount || '--'}`
  if (job.status === 'SUCCESS') return `最近${jobName}成功 · ${job.successCount} 只`
  if (job.status === 'PARTIAL_SUCCESS')
    return `${jobName}部分成功 · ${job.successCount} 成功 / ${job.failedCount} 失败`
  return `最近${jobName}失败，已保留原榜单`
})

async function loadBoard(): Promise<void> {
  loading.value = true
  try {
    board.value = await fundApi.observationBoard()
    try {
      latestJob.value = await fundApi.latestObservationJob()
      if (syncing.value) startPolling()
    } catch {
      adminAvailable.value = false
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '观察榜加载失败')
  } finally {
    loading.value = false
  }
}

async function startSync(): Promise<void> {
  startingSync.value = true
  try {
    latestJob.value = await fundApi.syncObservationUniverse()
    ElMessage.info(latestJob.value.status === 'RUNNING' ? '基金池同步已开始' : '同步任务未启动')
    startPolling()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '同步任务启动失败')
  } finally {
    startingSync.value = false
  }
}

function startPolling(): void {
  if (pollTimer !== undefined) return
  pollTimer = window.setInterval(async () => {
    try {
      latestJob.value = await fundApi.latestObservationJob()
      if (!syncing.value) {
        stopPolling()
        board.value = await fundApi.observationBoard()
        if (latestJob.value?.status === 'SUCCESS') {
          ElMessage.success(
            latestJob.value.jobType === 'RANKING' ? '观察榜排名已更新' : '基金池和观察榜已更新',
          )
        }
      }
    } catch {
      stopPolling()
    }
  }, 3000)
}

function stopPolling(): void {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer)
    pollTimer = undefined
  }
}

onMounted(loadBoard)
onBeforeUnmount(stopPolling)
</script>

<template>
  <section v-loading="loading" class="module-page observation-page">
    <section class="observation-toolbar">
      <div class="observation-method">
        <InfoFilled />
        <div>
          <strong>事实先于判断</strong>
          <p>{{ board?.methodology || '榜单仅使用已落库的公开净值与规模数据计算。' }}</p>
        </div>
      </div>
      <div v-if="adminAvailable" class="observation-sync">
        <div>
          <span>数据任务</span>
          <strong>{{ jobSummary }}</strong>
        </div>
        <el-button :icon="Refresh" :loading="startingSync || syncing" @click="startSync">
          同步基金池
        </el-button>
      </div>
    </section>

    <ObservationCategoryTable
      v-for="category in board?.categories || []"
      :key="category.category"
      :category="category"
      @open-fund="emit('openFund', $event)"
    />

    <footer class="observation-disclaimer">
      {{ board?.disclaimer || '历史表现不预示未来收益，榜单不构成投资建议。' }}
    </footer>
  </section>
</template>
