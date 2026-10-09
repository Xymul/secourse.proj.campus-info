<template>
  <div class="app">
    <header class="app-header">
      <div class="header-inner">
        <div class="brand" @click="$router.push('/')">
          <span class="logo">📍</span>
          <div>
            <div class="brand-name">此刻校园</div>
            <div class="brand-sub small">校园信息共享与生活地图</div>
          </div>
        </div>

        <nav class="nav">
          <router-link to="/" :class="{ active: $route.name === 'home' }">首页</router-link>
          <router-link to="/map" :class="{ active: $route.name === 'map' }">校园地图</router-link>
          <router-link to="/search" :class="{ active: $route.name === 'search' }">信息搜索</router-link>
          <router-link to="/publish" :class="{ active: $route.name === 'publish' }">发布信息</router-link>
          <router-link v-if="store.isAdmin.value" to="/admin" :class="{ active: isAdminRoute }">管理后台</router-link>
        </nav>

        <div class="header-right">
          <el-select
            v-model="schoolModel"
            size="small"
            placeholder="选择校区"
            style="width: 250px"
            @change="onSchoolChange"
          >
            <el-option-group v-if="formalSchools.length" label="正式校区">
              <el-option
                v-for="school in formalSchools"
                :key="school.schoolId"
                :label="school.name"
                :value="school.schoolId"
              />
            </el-option-group>
            <el-option-group v-if="testSchools.length" label="测试数据（演示用）">
              <el-option
                v-for="school in testSchools"
                :key="school.schoolId"
                :label="`${school.name}〔测试数据〕`"
                :value="school.schoolId"
              />
            </el-option-group>
          </el-select>
          <el-tag v-if="store.isTestSchool.value" size="small" type="warning" effect="plain">测试数据</el-tag>

          <template v-if="store.isLogin.value">
            <el-dropdown @command="onUserCommand">
              <span class="user-chip">
                {{ store.state.user.nickname }}
                <el-tag v-if="store.isAdmin.value" size="small" type="success" effect="plain">管理员</el-tag>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item v-if="store.isAdmin.value" command="admin">管理后台</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button size="small" type="primary" plain @click="$router.push('/login')">登录</el-button>
            <el-button size="small" text @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main>
      <router-view />
    </main>

    <footer class="app-footer muted small">
      此刻校园 · 校园信息共享与生活地图系统 &nbsp;|&nbsp; 地图数据 © OpenStreetMap 贡献者
      &nbsp;|&nbsp; 搜索由自实现的分词与相关性算法提供（可平滑替换为大模型检索）
    </footer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import useStore from './store'

const store = useStore()
const router = useRouter()
const route = useRoute()
const schoolModel = ref(store.state.currentSchoolId)
const isAdminRoute = computed(() => route.path.startsWith('/admin'))
/** 正式校区（华农） */
const formalSchools = computed(() => store.state.schools.filter((school) => !school.isTestData))
/** 测试数据校区（演示用） */
const testSchools = computed(() => store.state.schools.filter((school) => school.isTestData))

onMounted(async () => {
  try {
    await store.loadMeta()
    schoolModel.value = store.state.currentSchoolId
  } catch (error) {
    ElMessage.error(`基础数据加载失败：${error.message}`)
  }
  if (store.isLogin.value) {
    await store.refreshProfile()
  }
})

const onSchoolChange = (schoolId) => {
  store.setSchool(schoolId)
  const school = store.state.schools.find((item) => item.schoolId === schoolId)
  ElMessage.success(school && school.isTestData
    ? `已切换到「${school.name}」〔测试数据校区：信息由模拟数据生成，仅供演示〕`
    : `已切换到「${school ? school.name : '所选校区'}」，地图与搜索结果将按该校区展示`)
  if (route.name === 'map' || route.name === 'home') {
    router.replace({ path: route.path, query: { ...route.query, school: schoolId, t: Date.now() } })
  }
}

const onUserCommand = async (command) => {
  if (command === 'profile') {
    router.push('/me')
  } else if (command === 'admin') {
    router.push('/admin')
  } else if (command === 'logout') {
    await store.logout()
    ElMessage.success('已退出登录')
    router.push('/')
  }
}
</script>

<style scoped>
.app-header {
  background: #fff;
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: 0;
  z-index: 20;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.logo {
  font-size: 24px;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--brand);
}

.brand-sub {
  color: var(--text-soft);
}

.nav {
  display: flex;
  gap: 6px;
  flex: 1;
}

.nav a {
  padding: 6px 12px;
  border-radius: 8px;
  color: var(--text);
  font-size: 14px;
}

.nav a:hover {
  background: var(--brand-light);
}

.nav a.active {
  background: var(--brand);
  color: #fff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  outline: none;
}

.app-footer {
  text-align: center;
  padding: 18px 12px 28px;
}
</style>
