<template>
  <div class="verify-panel">
    <div class="row" style="justify-content: space-between">
      <div>
        <div class="stat-value">{{ summary.trustPercent }}%</div>
        <div class="stat-label">证实占比（{{ summary.level }}）</div>
      </div>
      <div class="row">
        <el-button
          size="small"
          :type="summary.myResult === 'TRUE' ? 'success' : 'default'"
          :disabled="!isLogin || submitting"
          @click="submit('TRUE')"
        >
          ✅ 证实
        </el-button>
        <el-button
          size="small"
          :type="summary.myResult === 'FALSE' ? 'danger' : 'default'"
          :disabled="!isLogin || submitting"
          @click="submit('FALSE')"
        >
          ❌ 证伪
        </el-button>
        <el-button size="small" :disabled="!isLogin || submitting" @click="submit('UNKNOWN')">❓ 存疑</el-button>
      </div>
    </div>

    <div class="bar" style="margin: 10px 0">
      <span :style="{ width: `${summary.trustPercent || 0}%` }" />
    </div>

    <div class="row small muted">
      <span>证实 {{ summary.trueCount || 0 }} 人</span>
      <span>证伪 {{ summary.falseCount || 0 }} 人</span>
      <span>存疑 {{ summary.unknownCount || 0 }} 人</span>
      <span v-if="summary.myResult">我的立场：
        <b>{{ resultLabel(summary.myResult) }}</b>
      </span>
      <span v-if="!isLogin">登录后可以参与协同验证</span>
    </div>

    <el-alert
      v-if="summary.warned"
      type="warning"
      :closable="false"
      show-icon
      style="margin-top: 10px"
      title="该信息收到较多证伪反馈，已进入平台关注列表，请注意甄别"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { interactionApi } from '../api'
import useStore from '../store'

const props = defineProps({
  postId: { type: Number, required: true },
  summary: { type: Object, required: true }
})

const emit = defineEmits(['updated'])
const store = useStore()
const isLogin = computed(() => store.isLogin.value)
const submitting = ref(false)

const resultLabel = (value) => ({ TRUE: '证实', FALSE: '证伪', UNKNOWN: '存疑' }[value] || value)

const submit = async (result) => {
  const isLogin = store.isLogin.value
  if (!isLogin) {
    ElMessage.warning('请先登录')
    return
  }
  let remark = ''
  try {
    const input = await ElMessageBox.prompt(
      `请补充说明（可留空），提交后将记录你的「${resultLabel(result)}」立场`,
      '协同验证',
      { inputPlaceholder: '例如：我在现场确认信息真实', confirmButtonText: '提交', cancelButtonText: '取消' }
    )
    remark = input.value || ''
  } catch (error) {
    return
  }
  submitting.value = true
  try {
    const summary = await interactionApi.verify(props.postId, { result, remark })
    emit('updated', summary)
    ElMessage.success('感谢你的反馈，已记录验证结果')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.verify-panel {
  border: 1px dashed var(--border);
  border-radius: var(--radius);
  padding: 14px;
}
</style>
