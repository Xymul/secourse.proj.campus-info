<template>
  <div class="page">
    <section class="hero card">
      <div>
        <div class="row">
          <h2>把校园里的信息，钉在地图上</h2>
          <el-tag v-if="store.isTestSchool.value" type="warning" effect="plain" size="small">
            当前为测试数据校区（模拟数据仅供演示）
          </el-tag>
          <el-tag v-else type="success" effect="plain" size="small">正式校区</el-tag>
        </div>
        <p class="muted">
          分享吐槽 · 组队交友 · 二手交易 · 失物招领 · 校园活动 —— 用地图组织信息、用搜索快速找到、
          用协同验证判断真假、用有效期自动治理。
        </p>
        <div class="row" style="margin-top: 12px">
          <el-input
            v-model="quickKeyword"
            placeholder="试试：图书馆丢的钥匙 / 附近1公里的二手台灯 / 今天的校园活动"
            style="max-width: 460px"
            clearable
            @keyup.enter="quickSearch"
          />
          <el-button type="primary" @click="quickSearch">搜索</el-button>
          <el-button @click="$router.push('/map')">在地图上看</el-button>
        </div>
        <div class="row" style="margin-top: 10px">
          <span class="small muted">热门搜索：</span>
          <span v-for="item in hotKeywords" :key="item.keyword" class="chip" @click="searchKeyword(item.keyword)">
            {{ item.keyword }}
          </span>
        </div>
      </div>
      <div class="hero-stats">
        <div v-for="stat in stats" :key="stat.label" class="hero-stat">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </section>

    <section class="card">
      <div class="card-title">
        <span>按分类查看（{{ schoolName }}）</span>
        <router-link to="/map">查看地图 →</router-link>
      </div>
      <div class="grid grid-3">
        <div v-for="category in store.state.categories" :key="category.categoryId" class="category-card"
             @click="goCategory(category)">
          <div class="cat-head">
            <span class="cat-dot" :style="{ background: category.color }" />
            <b>{{ category.name }}</b>
            <span class="muted small">{{ category.postCount || 0 }} 条</span>
          </div>
          <div class="muted small">{{ category.description }}</div>
          <div class="muted small">默认有效期 {{ category.baseValidDays }} 天</div>
        </div>
      </div>
    </section>

    <div class="grid grid-2">
      <section class="card">
        <div class="card-title">
          <span>最新信息</span>
          <router-link to="/search">更多 →</router-link>
        </div>
        <PostCard v-for="post in latest" :key="post.postId" :post="post" class="mb" />
        <div v-if="!latest.length" class="empty">暂无信息，快去发布第一条吧</div>
      </section>

      <section class="card">
        <div class="card-title">
          <span>热门信息</span>
          <span class="muted small">按热度排序</span>
        </div>
        <PostCard v-for="post in hot" :key="post.postId" :post="post" class="mb" />
        <div v-if="!hot.length" class="empty">暂无数据</div>
      </section>
    </div>

    <section class="card">
      <div class="card-title"><span>系统能力一览</span></div>
      <div class="grid grid-3">
        <div class="feature">
          <b>🗺️ 地图驱动</b>
          <p class="muted small">视野内加载、分类筛选、同地点聚合、周边 X 公里检索</p>
        </div>
        <div class="feature">
          <b>🔎 AI 智能搜索</b>
          <p class="muted small">{{ engineNote }}</p>
        </div>
        <div class="feature">
          <b>🤝 协同验证</b>
          <p class="muted small">证实 / 证伪状态互斥，可信度百分比展示，高频证伪自动预警</p>
        </div>
        <div class="feature">
          <b>⏳ 生命周期治理</b>
          <p class="muted small">有效期到期自动过期，任务记录与操作日志全程留痕</p>
        </div>
        <div class="feature">
          <b>🏫 多租户</b>
          <p class="muted small">按学校隔离信息与地点，切换校区数据互不影响</p>
        </div>
        <div class="feature">
          <b>🛡️ 后台运营</b>
          <p class="muted small">内容审核、举报处理、用户管理、地点核验、数据分析</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PostCard from '../components/PostCard.vue'
import { metaApi, postApi } from '../api'
import useStore from '../store'

const store = useStore()
const router = useRouter()
const route = useRoute()
const latest = ref([])
const hot = ref([])
const hotKeywords = ref([])
const quickKeyword = ref('')
const engineNote = ref('中文分词 + 意图解析 + 相关性排序 + 匹配原因解释（自实现算法，可替换为大模型）')

const stats = ref([
  { label: '本校区信息', value: '-' },
  { label: '今日新增', value: '-' },
  { label: '校园地点', value: '-' }
])

const schoolName = computed(() => (store.currentSchool.value ? store.currentSchool.value.name : '全部校区'))
const isToday = (value) => value && new Date(value).toDateString() === new Date().toDateString()

const loadData = async () => {
  const schoolId = store.state.currentSchoolId || undefined
  const [latestPage, hotList, keywords, locations] = await Promise.all([
    postApi.list({ schoolId, activeOnly: true, page: 1, size: 6, sort: 'latest' }),
    postApi.hot({ schoolId, limit: 6 }),
    metaApi.hotKeywords(schoolId, 6),
    metaApi.locations({ schoolId })
  ])
  latest.value = latestPage.list || []
  hot.value = hotList || []
  hotKeywords.value = keywords || []
  stats.value = [
    { label: '本校区信息', value: latestPage.total ?? '-' },
    { label: '今日新增', value: (latestPage.list || []).filter((item) => isToday(item.createdAt)).length },
    { label: '校园地点', value: (locations || []).length }
  ]
}

const quickSearch = () => {
  if (!quickKeyword.value.trim()) return
  router.push({ path: '/search', query: { q: quickKeyword.value, mode: 'ai' } })
}

const searchKeyword = (keyword) => {
  quickKeyword.value = keyword
  router.push({ path: '/search', query: { q: keyword, mode: 'keyword' } })
}

const goCategory = (category) => {
  router.push({ path: '/map', query: { categoryId: category.categoryId } })
}

onMounted(async () => {
  try {
    await loadData()
    const engine = await metaApi.searchEngine()
    engineNote.value = `${engine.note}（${engine.features.join('、')}）`
  } catch (error) {
    // 首屏数据加载失败时静默处理，页面仍可继续导航
  }
})

watch(() => store.state.currentSchoolId, loadData)
watch(() => route.query.t, loadData)
</script>

<style scoped>
.hero {
  display: flex;
  gap: 24px;
  justify-content: space-between;
  flex-wrap: wrap;
  background: linear-gradient(135deg, #ffffff 0%, #eef6f3 100%);
}

.hero h2 {
  margin: 0 0 8px;
}

.hero-stats {
  display: flex;
  gap: 24px;
  align-items: center;
}

.hero-stat {
  text-align: center;
  min-width: 96px;
}

.category-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.category-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow);
}

.cat-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.cat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.feature {
  border: 1px dashed var(--border);
  border-radius: var(--radius);
  padding: 12px 14px;
}

.feature p {
  margin: 4px 0 0;
}

.mb {
  margin-bottom: 10px;
  display: block;
}
</style>
