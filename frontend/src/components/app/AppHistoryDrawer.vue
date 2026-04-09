<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="操作历史"
    direction="rtl"
    size="50%"
  >
    <el-table :data="data" v-loading="loading" stripe border>
      <el-table-column prop="createTime" label="时间" width="160">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="opType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.opType === 'DEPLOY' ? 'primary' : 'info'">{{ row.opType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="详情" show-overflow-tooltip />
    </el-table>
  </el-drawer>
</template>

<script setup>
import dayjs from 'dayjs'

defineProps({
  modelValue: Boolean,
  data: Array,
  loading: Boolean
})

const formatDateTime = (date) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}
</script>
