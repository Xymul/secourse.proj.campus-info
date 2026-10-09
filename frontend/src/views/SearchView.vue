<template>
  <div class="page">
    <section class="card">
      <div class="row" style="justify-content: space-between">
        <b>信息搜索</b>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button label="keyword">普通搜索</el-radio-button>
          <el-radio-button label="ai">AI 智能搜索</el-radio-button>
        </el-radio-group>
      </div>

      <div class="row" style="margin-top: 12px">
        <el-input
          v-model="keyword"
          :placeholder="mode === 'ai'
            ? '用自然语言描述需求，例如：图书馆丢的钥匙 / 附近1公里的二手台灯 预算300元'
            : '输入关键词，例如：校园卡'"
          clearable
          @keyup.enter="doSearch"
        />
        <el-button type="primary" :loading="loading" @click="doSearch">搜索</el-button>
        <el-button v-if="mode === 'ai'" @click="useMyLocation">
          {{ hasLocation ? '已获取定位' : '带上我的位置' }}
        </el-button>
      </div>

      <div class="row" style="margin-top: 10px">
        <span class="small muted">热门：</span>
        <span v-for="item in hotKeywords" :key="item.keyword" class="chip" @click="quick(item.keyword)">
          {{ item.keyword }}
        </span>
      </div>

      <div v-if="mode === 'ai'" class="ai-note small muted">
        🤖 当前 AI 搜索由自实现算法提供：中文分词 → 意图解析（分类/地点/时间/价格/距离）→ 相关性排序 → 匹配原因解释；
        识别不到条件或没有结果时会自动放宽并给出说明。
      </div>
    </section>

    <section v-if="aiResult" class="card">
      <div class="card-title">
        <span>意图解析结果</span>
        <el-tag :type="aiResult.fallback ? 'warning' : 'success'" size="small">
          {{ aiResult.fallback ? '已降级为热门推荐' : '检索完成' }}
        </el-tag>
      </div>
      <div class="row">
        <span v-if="intent.categoryName" class="tag">分类：{{ intent.categoryName }}</span>
        <span v-if="intent.locationName" class="tag">地点：{{ intent.locationName }}</span>
        <span v-if="intent.timeLabel" class="tag">时间：{{ intent.timeLabel }}</span>
        <span v-if="intent.nearby" class="tag">距离：{{ intent.radiusKm }} 公里内</span>
        <span v-if="intent.maxPrice" class="tag">预算：≤ {{ intent.maxPrice }} 元</span>
        <span v-for="word in intent.keywords" :key="word" class="tag">关键词：{{ word }}</span>
      </div>
      <p class="muted small" style="margin: 10px 0 0">{{ aiResult.explanation }}</p>
      <div v-if="aiResult.relaxedConditions && aiResult.relaxedConditions.length" class="row small">
        <span v-for="item in aiResult.relaxedConditions" :key="item" class="reason">{{ item }}</span>
      </div>
    </section>

    <section class="card">
      <div class="card-title">
        <span>搜索结果</span>
        <span class="muted small">共 {{ total }} 条</span>
      </div>
      <div class="grid grid-2">
        <PostCard v-for="post in results" :key="post.postId" :post="post" />
      </div>
      <div v-if="!loading && !results.length" class="empty">
        没有找到匹配的信息，可以换个说法，或到地图上按地点浏览
      </div>
      <el-pagination
        v-if="mode === 'keyword' && total > size"
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        style="margin-top: 14px; justify-content: center"
        @current-change="onPageChange"
      />
    </section>
  </div>
</template>


<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PostCard from '../components/PostCard.vue'
import { metaApi, searchApi } from '../api'
import useStore from '../store'

const store = useStore()
const route = useRoute()

const mode = ref(route.query.mode === 'keyword' ? 'keyword' : 'ai')
const keyword = ref(route.query.q || '')
const results = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(12)
const loading = ref(false)
const aiResult = ref(null)
const intent = ref({})
const hotKeywords = ref([])
const hasLocation = ref(false)
const position = ref({ lat: null, lng: null })

const doSearch = async () => {
  if (!keyword.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  loading.value = true
  const schoolId = store.state.currentSchoolId
  try {
    if (mode.value === 'ai') {
      const data = await searchApi.ai({
        q: keyword.value,
        schoolId,
        lat: position.value.lat || undefined,
        lng: position.value.lng || undefined,
        limit: 20
      })
      aiResult.value = data
      intent.value = data.intent || {}
      results.value = data.list || []
      total.value = data.total || 0
    } else {
      const data = await searchApi.keyword({
        q: keyword.value, schoolId, page: page.value, size: size.value
      })
      aiResult.value = null
      results.value = data.list || []
      total.value = data.total || 0
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

const quick = (text) => {
  keyword.value = text
  page.value = 1
  doSearch()
}

const onPageChange = (value) => {
  page.value = value
  doSearch()
}

const useMyLocation = () => {
  if (!navigator.geolocation) {
    ElMessage.warning('浏览器不支持定位')
    return
  }
  navigator.geolocation.getCurrentPosition(
    (p) => {
      position.value = { lat: p.coords.latitude, lng: p.coords.longitude }
      hasLocation.value = true
      ElMessage.success('已获取当前位置，AI 搜索将优先返回附近信息')
      doSearch()
    },
    () => ElMessage.warning('定位失败，可继续按关键词搜索')
  )
}

onMounted(async () => {
  hotKeywords.value = await metaApi.hotKeywords(store.state.currentSchoolId, 6).catch(() => [])
  if (keyword.value) {
    doSearch()
  }
})

watch(() => store.state.currentSchoolId, () => {
  if (keyword.value) doSearch()
})

watch(() => route.query.q, (value) => {
  if (value !== undefined) {
    keyword.value = value
    mode.value = route.query.mode === 'keyword' ? 'keyword' : 'ai'
    page.value = 1
    doSearch()
  }
})
</script>

<style scoped>
.ai-note {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--brand-light);
  border-radius: 8px;
}
</style>
