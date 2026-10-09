<template>
  <div>
    <section class="card">
      <div class="card-title">
        <span>运营仪表盘{{ schoolName }}</span>
        <div class="row">
          <el-radio-group v-model="scope" size="small" @change="load">
            <el-radio-button label="current">本校区</el-radio-button>
            <el-radio-button label="all">全部校区</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="grid grid-3">
        <div v-for="item in cards" :key="item.label" class="stat-card">
          <div class="stat-value">{{ item.value }}</div>
          <div class="stat-label">{{ item.label }}</div>
          <div v-if="item.tip" class="muted small">{{ item.tip }}</div>
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
        <div class="card-title"><span>近 7 天新增趋势</span></div>
        <div v-for="item in data.dailyTrend" :key="item.date" class="dist-row">
          <span class="dist-name">{{ item.date.slice(5) }}</span>
          <div class="bar"><span :style="{ width: percent(item.count, maxTrend) }" /></div>
          <span class="muted small">{{ item.count }}</span>
        </div>
      </section>
    </div>

    <div class="grid grid-2">
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

    <div class="grid grid-2">
      <section class="card">
        <div class="card-title">
          <span>最近任务</span>
          <router-link to="/admin/tasks">全部 →</router-link>
        </div>
        <el-table :data="data.recentTasks" size="small">
          <el-table-column prop="taskName" label="任务" min-width="150" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : (row.status === 'FAILED' ? 'danger' : 'warning')">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="affectedCount" label="影响" width="70" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
          </el-table-column>
        </el-table>
      </section>

      <section class="card">
        <div class="card-title">
          <span>最近操作日志</span>
          <router-link to="/admin/tasks">全部 →</router-link>
        </div>
        <el-table :data="data.recentLogs" size="small">
          <el-table-column prop="operatorName" label="操作人" width="110" />
          <el-table-column prop="action" label="动作" width="140" />
          <el-table-column prop="detail" label="详情" min-width="200" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </div>
</template>


<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'
import useStore from '../../store'
import { formatTime } from '../../utils/format'

const store = useStore()
const scope = ref('current')
const data = reactive({
  totalPosts: 0, publishedPosts: 0, pendingPosts: 0, expiredPosts: 0, removedPosts: 0, resolvedPosts: 0,
  totalUsers: 0, activeUsers: 0, totalLocations: 0, totalCategories: 0,
  pendingReports: 0, pendingClaims: 0, todayNewPosts: 0, flaggedPosts: 0,
  categoryDistribution: [], schoolDistribution: [], dailyTrend: [], topLocations: [],
  recentTasks: [], recentLogs: []
})

const schoolName = computed(() => (scope.value === 'current' && store.currentSchool.value
  ? `（${store.currentSchool.value.name}）` : '（全部校区）'))

const cards = computed(() => [
  { label: '信息总数', value: data.totalPosts, tip: `今日新增 ${data.todayNewPosts}` },
  { label: '已发布', value: data.publishedPosts, tip: `已解决 ${data.resolvedPosts}` },
  { label: '待审核', value: data.pendingPosts, tip: '含敏感词或举报触发的信息' },
  { label: '已过期', value: data.expiredPosts, tip: '由定时任务自动治理' },
  { label: '已下架', value: data.removedPosts },
  { label: '异常关注', value: data.flaggedPosts, tip: '高频证伪或被多次举报' },
  { label: '用户总数', value: data.totalUsers, tip: `正常 ${data.activeUsers}` },
  { label: '校园地点', value: data.totalLocations, tip: `分类 ${data.totalCategories} 个` },
  { label: '待处理举报', value: data.pendingReports, tip: `待确认认领 ${data.pendingClaims}` }
])

const maxCategory = computed(() => Math.max(1, ...data.categoryDistribution.map((item) => item.count)))
const maxSchool = computed(() => Math.max(1, ...data.schoolDistribution.map((item) => item.count)))
const maxTrend = computed(() => Math.max(1, ...data.dailyTrend.map((item) => item.count)))
const maxLocation = computed(() => Math.max(1, ...data.topLocations.map((item) => item.count)))

const percent = (value, max) => `${Math.round((value / max) * 100)}%`

const load = async () => {
  try {
    const schoolId = scope.value === 'current' ? store.state.currentSchoolId : undefined
    const result = await adminApi.dashboard(schoolId)
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
