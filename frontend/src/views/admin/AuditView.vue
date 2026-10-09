<template>
  <div>
    <section class="card">
      <div class="card-title">
        <span>内容审核</span>
        <div class="row">
          <el-select v-model="filters.status" size="small" style="width: 150px" @change="load">
            <el-option label="全部状态" value="" />
            <el-option label="待审核" value="PENDING" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已下架" value="REMOVED" />
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="未通过" value="REJECTED" />
          </el-select>
          <el-input v-model="filters.keyword" size="small" placeholder="标题/正文关键词" style="width: 200px"
                    clearable @keyup.enter="load" />
          <el-button size="small" type="primary" @click="load">查询</el-button>
        </div>
      </div>

      <el-table :data="list" style="width: 100%" @row-click="(row) => $router.push(`/posts/${row.postId}`)">
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="authorNickname" label="发布者" width="110" />
        <el-table-column prop="locationName" label="地点" width="120" />
        <el-table-column label="验证" width="110">
          <template #default="{ row }">
            ✅{{ row.verifyTrueCount || 0 }} / ❌{{ row.verifyFalseCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="160">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click.stop="audit(row, 'APPROVE')">通过</el-button>
            <el-button size="small" type="warning" @click.stop="audit(row, 'REJECT')">驳回</el-button>
            <el-button size="small" type="danger" @click.stop="audit(row, 'REMOVE')">下架</el-button>
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

    <section class="card">
      <div class="card-title">
        <span>高频证伪 / 多次举报关注列表</span>
        <span class="muted small">证伪达到阈值自动进入预警</span>
      </div>
      <el-table :data="flagged" size="small">
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="100" />
        <el-table-column prop="verifyTrueCount" label="证实" width="80" />
        <el-table-column prop="verifyFalseCount" label="证伪" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/posts/${row.postId}`)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!flagged.length" class="empty">暂无异常信息</div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'
import useStore from '../../store'
import { formatTime, statusLabel, statusType } from '../../utils/format'

const store = useStore()
const list = ref([])
const total = ref(0)
const flagged = ref([])
const filters = reactive({ status: 'PENDING', keyword: '', page: 1, size: 10 })

const load = async () => {
  try {
    const result = await adminApi.posts({
      schoolId: store.state.currentSchoolId,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined,
      page: filters.page,
      size: filters.size,
      sort: 'latest'
    })
    list.value = result.list || []
    total.value = result.total || 0
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const loadFlagged = async () => {
  flagged.value = await adminApi.flagged(10).catch(() => [])
}

const onPageChange = (value) => {
  filters.page = value
  load()
}

const audit = async (row, action) => {
  try {
    const input = await ElMessageBox.prompt('请填写审核备注（可留空）', '内容审核', {
      confirmButtonText: '确认', cancelButtonText: '取消', inputValue: ''
    })
    await adminApi.audit(row.postId, { action, remark: input.value || '' })
    ElMessage.success('审核处理完成')
    await load()
    await loadFlagged()
  } catch (error) {
    if (error && error.message && error.message !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

onMounted(async () => {
  await load()
  await loadFlagged()
})
</script>
