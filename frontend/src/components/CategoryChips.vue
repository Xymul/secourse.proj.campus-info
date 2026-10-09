<template>
  <div class="row chips">
    <span class="chip" :class="{ active: modelValue === null || modelValue === undefined }" @click="select(null)">
      全部
    </span>
    <span
      v-for="category in categories"
      :key="category.categoryId"
      class="chip"
      :class="{ active: modelValue === category.categoryId }"
      @click="select(category.categoryId)"
    >
      <span class="dot" :style="{ background: category.color || '#8fa3b0' }" />
      {{ category.name }}
      <small v-if="category.postCount !== null && category.postCount !== undefined" class="muted">
        {{ category.postCount }}
      </small>
    </span>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  categories: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'change'])

const select = (value) => {
  emit('update:modelValue', value)
  emit('change', value)
}

// 便于模板中引用（保持 props 声明的完整性）
void props
</script>

<style scoped>
.chips {
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
</style>
