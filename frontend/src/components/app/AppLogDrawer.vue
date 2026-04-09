<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="`实时日志 - ${appName}`"
    direction="rtl"
    size="60%"
    destroy-on-close
    @close="$emit('close')"
  >
    <div 
      ref="logContainer"
      class="bg-gray-900 text-green-400 p-4 font-mono text-sm h-full overflow-y-auto whitespace-pre-wrap"
    >
      <div v-for="(line, index) in logs" :key="index">{{ line }}</div>
      <div v-if="logs.length === 0" class="text-gray-500 italic">正在等待日志输出...</div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  appName: String,
  logs: Array
})

defineEmits(['update:modelValue', 'close'])

const logContainer = ref(null)

// Auto-scroll to bottom when logs update
watch(() => props.logs.length, () => {
    nextTick(() => {
        if (logContainer.value) {
            logContainer.value.scrollTop = logContainer.value.scrollHeight
        }
    })
})
</script>
