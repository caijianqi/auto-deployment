<template>
  <div class="space-y-6 animate-fade-in">
    <el-row :gutter="20">
      <el-col :span="8" v-for="item in statCards" :key="item.label">
        <el-card shadow="hover">
          <div class="flex items-center gap-4">
            <div :class="['p-3 rounded-lg', item.bg, item.color]">
              <component :is="item.icon" class="w-6 h-6" />
            </div>
            <div>
              <p class="text-sm text-gray-400 mb-1">{{ item.label }}</p>
              <p class="text-2xl font-bold text-gray-800">{{ item.value }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-bold">最新操作日志</span>
              <el-button link type="primary" @click="viewAllLogs">查看全部</el-button>
            </div>
          </template>
          <el-table :data="recentLogs" style="width: 100%">
            <el-table-column prop="appName" label="应用名称" width="150" />
            <el-table-column prop="opType" label="操作类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getTagType(row.opType)" size="small">{{ row.opType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="content" label="操作详情" show-overflow-tooltip />
            <el-table-column prop="createTime" label="操作时间" width="160" align="right">
              <template #default="{ row }">
                <span class="text-gray-400 text-sm">{{ formatTime(row.createTime) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card shadow="hover" class="mb-6">
          <template #header>
            <span class="font-bold">系统负载</span>
          </template>
          <div class="space-y-6">
            <div>
              <div class="flex justify-between mb-2">
                <span class="text-sm text-gray-500">CPU 使用率</span>
                <span class="text-sm font-bold">{{ cpuUsage }}%</span>
              </div>
              <el-progress :percentage="cpuUsage" :show-text="false" :stroke-width="12" />
            </div>
            <div>
              <div class="flex justify-between mb-2">
                <span class="text-sm text-gray-500">内存 使用率</span>
                <span class="text-sm font-bold">{{ memUsage }}%</span>
              </div>
              <el-progress :percentage="memUsage" :show-text="false" :stroke-width="12" color="#67c23a" />
            </div>
            <div>
              <div class="flex justify-between mb-2">
                <span class="text-sm text-gray-500">磁盘 使用率</span>
                <span class="text-sm font-bold">{{ diskUsage }}%</span>
              </div>
              <el-progress :percentage="diskUsage" :show-text="false" :stroke-width="12" color="#e6a23c" />
            </div>
          </div>
        </el-card>
        
        <div class="bg-gray-800 text-white p-4 rounded-lg flex items-center justify-between">
          <div class="flex items-center gap-3">
            <Database class="w-5 h-5 text-gray-400" />
            <span class="text-sm font-medium">SQLite 数据库状态</span>
          </div>
          <el-tag type="success" size="small" effect="dark">正常</el-tag>
        </div>
      </el-col>
    </el-row>

    <!-- Global Logs Drawer -->
    <el-drawer
      v-model="showAllLogsDrawer"
      title="平台操作全记录"
      direction="rtl"
      size="60%"
    >
      <el-table :data="allLogs" v-loading="allLogsLoading" stripe border>
        <el-table-column prop="createTime" label="时间" width="160">
          <template #default="{ row }">
            {{ dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') }}
          </template>
        </el-table-column>
        <el-table-column prop="appName" label="应用" width="120" />
        <el-table-column prop="opType" label="操作" width="80">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.opType)" size="small">{{ row.opType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="详情" show-overflow-tooltip />
      </el-table>
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="historyTotal"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
          background
        />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, onUnmounted, computed } from 'vue'
import { Box, Activity, CircleStop, Database } from 'lucide-vue-next'
import api from '@/api'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const stats = reactive({
  total: 0,
  running: 0,
  stopped: 0
})

const statCards = computed(() => [
  { label: '注册应用', value: stats.total, icon: Box, bg: 'bg-blue-50', color: 'text-blue-500' },
  { label: '运行中', value: stats.running, icon: Activity, bg: 'bg-green-50', color: 'text-green-500' },
  { label: '已停止', value: stats.stopped, icon: CircleStop, bg: 'bg-red-50', color: 'text-red-500' }
])

const formatTime = (time) => {
    if (!time) return '-'
    return dayjs(time).fromNow()
}

const recentLogs = ref([])
const allLogs = ref([])
const showAllLogsDrawer = ref(false)
const allLogsLoading = ref(false)

// Pagination state
const currentPage = ref(1)
const pageSize = ref(15)
const historyTotal = ref(0)

const cpuUsage = ref(0)
const memUsage = ref(0)
const diskUsage = ref(0)
let statsTimer = null

const fetchRecentLogs = async () => {
    try {
        const res = await api.logs.listLatest()
        const logData = res.data || []
        // We need to match app names since the log table only has appId
        const appsRes = await api.apps.list()
        const apps = appsRes.data || []
        const appMap = {}
        apps.forEach(a => appMap[a.id] = a.name)
        
        recentLogs.value = logData.map(log => ({
            ...log,
            appName: appMap[log.appId] || '未知应用'
        }))
    } catch (e) {
        console.error('获取最近日志失败', e)
    }
}

const fetchStats = async () => {
    try {
        const appsRes = await api.apps.list()
        const apps = appsRes.data || []
        stats.total = apps.length
        stats.running = apps.filter(a => a.status === 'RUNNING').length
        stats.stopped = apps.filter(a => a.status === 'STOPPED').length
        
        // Fetch hardware stats
        const hardwareRes = await api.config.getStats()
        if (hardwareRes.code === 200 && hardwareRes.data) {
            cpuUsage.value = hardwareRes.data.cpu
            memUsage.value = hardwareRes.data.memory
            diskUsage.value = hardwareRes.data.disk
        }
    } catch (e) {
        console.error(e)
    }
}

const viewAllLogs = async () => {
    currentPage.value = 1
    showAllLogsDrawer.value = true
    await fetchAllLogs()
}

const handlePageChange = async (val) => {
    currentPage.value = val
    await fetchAllLogs()
}

const fetchAllLogs = async () => {
    allLogsLoading.value = true
    try {
        const res = await api.logs.listAll({
            current: currentPage.value,
            size: pageSize.value
        })
        const pageData = res.data
        if (!pageData) return
        
        historyTotal.value = pageData.total || 0
        const logData = pageData.records || []
        
        const appsRes = await api.apps.list()
        const apps = appsRes.data || []
        const appMap = {}
        apps.forEach(a => appMap[a.id] = a.name)
        
        allLogs.value = logData.map(log => ({
            ...log,
            appName: appMap[log.appId] || '未知应用'
        }))
    } catch (e) {
        console.error('获取全量日志失败', e)
    } finally {
        allLogsLoading.value = false
    }
}

const getTagType = (type) => {
  switch (type) {
    case 'DEPLOY': return 'success'
    case 'START': return 'primary'
    case 'STOP': return 'danger'
    default: return 'info'
  }
}

onMounted(() => {
    fetchStats()
    fetchRecentLogs()
    // Start hardware stats polling every 5 seconds
    statsTimer = setInterval(fetchStats, 5000)
})

onUnmounted(() => {
    if (statsTimer) clearInterval(statsTimer)
})
</script>
