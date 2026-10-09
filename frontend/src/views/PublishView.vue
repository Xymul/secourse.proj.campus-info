<template>
  <div class="page">
    <section class="card">
      <div class="card-title">
        <span>发布校园信息</span>
        <el-button size="small" type="success" plain :loading="aiLoading" @click="runAiSuggest">
          🤖 AI 辅助：生成分类 / 标签 / 有效期建议
        </el-button>
      </div>

      <el-form label-width="90px" :model="form">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit
                    placeholder="一句话说明信息，例如：在图书馆捡到校园卡一张" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="form.content" type="textarea" :rows="5" maxlength="4000" show-word-limit
                    placeholder="补充时间、地点、物品特征、联系方式等信息" />
        </el-form-item>

        <el-form-item label="分类" required>
          <el-select v-model="form.categoryId" placeholder="请选择分类" style="max-width: 260px"
                     @change="onCategoryChange">
            <el-option v-for="category in store.state.categories" :key="category.categoryId"
                       :label="category.name" :value="category.categoryId">
              <span class="row">
                <span class="dot" :style="{ background: category.color }" />
                {{ category.name }}（默认 {{ category.baseValidDays }} 天）
              </span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="地点">
          <div style="width: 100%">
            <div class="row">
              <el-select v-model="form.locationId" placeholder="选择校园地点（可不选）" clearable
                         style="max-width: 260px" @change="onLocationChange">
                <el-option v-for="location in locations" :key="location.locationId"
                           :label="location.name" :value="location.locationId" />
              </el-select>
              <span class="muted small">在地图上点击可选择精确位置</span>
            </div>
            <MiniMapPicker v-model="coordinate" :center="schoolCenter" :locations="locations" style="margin-top: 8px" />
          </div>
        </el-form-item>

        <el-form-item label="有效期">
          <div class="row">
            <el-input-number v-model="form.validDays" :min="1" :max="365" />
            <span class="muted small">天后自动过期（系统定时任务会将其置为「已过期」）</span>
          </div>
        </el-form-item>

        <el-form-item label="标签">
          <div style="width: 100%">
            <div class="row">
              <el-input v-model="tagInput" placeholder="输入标签后回车，可添加多个" style="max-width: 320px"
                        @keyup.enter="addTag" />
              <el-button size="small" @click="addTag">添加标签</el-button>
            </div>
            <div class="row" style="margin-top: 6px">
              <el-tag v-for="tag in form.tags" :key="tag" closable @close="removeTag(tag)">{{ tag }}</el-tag>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="联系方式">
          <el-input v-model="form.contact" style="max-width: 320px"
                    placeholder="手机号 / 微信（页面会按权限脱敏展示）" />
        </el-form-item>

        <el-form-item label="图片链接">
          <el-input v-model="form.imageUrl" style="max-width: 420px" placeholder="可选，填写图片 URL" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">发布信息</el-button>
          <el-button @click="$router.push('/map')">取消</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-if="advice" class="card">
      <div class="card-title">
        <span>AI 辅助建议</span>
        <span class="muted small">建议仅供参考，可自由修改</span>
      </div>
      <p>{{ advice.summary }}</p>
      <div class="row">
        <span class="tag">分类：{{ advice.categoryName }}（置信度 {{ Math.round(advice.categoryConfidence * 100) }}%）</span>
        <span class="tag">有效期：{{ advice.suggestedValidDays }} 天</span>
        <span v-for="tag in advice.tags" :key="tag" class="tag">标签：{{ tag }}</span>
        <el-tag v-if="advice.riskLevel !== 'LOW'" type="danger" size="small">
          风险等级：{{ advice.riskLevel }}
        </el-tag>
      </div>
      <ul class="tips muted small">
        <li v-for="tip in advice.tips" :key="tip">{{ tip }}</li>
      </ul>
    </section>
  </div>
</template>


<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MiniMapPicker from '../components/MiniMapPicker.vue'
import { aiApi, metaApi, postApi } from '../api'
import useStore from '../store'

const store = useStore()
const router = useRouter()

const form = reactive({
  title: '',
  content: '',
  categoryId: null,
  locationId: null,
  validDays: 7,
  tags: [],
  contact: '',
  imageUrl: ''
})
const coordinate = ref({ latitude: null, longitude: null })
const locations = ref([])
const tagInput = ref('')
const advice = ref(null)
const aiLoading = ref(false)
const submitting = ref(false)

const schoolCenter = computed(() => {
  const school = store.currentSchool.value
  return school ? [school.centerLongitude, school.centerLatitude] : [120.2653, 31.4856]
})

const loadLocations = async () => {
  locations.value = await metaApi.locations({ schoolId: store.state.currentSchoolId }).catch(() => [])
}

const onLocationChange = (locationId) => {
  const location = locations.value.find((item) => item.locationId === locationId)
  if (location) {
    coordinate.value = { latitude: location.latitude, longitude: location.longitude }
  }
}

const onCategoryChange = (categoryId) => {
  const category = store.categoryById(categoryId)
  if (category) {
    form.validDays = category.baseValidDays
  }
}

const addTag = () => {
  const value = tagInput.value.trim()
  if (!value) return
  if (!form.tags.includes(value)) {
    form.tags.push(value)
  }
  tagInput.value = ''
}

const removeTag = (tag) => {
  form.tags = form.tags.filter((item) => item !== tag)
}

const runAiSuggest = async () => {
  if (!form.title.trim()) {
    ElMessage.warning('请先填写标题')
    return
  }
  aiLoading.value = true
  try {
    advice.value = await aiApi.suggest({
      title: form.title,
      content: form.content,
      schoolId: store.state.currentSchoolId,
      selectedCategoryCode: form.categoryId ? store.categoryById(form.categoryId).code : null
    })
    if (advice.value.categoryCode) {
      const category = store.categoryByCode(advice.value.categoryCode)
      if (category) form.categoryId = category.categoryId
    }
    if (advice.value.suggestedValidDays) form.validDays = advice.value.suggestedValidDays
    if (advice.value.tags && advice.value.tags.length) {
      advice.value.tags.forEach((tag) => {
        if (!form.tags.includes(tag)) form.tags.push(tag)
      })
    }
    ElMessage.success('已生成 AI 建议，可自行修改后发布')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    aiLoading.value = false
  }
}

const submit = async () => {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写标题与正文')
    return
  }
  if (!form.categoryId) {
    ElMessage.warning('请选择分类')
    return
  }
  submitting.value = true
  try {
    const detail = await postApi.publish({
      ...form,
      schoolId: store.state.currentSchoolId,
      latitude: coordinate.value.latitude,
      longitude: coordinate.value.longitude,
      aiAssisted: Boolean(advice.value)
    })
    ElMessage.success(detail.base.status === 'PENDING'
      ? '发布成功，内容已进入审核队列'
      : '发布成功，已出现在校园地图上')
    router.push(`/posts/${detail.base.postId}`)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

onMounted(loadLocations)
</script>

<style scoped>
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-right: 4px;
}

.tips {
  margin: 10px 0 0;
  padding-left: 18px;
}
</style>
