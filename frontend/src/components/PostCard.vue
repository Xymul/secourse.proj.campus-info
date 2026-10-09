<template>
  <div class="post-card" @click="$router.push(`/posts/${post.postId}`)">
    <div class="post-card-head">
      <span class="cat-dot" :style="{ background: post.categoryColor || '#8fa3b0' }" />
      <span class="cat-name">{{ post.categoryName || '未分类' }}</span>
      <el-tag v-if="post.status && post.status !== 'PUBLISHED'" size="small" :type="statusType(post.status)">
        {{ statusLabel(post.status) }}
      </el-tag>
      <span class="muted small">{{ relativeTime(post.createdAt) }}</span>
      <span v-if="post.relevance !== null && post.relevance !== undefined" class="relevance">
        相关度 {{ post.relevance }}%
      </span>
    </div>

    <h4 class="post-title">{{ post.title }}</h4>
    <p class="post-summary muted">{{ post.summary }}</p>

    <div class="row small muted">
      <span v-if="post.locationName">📍 {{ post.locationName }}</span>
      <span v-if="post.distanceKm">🚶 {{ distanceText(post.distanceKm) }}</span>
      <span>👁 {{ post.viewCount || 0 }}</span>
      <span>💬 {{ post.commentCount || 0 }}</span>
      <span>👍 {{ post.likeCount || 0 }}</span>
      <span v-if="post.verifyTrueCount || post.verifyFalseCount">
        ✅ {{ post.verifyTrueCount || 0 }} / ❌ {{ post.verifyFalseCount || 0 }}
      </span>
      <span>发布者：{{ post.authorNickname || '-' }}</span>
    </div>

    <div v-if="post.tags && post.tags.length" class="row">
      <span v-for="tag in post.tags" :key="tag" class="tag">{{ tag }}</span>
    </div>

    <div v-if="post.matchReasons && post.matchReasons.length" class="row">
      <span v-for="reason in post.matchReasons" :key="reason" class="reason">🔎 {{ reason }}</span>
    </div>

    <div v-if="showTrust && post.trustRatio !== null && post.trustRatio !== undefined" class="trust">
      <div class="bar">
        <span :style="{ width: `${Math.round(post.trustRatio * 100)}%` }" />
      </div>
      <span class="small muted">协同验证可信度 {{ Math.round(post.trustRatio * 100) }}%</span>
    </div>
  </div>
</template>

<script setup>
import { relativeTime, distanceText, statusLabel, statusType } from '../utils/format'

defineProps({
  post: { type: Object, required: true },
  showTrust: { type: Boolean, default: true }
})
</script>

<style scoped>
.post-card {
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.post-card:hover {
  box-shadow: var(--shadow);
  transform: translateY(-1px);
}

.post-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-soft);
}

.cat-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.cat-name {
  font-weight: 600;
  color: var(--text);
}

.relevance {
  margin-left: auto;
  color: var(--brand);
  font-weight: 600;
}

.post-title {
  margin: 8px 0 6px;
  font-size: 16px;
}

.post-summary {
  margin: 0 0 8px;
  font-size: 13px;
}

.trust {
  margin-top: 8px;
}
</style>
