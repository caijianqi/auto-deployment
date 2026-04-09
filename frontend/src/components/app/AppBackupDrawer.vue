<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="备份点管理"
    direction="rtl"
    size="50%"
  >
    <div class="mb-4 text-sm text-gray-500 italic">
      * 系统在每次“上传成功”后会自动创建当前版本的备份点。
    </div>
    <el-table :data="data" v-loading="loading" stripe border>
      <el-table-column prop="version" label="版本" width="160">
          <template #default="{ row }">
              <div class="flex items-center gap-2">
                  <span class="font-mono">{{ row.version }}</span>
                  <el-tag v-if="row.version === currentVersion" size="small" type="success" effect="dark">当前版本</el-tag>
              </div>
          </template>
      </el-table-column>
      <el-table-column prop="size" label="文件大小" width="100">
        <template #default="{ row }">
          {{ formatSize(row.size) }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="备份时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="filePath" label="物理路径" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="{ row }">
          <el-button-group v-if="row.version !== currentVersion">
            <el-button 
              type="warning" 
              size="small" 
              @click="$emit('rollback', row)"
            >
              回滚
            </el-button>
            <el-button type="danger" size="small" @click="$emit('delete', row)">删除</el-button>
          </el-button-group>
          <span v-else class="text-xs text-gray-400 italic">活跃版本保护</span>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup>
import dayjs from 'dayjs'

defineProps({
  modelValue: Boolean,
  data: Array,
  loading: Boolean,
  currentVersion: String
})

defineEmits(['update:modelValue', 'rollback', 'delete'])

const formatSize = (bytes) => {
    if (bytes === null || bytes === undefined || bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatDateTime = (date) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}
</script>
