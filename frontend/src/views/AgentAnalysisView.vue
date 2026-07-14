<script setup lang="ts">
import { toRef, watch } from 'vue'
import { ChatLineRound, CircleClose, DataLine, Download, Refresh } from '@element-plus/icons-vue'
import AgentModelTelemetry from '@/components/agent/AgentModelTelemetry.vue'
import AgentReportSections from '@/components/agent/AgentReportSections.vue'
import AgentStageAudit from '@/components/agent/AgentStageAudit.vue'
import AgentTimeline from '@/components/agent/AgentTimeline.vue'
import AgentWorkflowGraph from '@/components/agent/AgentWorkflowGraph.vue'
import { AGENT_THINKING_MODE_OPTIONS } from '@/constants/workbench'
import { useAgentWorkflow } from '@/composables/useAgentWorkflow'
import type { FundAnalysisResult } from '@/types'
import { formatElapsed, taskStatusTagType } from '@/utils/fundFormatters'

const props = defineProps<{
  fundCode: string
}>()

const emit = defineEmits<{
  analysisUpdated: [analysis: FundAnalysisResult]
}>()

const {
  agentAnswer,
  agentGeneratedAt,
  agentQuestion,
  agentSteps,
  agentTask,
  agentThinkingMode,
  canCancelTask,
  canResumeTask,
  currentTaskLabel,
  liveStages,
  loading,
  modelCalls,
  progressMessages,
  reportSections,
  taskHistory,
  cancelCurrentTask,
  downloadCurrentReport,
  loadTaskHistory,
  replayTask,
  rerunStage,
  resumeCurrentTask,
  runAgentAnalysis,
} = useAgentWorkflow({
  fundCode: toRef(props, 'fundCode'),
  onAnalysisUpdated: (analysis) => emit('analysisUpdated', analysis),
})

watch(
  () => props.fundCode,
  (fundCode) => {
    void loadTaskHistory(fundCode)
  },
  { immediate: true },
)
</script>

<template>
  <section class="module-page agent-page">
    <section class="agent-board">
      <div class="agent-main">
        <header class="agent-header">
          <div>
            <p class="eyebrow">AgentScope</p>
            <h2>基金分析 Agent</h2>
          </div>
          <div class="agent-header-actions">
            <el-tag
              class="task-status-tag"
              :type="taskStatusTagType(agentTask?.status || 'PENDING')"
            >
              {{ currentTaskLabel }}
            </el-tag>
            <div class="agent-action-buttons">
              <el-button
                v-if="canCancelTask"
                size="small"
                type="danger"
                plain
                :icon="CircleClose"
                :loading="loading.taskControl"
                @click="cancelCurrentTask"
              >
                取消任务
              </el-button>
              <el-button
                size="small"
                :icon="Refresh"
                :disabled="!canResumeTask"
                :loading="loading.analysis || loading.taskControl"
                @click="resumeCurrentTask"
              >
                继续/重试
              </el-button>
              <el-button
                size="small"
                :icon="Download"
                :disabled="agentTask === null"
                :loading="loading.exportReport"
                @click="downloadCurrentReport"
              >
                导出报告
              </el-button>
            </div>
          </div>
        </header>

        <section class="question-block">
          <div class="thinking-mode-control">
            <span>思考强度</span>
            <el-segmented
              v-model="agentThinkingMode"
              :options="AGENT_THINKING_MODE_OPTIONS"
              :disabled="loading.analysis"
            />
          </div>
          <el-input
            v-model="agentQuestion"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 7 }"
            resize="none"
            placeholder="输入你的问题，例如：这只基金最近表现怎么样？"
          />
          <el-button
            class="full-button"
            type="primary"
            :icon="ChatLineRound"
            :loading="loading.analysis"
            @click="runAgentAnalysis"
          >
            开始分析
          </el-button>
        </section>

        <AgentWorkflowGraph :stages="liveStages" />

        <section class="answer-block">
          <div class="section-title">
            <ChatLineRound class="title-icon" />
            <span>回答</span>
          </div>
          <p v-if="agentAnswer" class="answer-text">{{ agentAnswer }}</p>
          <el-empty v-else description="点击分析后展示结果" :image-size="76" />
          <small v-if="agentGeneratedAt">生成时间：{{ agentGeneratedAt }}</small>
        </section>

        <AgentReportSections :sections="reportSections" />
      </div>

      <div class="agent-side">
        <AgentTimeline :steps="agentSteps" :progress-messages="progressMessages" />

        <AgentModelTelemetry :calls="modelCalls" />

        <section v-loading="loading.taskHistory" class="task-history-block">
          <div class="section-title">
            <DataLine class="title-icon" />
            <span>历史任务</span>
          </div>
          <button
            v-for="task in taskHistory"
            :key="task.taskId"
            class="task-row"
            :class="{ active: task.taskId === agentTask?.taskId }"
            type="button"
            @click="replayTask(task.taskId)"
          >
            <span>{{ task.taskNo }}</span>
            <small>{{ task.status }} · {{ formatElapsed(task.elapsedMs) }}</small>
          </button>
          <el-empty v-if="taskHistory.length === 0" description="暂无历史任务" :image-size="76" />
        </section>

        <AgentStageAudit
          :stages="liveStages"
          :disabled="loading.analysis || loading.taskControl"
          @rerun="rerunStage"
        />
      </div>
    </section>
  </section>
</template>
