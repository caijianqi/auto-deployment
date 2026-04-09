<template>
  <el-card shadow="hover">
    <template #header>
      <div class="flex justify-between items-center">
        <div class="flex items-center gap-2 font-bold">
          <el-icon><Monitor /></el-icon>
          Nginx 实时日志
        </div>
        <div class="flex gap-2">
          <el-radio-group :model-value="activeLogType" size="small" @update:model-value="$emit('update:activeLogType', $event)">
            <el-radio-button value="access">访问日志</el-radio-button>
            <el-radio-button value="error">错误日志</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="$emit('clear')">清空日志</el-button>
        </div>
      </div>
    </template>
    <div 
      ref="logContainer"
      class="log-console bg-gray-900 text-gray-200 p-4 rounded h-[400px] overflow-y-auto font-mono text-xs whitespace-pre-wrap"
    >
      <div v-for="(log, index) in logs" :key="index" class="log-line mb-1" v-html="formatLogLine(log)"></div>
      <div v-if="logs.length === 0" class="text-gray-500 italic">
        正在等待日志输出...
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { Monitor } from '@element-plus/icons-vue'

const props = defineProps({
  logs: Array,
  activeLogType: String
})

const emit = defineEmits(['update:activeLogType', 'clear'])

const logContainer = ref(null)

// Auto-scroll to bottom when logs update
watch(() => props.logs.length, () => {
  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight
    }
  })
})

const formatLogLine = (line) => {
  if (!line) return ''
  
  // Basic security: escape HTML first
  let escaped = line.replace(/&/g, '&amp;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;')
                    .replace(/"/g, '&quot;')
                    .replace(/'/g, '&#039;')

  // IP Address
  escaped = escaped.replace(/\b\d{1,3}(\.\d{1,3}){3}\b/g, '<span class="text-blue-400">$&</span>')
  
  // Date/Time in brackets
  escaped = escaped.replace(/\[(.*?)\]/g, '<span class="text-gray-500">[$1]</span>')
  
  // Quoted strings (often the request)
  escaped = escaped.replace(/&quot;(.*?)&quot;/g, '<span class="text-yellow-200">"$1"</span>')
  
  // HTTP Methods (bold purple)
  escaped = escaped.replace(/\b(GET|POST|PUT|DELETE|HEAD|OPTIONS|PATCH)\b/g, '<span class="text-purple-400 font-bold">$&</span>')
  
  // Status Codes
  escaped = escaped.replace(/\s(2\d{2})\s/g, ' <span class="text-green-400 font-bold">$1</span> ')
  escaped = escaped.replace(/\s(3\d{2})\s/g, ' <span class="text-yellow-400">$1</span> ')
  escaped = escaped.replace(/\s([45]\d{2})\s/g, ' <span class="text-red-400 font-bold">$1</span> ')

  // Error/Warn keywords
  escaped = escaped.replace(/\b(ERROR|Crit|Alert|Emerg|failed|error)\b/gi, '<span class="text-red-500 font-bold underline">$&</span>')
  escaped = escaped.replace(/\b(WARN|notice|warn)\b/gi, '<span class="text-yellow-500 font-bold">$&</span>')
  
  return escaped
}
</script>

<style scoped>
.text-blue-400 { color: #60a5fa; }
.text-gray-500 { color: #6b7280; }
.text-yellow-200 { color: #fef08a; }
.text-purple-400 { color: #c084fc; }
.text-green-400 { color: #4ade80; }
.text-yellow-400 { color: #facc15; }
.text-red-400 { color: #f87171; }
.text-red-500 { color: #ef4444; }
.text-yellow-500 { color: #eab308; }
.font-bold { font-weight: 700; }
.underline { text-decoration: underline; }
</style>
