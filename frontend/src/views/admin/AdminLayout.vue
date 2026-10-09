<template>
  <div class="admin">
    <aside class="admin-side">
      <div class="admin-brand">此刻校园 · 管理后台</div>
      <el-menu :default-active="$route.path" router class="admin-menu">
        <el-menu-item index="/admin/dashboard">📊 仪表盘</el-menu-item>
        <el-menu-item index="/admin/audit">📝 内容审核</el-menu-item>
        <el-menu-item index="/admin/reports">🚩 举报管理</el-menu-item>
        <el-menu-item index="/admin/users">👥 用户管理</el-menu-item>
        <el-menu-item index="/admin/locations">📍 地点核验</el-menu-item>
        <el-menu-item index="/admin/categories">🗂️ 分类管理</el-menu-item>
        <el-menu-item index="/admin/tasks">⏱️ 任务记录 / 日志</el-menu-item>
        <el-menu-item index="/admin/analytics">📈 数据分析</el-menu-item>
      </el-menu>
      <div class="admin-tip muted small">
        当前管理员：{{ store.state.user ? store.state.user.nickname : '-' }}
        <div><router-link to="/">← 返回用户端</router-link></div>
      </div>
    </aside>
    <section class="admin-main">
      <router-view />
    </section>
  </div>
</template>

<script setup>
import useStore from '../../store'

const store = useStore()
</script>

<style scoped>
.admin {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px;
}

.admin-side {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px;
  height: fit-content;
  position: sticky;
  top: 76px;
}

.admin-brand {
  font-weight: 700;
  color: var(--brand);
  padding: 6px 8px 12px;
}

.admin-menu {
  border: none;
}

.admin-tip {
  padding: 10px 8px 4px;
  border-top: 1px solid var(--border);
  margin-top: 8px;
}

.admin-main {
  min-width: 0;
}

@media (max-width: 900px) {
  .admin {
    grid-template-columns: 1fr;
  }

  .admin-side {
    position: static;
  }
}
</style>
