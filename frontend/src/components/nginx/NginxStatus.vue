<template>
  <div class="flex items-center gap-4">
    <el-tag :type="status ? 'success' : 'danger'" effect="dark" class="status-tag">
      <div class="flex items-center gap-2 px-2">
        <div class="w-2 h-2 rounded-full bg-white animate-pulse" v-if="status"></div>
        {{ status ? '运行中' : '已停止' }}
      </div>
    </el-tag>
    <el-button :icon="Setting" @click="$emit('open-path-dialog')">设置路径</el-button>
    <el-button-group>
      <el-button type="success" :icon="VideoPlay" @click="$emit('start')" :disabled="status">启动</el-button>
      <el-button type="danger" :icon="VideoPause" @click="$emit('stop')" :disabled="!status">停止</el-button>
      <el-button type="primary" :icon="Refresh" @click="$emit('restart')">重载配置</el-button>
    </el-button-group>
  </div>
</template>

<script setup>
import { VideoPlay, VideoPause, Refresh, Setting } from '@element-plus/icons-vue'

defineProps({
  status: Boolean
})

defineEmits(['start', 'stop', 'restart', 'open-path-dialog'])
</script>

<style scoped>
.status-tag {
  height: 32px;
  font-size: 14px;
}
</style>
