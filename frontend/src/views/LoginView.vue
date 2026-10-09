<template>
  <div class="page auth-page">
    <section class="card auth-card">
      <h2>登录此刻校园</h2>
      <p class="muted small">登录后可以发布信息、评论、协同验证与举报</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" @keyup.enter="submit" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码"
                    @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="submit">登录</el-button>
      </el-form>

      <div class="row" style="margin-top: 12px; justify-content: space-between">
        <router-link to="/register">还没有账号？去注册</router-link>
        <span class="muted small">演示账号：student1 / 123456</span>
      </div>

      <el-divider content-position="left">演示账号</el-divider>
      <div class="row">
        <span class="chip" @click="fill('student1', '123456')">student1 / 123456（普通用户）</span>
        <span class="chip" @click="fill('admin', 'admin123')">admin / admin123（管理员）</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import useStore from '../store'

const store = useStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const fill = (username, password) => {
  form.username = username
  form.password = password
}

const submit = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名与密码')
    return
  }
  loading.value = true
  try {
    const user = await store.login(form)
    ElMessage.success(`欢迎回来，${user.nickname}`)
    router.push(route.query.redirect || (user.role === 'ADMIN' ? '/admin' : '/'))
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
  max-width: 420px;
  margin-top: 40px;
}
</style>
