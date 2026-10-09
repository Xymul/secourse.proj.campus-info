<template>
  <div class="page">
    <section class="card">
      <div class="row" style="justify-content: space-between">
        <div>
          <h2 style="margin: 0 0 6px">{{ user ? user.nickname : '个人中心' }}</h2>
          <div class="muted small">
            <span>{{ user ? user.schoolName : '' }}</span>
            <span> · 信用分：{{ user ? user.creditScore : '-' }}</span>
            <span> · 角色：{{ user && user.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
          </div>
        </div>
        <el-button size="small" @click="$router.push('/publish')">发布新信息</el-button>
      </div>
    </section>

    <section class="card">
      <el-tabs v-model="tab">
        <el-tab-pane :label="`我的发布 (${data.posts.length})`" name="posts">
          <div class="grid grid-2">
            <PostCard v-for="post in data.posts" :key="post.postId" :post="post" />
          </div>
          <div v-if="!data.posts.length" class="empty">还没有发布过信息</div>
        </el-tab-pane>

        <el-tab-pane :label="`我的点赞 (${data.likedPosts.length})`" name="likes">
          <div class="grid grid-2">
            <PostCard v-for="post in data.likedPosts" :key="post.postId" :post="post" />
          </div>
          <div v-if="!data.likedPosts.length" class="empty">还没有点赞过信息</div>
        </el-tab-pane>

        <el-tab-pane :label="`我的验证 (${data.verifications.length})`" name="verifications">
          <el-table :data="data.verifications" style="width: 100%">
            <el-table-column prop="postTitle" label="信息标题" min-width="220">
              <template #default="{ row }">
                <router-link :to="`/posts/${row.postId}`">{{ row.postTitle }}</router-link>
              </template>
            </el-table-column>
            <el-table-column prop="result" label="立场" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.result === 'TRUE' ? 'success' : (row.result === 'FALSE' ? 'danger' : 'info')">
                  {{ row.result === 'TRUE' ? '证实' : (row.result === 'FALSE' ? '证伪' : '存疑') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="160" />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!data.verifications.length" class="empty">还没有参与过验证</div>
        </el-tab-pane>

        <el-tab-pane :label="`我的认领 (${data.claims.length})`" name="claims">
          <el-table :data="data.claims" style="width: 100%">
            <el-table-column prop="postTitle" label="信息标题" min-width="220">
              <template #default="{ row }">
                <router-link :to="`/posts/${row.postId}`">{{ row.postTitle }}</router-link>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="认领说明" min-width="200" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small">{{ claimStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!data.claims.length" class="empty">还没有提交过认领申请</div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PostCard from '../components/PostCard.vue'
import { interactionApi } from '../api'
import useStore from '../store'
import { formatTime } from '../utils/format'

const store = useStore()
const tab = ref('posts')
const data = reactive({ posts: [], likedPosts: [], verifications: [], claims: [] })
const user = computed(() => store.state.user)

const claimStatusLabel = (status) => ({
  PENDING: '待确认', APPROVED: '已通过', REJECTED: '已驳回', CANCELED: '已取消'
}[status] || status)

onMounted(async () => {
  try {
    const result = await interactionApi.myInteractions()
    data.posts = result.posts || []
    data.likedPosts = result.likedPosts || []
    data.verifications = result.verifications || []
    data.claims = result.claims || []
  } catch (error) {
    ElMessage.error(error.message)
  }
})
</script>
