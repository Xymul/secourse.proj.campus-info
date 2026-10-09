<template>
  <div class="page" v-if="detail">
    <section class="card">
      <div class="row" style="justify-content: space-between">
        <div class="row">
          <span class="cat-dot" :style="{ background: detail.base.categoryColor || '#8fa3b0' }" />
          <b>{{ detail.base.categoryName }}</b>
          <el-tag size="small" :type="statusType(detail.base.status)">{{ statusLabel(detail.base.status) }}</el-tag>
          <el-tag v-if="detail.aiAssisted" size="small" type="success" effect="plain">AI 辅助发布</el-tag>
          <span class="muted small">发布于 {{ formatTime(detail.base.createdAt) }}</span>
          <span class="muted small">有效期至 {{ formatTime(detail.base.expireAt) }}</span>
        </div>
        <div class="row">
          <el-button size="small" @click="toggleLike">{{ liked ? '💔 取消点赞' : '👍 点赞' }}
            ({{ detail.base.likeCount || 0 }})
          </el-button>
          <el-button size="small" @click="reportDialog.open()">🚩 举报</el-button>
          <template v-if="isOwner">
            <el-button size="small" @click="markResolved">✅ 标记已解决</el-button>
            <el-button size="small" type="danger" plain @click="removePost">下架</el-button>
          </template>
        </div>
      </div>

      <h2 class="detail-title">{{ detail.base.title }}</h2>
      <p class="detail-content">{{ detail.content }}</p>

      <div class="row muted small">
        <span>📍 {{ detail.base.locationName || '未绑定地点' }}</span>
        <span v-if="detail.base.latitude">坐标：{{ detail.base.latitude }}, {{ detail.base.longitude }}</span>
        <span>👁 {{ detail.base.viewCount || 0 }}</span>
        <span>💬 {{ detail.base.commentCount || 0 }}</span>
        <span>发布者：{{ detail.base.authorNickname }}</span>
      </div>

      <div class="row" style="margin-top: 8px">
        <el-tag v-for="tag in detail.base.tags" :key="tag" size="small">{{ tag }}</el-tag>
      </div>

      <el-alert
        v-if="detail.contact"
        :title="`联系方式：${detail.contact}${detail.contactMasked ? '（已脱敏，登录后可查看完整信息）' : ''}`"
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      />

      <div class="row" style="margin-top: 12px">
        <el-button size="small" @click="$router.push({ path: '/map', query: { categoryId: detail.base.categoryId } })">
          在地图上查看同类信息
        </el-button>
        <el-button v-if="detail.base.locationId" size="small" @click="loadCluster">查看同地点信息</el-button>
      </div>
    </section>

    <div class="grid grid-2">
      <section class="card">
        <div class="card-title">
          <span>协同验证</span>
          <span class="muted small">同一用户只能保留一条立场，可随时修改</span>
        </div>
        <VerifyPanel :post-id="detail.base.postId" :summary="verification" @updated="onVerified" />

        <div v-if="claimable" style="margin-top: 14px">
          <el-divider content-position="left">失物招领认领</el-divider>
          <div class="row">
            <el-button size="small" type="primary" @click="claimDialog.open()">提交认领申请</el-button>
            <el-button v-if="isOwner" size="small" @click="loadClaims">查看认领申请（{{ claims.length }}）</el-button>
          </div>
          <div v-for="item in claims" :key="item.claimId" class="claim-item">
            <div class="row" style="justify-content: space-between">
              <b>{{ item.nickname }}（{{ item.status }}）</b>
              <span class="muted small">{{ formatTime(item.createdAt) }}</span>
            </div>
            <div class="small">{{ item.description }}</div>
            <div class="small muted">联系方式：{{ item.contact || '-' }}</div>
            <div v-if="isOwner && item.status === 'PENDING'" class="row" style="margin-top: 6px">
              <el-button size="small" type="success" @click="handleClaim(item.claimId, 'APPROVED')">确认认领</el-button>
              <el-button size="small" @click="handleClaim(item.claimId, 'REJECTED')">驳回</el-button>
            </div>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-title">
          <span>评论（{{ comments.length }}）</span>
        </div>
        <div class="row">
          <el-input v-model="commentText" placeholder="说点什么…" maxlength="1000"
                    :disabled="!isLogin" @keyup.enter="submitComment" />
          <el-button type="primary" :disabled="!isLogin" @click="submitComment">发表</el-button>
        </div>
        <div v-if="!isLogin" class="muted small" style="margin-top: 6px">登录后即可评论、验证与举报</div>
        <div class="comment-list">
          <div v-for="item in comments" :key="item.commentId" class="comment-item">
            <div class="row" style="justify-content: space-between">
              <b>{{ item.authorNickname }}</b>
              <span class="muted small">{{ relativeTime(item.createdAt) }}</span>
            </div>
            <div>{{ item.content }}</div>
          </div>
          <div v-if="!comments.length" class="empty">还没有评论，来抢沙发吧</div>
        </div>
      </section>
    </div>

    <ReportDialog ref="reportDialog" :post-id="detail.base.postId" />
    <ClaimDialog ref="claimDialog" :post-id="detail.base.postId" @submitted="loadClaims" />
  </div>
  <div v-else class="page">
    <div class="card empty">正在加载信息…</div>
  </div>
</template>


<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import VerifyPanel from '../components/VerifyPanel.vue'
import ReportDialog from '../components/ReportDialog.vue'
import ClaimDialog from '../components/ClaimDialog.vue'
import { interactionApi, postApi } from '../api'
import useStore from '../store'
import { formatTime, relativeTime, statusLabel, statusType } from '../utils/format'

const store = useStore()
const route = useRoute()
const router = useRouter()

const detail = ref(null)
const verification = ref({ trustPercent: 0, trueCount: 0, falseCount: 0, unknownCount: 0, level: '暂无验证' })
const comments = ref([])
const claims = ref([])
const liked = ref(false)
const commentText = ref('')
const reportDialog = ref(null)
const claimDialog = ref(null)

const isLogin = computed(() => store.isLogin.value)
const isOwner = computed(() => Boolean(detail.value && store.state.user
  && detail.value.base.authorId === store.state.user.userId))
const claimable = computed(() => Boolean(detail.value && detail.value.base.categoryCode === 'LOST_FOUND'))

const load = async () => {
  const postId = Number(route.params.id)
  try {
    detail.value = await postApi.detail(postId)
    verification.value = await interactionApi.verification(postId)
    comments.value = await interactionApi.comments(postId)
    if (claimable.value) {
      await loadClaims()
    }
  } catch (error) {
    ElMessage.error(error.message)
    router.push('/')
  }
}

const loadClaims = async () => {
  try {
    claims.value = await interactionApi.claims(Number(route.params.id))
  } catch (error) {
    claims.value = []
  }
}

const onVerified = (summary) => {
  verification.value = summary
}

const toggleLike = async () => {
  if (!isLogin.value) {
    ElMessage.warning('请先登录')
    return
  }
  const postId = Number(route.params.id)
  try {
    const result = liked.value
      ? await interactionApi.unlike(postId)
      : await interactionApi.like(postId)
    liked.value = result.active
    detail.value.base.likeCount = result.total
    ElMessage.success(result.message)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const submitComment = async () => {
  if (!commentText.value.trim()) return
  try {
    const comment = await interactionApi.addComment(Number(route.params.id), commentText.value)
    comments.value.push(comment)
    detail.value.base.commentCount = (detail.value.base.commentCount || 0) + 1
    commentText.value = ''
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const handleClaim = async (claimId, action) => {
  try {
    await interactionApi.handleClaim(claimId, {
      action,
      remark: action === 'APPROVED' ? '信息核对一致' : '信息不符'
    })
    ElMessage.success('处理完成')
    await loadClaims()
    if (action === 'APPROVED') {
      detail.value.base.status = 'RESOLVED'
    }
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const markResolved = async () => {
  try {
    await postApi.resolve(Number(route.params.id))
    detail.value.base.status = 'RESOLVED'
    ElMessage.success('已标记为「已解决」')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

const removePost = async () => {
  try {
    const input = await ElMessageBox.prompt('请填写下架原因（可留空）', '下架信息', {
      confirmButtonText: '确认下架', cancelButtonText: '取消', inputValue: '信息已失效'
    })
    await postApi.remove(Number(route.params.id), input.value || '')
    ElMessage.success('已下架')
    router.push('/me')
  } catch (error) {
    if (error && error.message && error.message !== 'cancel') {
      ElMessage.error(error.message)
    }
  }
}

const loadCluster = async () => {
  try {
    const cluster = await postApi.cluster(detail.value.base.locationId)
    ElMessageBox.alert(
      cluster.posts.map((item) => `· ${item.title}`).join('\n'),
      `${cluster.locationName}：共 ${cluster.count} 条有效信息`,
      { confirmButtonText: '知道了' }
    )
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.cat-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.detail-title {
  margin: 12px 0 8px;
}

.detail-content {
  white-space: pre-wrap;
  margin: 0 0 10px;
}

.comment-list {
  margin-top: 12px;
  max-height: 360px;
  overflow: auto;
}

.comment-item {
  border-bottom: 1px dashed var(--border);
  padding: 8px 0;
}

.claim-item {
  border: 1px dashed var(--border);
  border-radius: 8px;
  padding: 8px 10px;
  margin-top: 8px;
}
</style>
