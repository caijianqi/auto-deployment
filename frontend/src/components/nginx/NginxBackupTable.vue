<template>
  <el-card shadow="hover">
    <template #header>
      <div class="flex justify-between items-center font-bold">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <el-icon><Clock /></el-icon>
            配置备份记录
          </div>
          <el-input
            :model-value="backupSearch"
            @update:model-value="$emit('update:backupSearch', $event)"
            placeholder="搜索备注..."
            size="small"
            style="width: 200px"
            clearable
            :prefix-icon="Search"
          />
        </div>
        <el-button size="small" :icon="Refresh" @click="$emit('refresh')" :loading="loading">刷新</el-button>
      </div>
    </template>
    <el-table :data="data" v-loading="loading" size="small" border stripe highlight-current-row style="width: 100%" :default-sort="{ prop: 'createTime', order: 'descending' }">
      <el-table-column prop="createTime" label="备份时间" width="160" sortable>
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="size" label="大小" width="100" sortable>
        <template #default="{ row }">
          {{ formatSize(row.size) }}
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注">
        <template #default="{ row }">
          <div class="flex items-center justify-between group">
            <span>{{ row.remark || '-' }}</span>
            <el-button link type="primary" :icon="Edit" @click="$emit('edit-remark', row)" class="hidden group-hover:inline-flex"></el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$emit('view', row)">查看</el-button>
          <el-button link type="primary" @click="$emit('restore', row)">回滚</el-button>
          <el-button link type="danger" @click="$emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { Clock, Search, Refresh, Edit } from '@element-plus/icons-vue'

defineProps({
  data: Array,
  loading: Boolean,
  backupSearch: String
})

defineEmits(['update:backupSearch', 'refresh', 'edit-remark', 'view', 'restore', 'delete'])

const formatSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatDateTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}
</script>
