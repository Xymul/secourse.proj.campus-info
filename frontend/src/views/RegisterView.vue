<template>
  <div class="page auth-page">
    <section class="card auth-card">
      <h2>注册新账号</h2>
      <p class="muted small">注册后即可在校园地图上发布信息并与同学互动</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="3-50 位，用于登录" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="展示给其他同学的名字" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="所属学校">
          <el-select v-model="form.schoolId" placeholder="请选择学校" style="width: 100%">
            <el-option v-for="school in store.state.schools" :key="school.schoolId"
                       :label="school.name" :value="school.schoolId" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系方式（可选）">
          <el-input v-model="form.contact" placeholder="手机号 / 微信，仅在你的信息中按权限展示" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">注册并登录</el-button>
      </el-form>

      <div class="row" style="margin-top: 12px; justify-content: space-between">
        <router-link to="/login">已有账号？去登录</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import useStore from '../store'

const store = useStore()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '', schoolId: null, contact: '' })

onMounted(() => {
  form.schoolId = store.state.currentSchoolId || null
})

const submit = async () => {
  if (!form.username || !form.nickname || !form.password) {
    ElMessage.warning('请填写用户名、昵称与密码')
    return
  }
  loading.value = true
  try {
    const user = await store.register(form)
    ElMessage.success(`注册成功，欢迎加入，${user.nickname}`)
    router.push('/')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
}

.auth-card {
  width: 100%;
  max-width: 460px;
  margin-top: 30px;
}
</style>
