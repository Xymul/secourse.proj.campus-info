<template>
  <section class="card">
    <div class="card-title">
      <span>分类管理</span>
      <el-button size="small" type="success" plain @click="openForm(null)">新增分类</el-button>
    </div>

    <el-table :data="list" style="width: 100%">
      <el-table-column prop="code" label="编码" width="130" />
      <el-table-column prop="name" label="名称" width="130">
        <template #default="{ row }">
          <span class="dot" :style="{ background: row.color }" /> {{ row.name }}
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column prop="baseValidDays" label="默认有效期(天)" width="130" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="postCount" label="信息数" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'">
            {{ row.status === 'ENABLED' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openForm(row)">编辑</el-button>
          <el-button size="small" :type="row.status === 'ENABLED' ? 'info' : 'success'"
                     @click="toggle(row)">{{ row.status === 'ENABLED' ? '禁用' : '启用' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.categoryId ? '编辑分类' : '新增分类'" width="480px">
      <el-form label-width="110px">
        <el-form-item label="编码">
          <el-input v-model="form.code" :disabled="Boolean(form.categoryId)" placeholder="大写英文，如 LOST_FOUND" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如 失物招领" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="默认有效期">
          <el-input-number v-model="form.baseValidDays" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-input v-model="form.color" placeholder="#9DC88D" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 180px">
            <el-option label="启用" value="ENABLED" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'
import useStore from '../../store'

const store = useStore()
const list = ref([])
const dialogVisible = ref(false)
const form = reactive({
  categoryId: null, code: '', name: '', description: '', icon: '', color: '#9DC88D',
  baseValidDays: 7, sortOrder: 0, status: 'ENABLED'
})

const load = async () => {
  try {
    list.value = await adminApi.categories()
    await store.loadMeta()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const openForm = (row) => {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, {
      categoryId: null, code: '', name: '', description: '', icon: '', color: '#9DC88D',
      baseValidDays: 7, sortOrder: list.value.length, status: 'ENABLED'
    })
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    await adminApi.saveCategory({ ...form }, form.categoryId)
    ElMessage.success('保存成功，分类配置已即时生效')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const toggle = async (row) => {
  try {
    await adminApi.saveCategory({ status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED' }, row.categoryId)
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<style scoped>
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
</style>
