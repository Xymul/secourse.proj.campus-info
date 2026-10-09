<template>
  <div>
    <section class="card">
      <div class="card-title">
        <span>定时任务记录</span>
        <div class="row">
          <el-button size="small" type="warning" plain :loading="running" @click="triggerExpire">
            手动触发过期治理
          </el-button>
          <el-button size="small" @click="load">刷新</el-button>
        </div>
      </div>
      <el-table :data="tasks" style="width: 100%">
        <el-table-column prop="taskId" label="ID" width="70" />
        <el-table-column prop="taskName" label="任务名称" min-width="200" />
        <el-table-column prop="taskType" label="类型" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : (row.status === 'FAILED' ? 'danger' : 'warning')">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="affectedCount" label="影响条数" width="100" />
        <el-table-column prop="message" label="结果说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ formatTime(row.finishedAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        layout="total, prev, pager, next"
        :total="taskTotal"
        :page-size="page.size"
        :current-page="page.page"
        style="margin-top: 12px"
        @current-change="onPageChange"
      />
    </section>

    <section class="card">
      <div class="card-title">
        <span>操作日志</span>
        <div class="row">
          <el-input v-model="logFilters.action" size="small" placeholder="动作，如 CREATE_POST" style="width: 200px"
                    clearable @keyup.enter="loadLogs" />
          <el-input v-model="logFilters.keyword" size="small" placeholder="关键词（详情/操作人）" style="width: 200px"
                    clearable @keyup.enter="loadLogs" />
          <el-button size="small" type="primary" @click="loadLogs">查询</el-button>
        </div>
      </div>
      <el-table :data="logs" style="width: 100%">
        <el-table-column prop="logId" label="ID" width="70" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="action" label="动作" width="170" />
        <el-table-column prop="targetType" label="对象" width="100" />
        <el-table-column prop="targetId" label="对象ID" width="90" />
        <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        layout="total, prev, pager, next"
        :total="logTotal"
        :page-size="logPage.size"
        :current-page="logPage.page"
        style="margin-top: 12px"
        @current-change="onLogPageChange"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'
import { formatTime } from '../../utils/format'

const tasks = ref([])
const taskTotal = ref(0)
const page = reactive({ page: 1, size: 10 })
const logs = ref([])
const logTotal = ref(0)
const logPage = reactive({ page: 1, size: 20 })
const logFilters = reactive({ action: '', keyword: '' })
const running = ref(false)

const load = async () => {
  try {
    const result = await adminApi.tasks({ page: page.page, size: page.size })
    tasks.value = result.list || []
    taskTotal.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const loadLogs = async () => {
  try {
    const result = await adminApi.logs({
      action: logFilters.action || undefined,
      keyword: logFilters.keyword || undefined,
      page: logPage.page,
      size: logPage.size
    })
    logs.value = result.list || []
    logTotal.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const onPageChange = (value) => {
  page.page = value
  load()
}

const onLogPageChange = (value) => {
  logPage.page = value
  loadLogs()
}

const triggerExpire = async () => {
  running.value = true
  try {
    await adminApi.triggerExpire()
    ElMessage.success('过期治理任务已执行')
    await load()
    await loadLogs()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    running.value = false
  }
}

onMounted(async () => {
  await load()
  await loadLogs()
})
</script>
