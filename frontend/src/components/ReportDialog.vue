<template>
  <el-dialog v-model="visible" title="举报信息" width="440px">
    <el-form label-width="80px">
      <el-form-item label="举报原因">
        <el-select v-model="reason" placeholder="请选择举报原因" style="width: 100%">
          <el-option v-for="item in reasons" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="补充说明">
        <el-input v-model="detail" type="textarea" :rows="3" maxlength="500" show-word-limit
                  placeholder="请描述具体情况，便于管理员核实" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { interactionApi } from '../api'

const props = defineProps({
  postId: { type: Number, required: true }
})

const visible = ref(false)
const submitting = ref(false)
const reason = ref('')
const detail = ref('')
const reasons = ['信息已过期', '内容与实际不符', '涉嫌广告', '包含不文明用语', '疑似诈骗信息', '重复发布', '其他']

const open = () => {
  reason.value = ''
  detail.value = ''
  visible.value = true
}

const submit = async () => {
  if (!reason.value) {
    ElMessage.warning('请选择举报原因')
    return
  }
  submitting.value = true
  try {
    await interactionApi.report(props.postId, { reason: reason.value, detail: detail.value })
    ElMessage.success('举报已提交，管理员会尽快核实处理')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
