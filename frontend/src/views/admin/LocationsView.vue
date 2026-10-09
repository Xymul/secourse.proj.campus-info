<template>
  <section class="card">
    <div class="card-title">
      <span>地点核验</span>
      <div class="row">
        <el-select v-model="filters.status" size="small" style="width: 140px" @change="load">
          <el-option label="全部状态" value="" />
          <el-option label="已核验" value="VERIFIED" />
          <el-option label="待核验" value="PENDING" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-input v-model="filters.keyword" size="small" placeholder="地点名称 / 区域" style="width: 180px"
                  clearable @keyup.enter="load" />
        <el-button size="small" type="primary" @click="load">查询</el-button>
        <el-button size="small" type="success" plain @click="openForm(null)">新增地点</el-button>
      </div>
    </div>

    <el-table :data="list" style="width: 100%">
      <el-table-column prop="name" label="地点" width="150" />
      <el-table-column prop="area" label="区域" width="110" />
      <el-table-column prop="schoolName" label="学校" min-width="170" show-overflow-tooltip />
      <el-table-column label="坐标" width="190">
        <template #default="{ row }">{{ row.latitude }}, {{ row.longitude }}</template>
      </el-table-column>
      <el-table-column prop="postCount" label="信息数" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="LOCATION_STATUS[row.status] ? LOCATION_STATUS[row.status].type : 'info'">
            {{ LOCATION_STATUS[row.status] ? LOCATION_STATUS[row.status].label : row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="270" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openForm(row)">编辑</el-button>
          <el-button v-if="row.status !== 'VERIFIED'" size="small" type="success"
                     @click="verify(row, true)">通过核验</el-button>
          <el-button v-if="row.status === 'VERIFIED'" size="small" type="warning" plain
                     @click="verify(row, false)">标记异常</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.locationId ? '编辑地点' : '新增地点'" width="520px">
      <el-form label-width="80px">
        <el-form-item label="学校">
          <el-select v-model="form.schoolId" style="width: 100%">
            <el-option v-for="school in store.state.schools" :key="school.schoolId"
                       :label="school.name" :value="school.schoolId" />
          </el-select>
        </el-form-item>
        <el-form-item label="地点名称">
          <el-input v-model="form.name" placeholder="例如：第三食堂" />
        </el-form-item>
        <el-form-item label="所属区域">
          <el-input v-model="form.area" placeholder="例如：生活区" />
        </el-form-item>
        <el-form-item label="纬度">
          <el-input-number v-model="form.latitude" :precision="6" :step="0.0001" style="width: 200px" />
        </el-form-item>
        <el-form-item label="经度">
          <el-input-number v-model="form.longitude" :precision="6" :step="0.0001" style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="已核验" value="VERIFIED" />
            <el-option label="待核验" value="PENDING" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'
import useStore from '../../store'
import { LOCATION_STATUS } from '../../utils/format'

const store = useStore()
const list = ref([])
const dialogVisible = ref(false)
const filters = reactive({ status: '', keyword: '' })
const form = reactive({
  locationId: null, schoolId: null, name: '', area: '', latitude: null, longitude: null,
  description: '', status: 'VERIFIED'
})

const load = async () => {
  try {
    list.value = await adminApi.locations({
      schoolId: store.state.currentSchoolId,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const openForm = (row) => {
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, {
      locationId: null, schoolId: store.state.currentSchoolId, name: '', area: '',
      latitude: null, longitude: null, description: '', status: 'PENDING'
    })
  }
  dialogVisible.value = true
}

const save = async () => {
  try {
    await adminApi.saveLocation({ ...form }, form.locationId)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const verify = async (row, approved) => {
  try {
    const input = await ElMessageBox.prompt('请填写核验备注（可留空）', '地点核验', {
      confirmButtonText: '确认', cancelButtonText: '取消', inputValue: ''
    })
    await adminApi.verifyLocation(row.locationId, approved, input.value || '')
    ElMessage.success('核验结果已记录')
    await load()
  } catch (error) {
    if (error && error.message && error.message !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

const remove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除地点「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await adminApi.deleteLocation(row.locationId)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (error && error.message && error.message !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

onMounted(load)
</script>
