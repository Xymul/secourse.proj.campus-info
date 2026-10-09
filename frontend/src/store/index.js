import { computed, reactive } from 'vue'
import { authApi, metaApi } from '../api'

/**
 * 轻量全局状态（无需引入 Pinia）：
 * 登录态、当前学校（多租户切换）、分类字典。
 */
const state = reactive({
  token: localStorage.getItem('campus_token') || '',
  user: JSON.parse(localStorage.getItem('campus_user') || 'null'),
  schools: [],
  categories: [],
  currentSchoolId: Number(localStorage.getItem('campus_school') || 0)
})

const persistUser = () => {
  if (state.token) {
    localStorage.setItem('campus_token', state.token)
  } else {
    localStorage.removeItem('campus_token')
  }
  if (state.user) {
    localStorage.setItem('campus_user', JSON.stringify(state.user))
  } else {
    localStorage.removeItem('campus_user')
  }
}

export const useStore = () => ({
  state,
  isLogin: computed(() => Boolean(state.token && state.user)),
  isAdmin: computed(() => Boolean(state.user && state.user.role === 'ADMIN')),
  currentSchool: computed(() => state.schools.find((item) => item.schoolId === state.currentSchoolId) || null),
  /** 当前是否为测试数据校区（演示数据校区，前端会显示「测试数据」标记） */
  isTestSchool: computed(() => Boolean(
    state.schools.find((item) => item.schoolId === state.currentSchoolId)?.isTestData
  )),

  async loadMeta() {
    const [schools, categories] = await Promise.all([metaApi.schools(), metaApi.categories()])
    state.schools = schools || []
    state.categories = categories || []
    if (!state.schools.length) {
      return
    }
    const preferred = state.schools.find((item) => !item.isTestData) || state.schools[0]
    const known = state.schools.some((item) => item.schoolId === state.currentSchoolId)
    // 新版校区结构（新增华农正式校区）上线后，对老浏览器中保存的校区做一次性重置为正式校区
    const migrated = localStorage.getItem('campus_school_default_v2')
    if (!known || !migrated) {
      this.setSchool(preferred.schoolId)
    }
    localStorage.setItem('campus_school_default_v2', '1')
  },

  setSchool(schoolId) {
    state.currentSchoolId = Number(schoolId)
    localStorage.setItem('campus_school', String(state.currentSchoolId))
  },

  async login(payload) {
    const result = await authApi.login(payload)
    state.token = result.token
    state.user = result.user
    if (result.user && result.user.schoolId) {
      this.setSchool(result.user.schoolId)
    }
    persistUser()
    return result.user
  },

  async register(payload) {
    const result = await authApi.register(payload)
    state.token = result.token
    state.user = result.user
    persistUser()
    return result.user
  },

  async refreshProfile() {
    if (!state.token) {
      return null
    }
    try {
      state.user = await authApi.me()
      persistUser()
      return state.user
    } catch (error) {
      this.clear()
      return null
    }
  },

  async logout() {
    try {
      await authApi.logout()
    } catch (error) {
      // 忽略退出失败
    }
    this.clear()
  },

  clear() {
    state.token = ''
    state.user = null
    persistUser()
  },

  categoryById(categoryId) {
    return state.categories.find((item) => item.categoryId === Number(categoryId)) || null
  },

  categoryByCode(code) {
    return state.categories.find((item) => item.code === code) || null
  }
})

export default useStore
