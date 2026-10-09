<template>
  <section class="card">
    <div class="card-title">
      <span>举报管理</span>
      <div class="row">
        <el-select v-model="filters.status" size="small" style="width: 140px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="待处理" value="PENDING" />
          <el-option label="已处理" value="HANDLED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input v-model="filters.keyword" size="small" placeholder="信息标题 / 举报原因" style="width: 200px"
                  clearable @keyup.enter="load" />
        <el-button size="small" type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="list" style="width: 100%">
      <el-table-column prop="postTitle" label="被举报信息" min-width="200" show-overflow-tooltip />
      <el-table-column prop="reporterName" label="举报人" width="110" />
      <el-table-column prop="reason" label="原因" width="140" />
      <el-table-column prop="detail" label="补充说明" min-width="180" show-overflow-tooltip />
      <el-table-column prop="postStatus" label="信息状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.postStatus)">{{ statusLabel(row.postStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="举报状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="REPORT_STATUS[row.status] ? REPORT_STATUS[row.status].type : 'info'">
            {{ REPORT_STATUS[row.status] ? REPORT_STATUS[row.status].label : row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING'">
            <el-button size="small" type="danger" @click="handle(row, 'HANDLE')">举报成立并下架</el-button>
            <el-button size="small" @click="handle(row, 'REJECT')">驳回</el-button>
          </template>
          <span v-else class="muted small">{{ row.handleRemark || '已处理' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      layout="total, prev, pager, next"
      :total="total"
      :page-size="filters.size"
      :current-page="filters.page"
      style="margin-top: 12px"
      @current-change="onPageChange"
    />
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'
import { REPORT_STATUS, formatTime, statusLabel, statusType } from '../../utils/format'

const list = ref([])
const total = ref(0)
const filters = reactive({ status: 'PENDING', keyword: '', page: 1, size: 10 })

const load = async () => {
  try {
    const result = await adminApi.reports({
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      page: filters.page,
      size: filters.size
    })
    list.value = result.list || []
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const onPageChange = (value) => {
  filters.page = value
  load()
}

const handle = async (row, action) => {
  try {
    const input = await ElMessageBox.prompt('请填写处理说明', '举报处理', {
      confirmButtonText: '确认', cancelButtonText: '取消',
      inputValue: action === 'HANDLE' ? '举报成立，信息已下架' : '信息核实无误'
    })
    await adminApi.handleReport(row.reportId, { action, remark: input.value || '' })
    ElMessage.success('处理完成')
    await load()
  } catch (error) {
    if (error && error.message && error.message !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

onMounted(load)
</script>
