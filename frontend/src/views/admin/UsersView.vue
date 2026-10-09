<template>
  <section class="card">
    <div class="card-title">
      <span>用户管理</span>
      <div class="row">
        <el-select v-model="filters.schoolId" size="small" style="width: 190px" clearable placeholder="全部校区" @change="load">
          <el-option v-for="school in store.state.schools" :key="school.schoolId"
                     :label="school.name" :value="school.schoolId" />
        </el-select>
        <el-select v-model="filters.status" size="small" style="width: 130px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已禁用" value="DISABLED" />
        </el-select>
        <el-select v-model="filters.role" size="small" style="width: 130px" @change="load">
          <el-option label="全部角色" value="" />
          <el-option label="普通用户" value="USER" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-input v-model="filters.keyword" size="small" placeholder="用户名 / 昵称" style="width: 180px"
                  clearable @keyup.enter="load" />
        <el-button size="small" type="primary" @click="load">查询</el-button>
      </div>
    </div>

    <el-table :data="list" style="width: 100%">
      <el-table-column prop="userId" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="nickname" label="昵称" width="130" />
      <el-table-column prop="schoolName" label="学校" min-width="180" show-overflow-tooltip />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.role === 'ADMIN' ? 'success' : 'info'">
            {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creditScore" label="信用分" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
            {{ row.status === 'ACTIVE' ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ACTIVE'" size="small" type="danger" plain
                     @click="changeStatus(row, 'DISABLED')">禁用</el-button>
          <el-button v-else size="small" type="success" plain
                     @click="changeStatus(row, 'ACTIVE')">启用</el-button>
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
import useStore from '../../store'

const store = useStore()
const list = ref([])
const total = ref(0)
const filters = reactive({ schoolId: null, status: '', role: '', keyword: '', page: 1, size: 10 })

const load = async () => {
  try {
    const result = await adminApi.users({
      schoolId: filters.schoolId || undefined,
      status: filters.status || undefined,
      role: filters.role || undefined,
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

const changeStatus = async (row, status) => {
  try {
    const input = await ElMessageBox.prompt(
      status === 'DISABLED' ? '请填写禁用原因' : '请填写启用说明',
      '调整用户状态',
      { confirmButtonText: '确认', cancelButtonText: '取消', inputValue: '' }
    )
    await adminApi.updateUserStatus(row.userId, { status, remark: input.value || '' })
    ElMessage.success('已更新用户状态')
    await load()
  } catch (error) {
    if (error && error.message && error.message !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

onMounted(load)
</script>
