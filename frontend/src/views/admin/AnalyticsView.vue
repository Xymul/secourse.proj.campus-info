<template>
  <div>
    <section class="card">
      <div class="card-title">
        <span>互动数据概览</span>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
      <div class="grid grid-3">
        <div v-for="item in interactionCards" :key="item.label" class="stat-card">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
        </div>
      </div>
    </section>

    <div class="grid grid-2">
      <section class="card">
        <div class="card-title"><span>分类分布（已发布）</span></div>
        <div v-for="item in data.categoryDistribution" :key="item.name" class="dist-row">
          <span class="dist-name">{{ item.name }}</span>
          <div class="bar"><span :style="{ width: percent(item.count, maxCategory) }" /></div>
          <span class="muted small">{{ item.count }}</span>
        </div>
      </section>

      <section class="card">
        <div class="card-title"><span>信息状态分布</span></div>
        <div v-for="item in data.statusDistribution" :key="item.name" class="dist-row">
          <span class="dist-name">{{ statusLabel(item.name) }}</span>
          <div class="bar"><span :style="{ width: percent(item.count, maxStatus) }" /></div>
          <span class="muted small">{{ item.count }}</span>
        </div>
      </section>

      <section class="card">
        <div class="card-title"><span>近 14 天新增趋势</span></div>
        <div v-for="item in data.dailyTrend" :key="item.date" class="dist-row">
          <span class="dist-name">{{ item.date.slice(5) }}</span>
          <div class="bar"><span :style="{ width: percent(item.count, maxTrend) }" /></div>
          <span class="muted small">{{ item.count }}</span>
        </div>
      </section>

      <section class="card">
        <div class="card-title"><span>热门标签（按近 30 天有效信息统计）</span></div>
        <div class="row">
          <span v-for="item in data.hotKeywords" :key="item.keyword" class="chip">
            {{ item.keyword }}（{{ item.count }}）
          </span>
        </div>
        <div v-if="!data.hotKeywords.length" class="empty">暂无标签数据</div>
      </section>

      <section class="card">
        <div class="card-title"><span>校区分布</span></div>
        <div v-for="item in data.schoolDistribution" :key="item.name" class="dist-row">
          <span class="dist-name">{{ item.name }}</span>
          <div class="bar"><span :style="{ width: percent(item.count, maxSchool) }" /></div>
          <span class="muted small">{{ item.count }}</span>
        </div>
      </section>

      <section class="card">
        <div class="card-title"><span>信息最活跃的地点</span></div>
        <div v-for="item in data.topLocations" :key="item.name" class="dist-row">
          <span class="dist-name">{{ item.name }}</span>
          <div class="bar"><span :style="{ width: percent(item.count, maxLocation) }" /></div>
          <span class="muted small">{{ item.count }}</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'
import useStore from '../../store'
import { statusLabel } from '../../utils/format'

const store = useStore()
const data = reactive({
  categoryDistribution: [], schoolDistribution: [], dailyTrend: [], topLocations: [],
  statusDistribution: [], interactionSummary: {}, hotKeywords: []
})

const interactionCards = computed(() => [
  { label: '总浏览量', value: data.interactionSummary.viewCount ?? 0 },
  { label: '总评论数', value: data.interactionSummary.commentCount ?? 0 },
  { label: '总点赞数', value: data.interactionSummary.likeCount ?? 0 },
  { label: '证实次数', value: data.interactionSummary.verifyTrueCount ?? 0 },
  { label: '证伪次数', value: data.interactionSummary.verifyFalseCount ?? 0 },
  { label: 'AI 辅助发布数', value: data.interactionSummary.aiAssistedPosts ?? 0 }
])

const maxCategory = computed(() => Math.max(1, ...data.categoryDistribution.map((item) => item.count)))
const maxStatus = computed(() => Math.max(1, ...data.statusDistribution.map((item) => item.count)))
const maxTrend = computed(() => Math.max(1, ...data.dailyTrend.map((item) => item.count)))
const maxSchool = computed(() => Math.max(1, ...data.schoolDistribution.map((item) => item.count)))
const maxLocation = computed(() => Math.max(1, ...data.topLocations.map((item) => item.count)))

const percent = (value, max) => `${Math.round((value / max) * 100)}%`

const load = async () => {
  try {
    const result = await adminApi.analytics(store.state.currentSchoolId)
    Object.assign(data, result)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<style scoped>
.stat-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
}

.dist-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) 50px;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.dist-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
