import { createRouter, createWebHashHistory } from 'vue-router'
import useStore from '../store'

const routes = [
  { path: '/', name: 'home', component: () => import('../views/HomeView.vue'), meta: { title: '首页' } },
  { path: '/map', name: 'map', component: () => import('../views/MapView.vue'), meta: { title: '校园地图' } },
  { path: '/search', name: 'search', component: () => import('../views/SearchView.vue'), meta: { title: '信息搜索' } },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { requiresAuth: true, title: '发布信息' } },
  { path: '/posts/:id', name: 'post-detail', component: () => import('../views/PostDetailView.vue'), meta: { title: '信息详情' } },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } },
  { path: '/me', name: 'me', component: () => import('../views/ProfileView.vue'), meta: { requiresAuth: true, title: '个人中心' } },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'admin-dashboard', component: () => import('../views/admin/DashboardView.vue'), meta: { title: '仪表盘' } },
      { path: 'audit', name: 'admin-audit', component: () => import('../views/admin/AuditView.vue'), meta: { title: '内容审核' } },
      { path: 'reports', name: 'admin-reports', component: () => import('../views/admin/ReportsView.vue'), meta: { title: '举报管理' } },
      { path: 'users', name: 'admin-users', component: () => import('../views/admin/UsersView.vue'), meta: { title: '用户管理' } },
      { path: 'locations', name: 'admin-locations', component: () => import('../views/admin/LocationsView.vue'), meta: { title: '地点核验' } },
      { path: 'categories', name: 'admin-categories', component: () => import('../views/admin/CategoriesView.vue'), meta: { title: '分类管理' } },
      { path: 'tasks', name: 'admin-tasks', component: () => import('../views/admin/TasksView.vue'), meta: { title: '任务记录 / 操作日志' } },
      { path: 'analytics', name: 'admin-analytics', component: () => import('../views/admin/AnalyticsView.vue'), meta: { title: '数据分析' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const store = useStore()
  if (to.meta.requiresAuth && !store.isLogin.value) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !store.isAdmin.value) {
    return { name: 'home' }
  }
  document.title = to.meta.title ? `${to.meta.title} · 此刻校园` : '此刻校园'
  return true
})

export default router
