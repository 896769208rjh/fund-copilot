<script setup lang="ts">
import { TrendCharts } from '@element-plus/icons-vue'
import type { FundAgentReportSection } from '../../types/fund'

defineProps<{
  sections: FundAgentReportSection[]
}>()
</script>

<template>
  <section class="report-block agent-report-block">
    <div class="section-title">
      <TrendCharts class="title-icon" />
      <span>结构化报告</span>
    </div>
    <div v-if="sections.length > 0" class="report-section-list">
      <article
        v-for="section in sections"
        :key="section.id"
        class="report-section"
      >
        <header>
          <strong>{{ section.title }}</strong>
          <el-tag size="small" type="info">{{ section.sectionType }}</el-tag>
        </header>
        <p>{{ section.content }}</p>
      </article>
    </div>
    <el-empty v-else description="暂无报告分区" :image-size="76" />
  </section>
</template>
