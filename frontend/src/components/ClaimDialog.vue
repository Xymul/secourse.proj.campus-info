<template>
  <el-dialog v-model="visible" title="提交认领申请" width="460px">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="请尽量提供能证明物品归属的细节，发布者确认后信息将标记为已解决"
      style="margin-bottom: 12px"
    />
    <el-form label-width="80px">
      <el-form-item label="认领说明">
        <el-input v-model="description" type="textarea" :rows="4" maxlength="500" show-word-limit
                  placeholder="例如：校园卡姓名、学号后四位、丢失时间与地点等" />
      </el-form-item>
      <el-form-item label="联系方式">
        <el-input v-model="contact" maxlength="100" placeholder="手机号 / 微信 / QQ（仅发布者可见）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
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

const emit = defineEmits(['submitted'])
const visible = ref(false)
const submitting = ref(false)
const description = ref('')
const contact = ref('')

const open = () => {
  description.value = ''
  contact.value = ''
  visible.value = true
}

const submit = async () => {
  if (!description.value.trim()) {
    ElMessage.warning('请填写认领说明')
    return
  }
  submitting.value = true
  try {
    await interactionApi.claim(props.postId, { description: description.value, contact: contact.value })
    ElMessage.success('认领申请已提交，请等待发布者确认')
    visible.value = false
    emit('submitted')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
