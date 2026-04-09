<template>
  <div class="process-manage">
    <el-card class="mb-6 shadow-sm border-none bg-gradient-to-r from-slate-50 to-white">
      <div class="flex justify-between items-center">
        <div>
          <h2 class="text-xl font-bold text-slate-800 flex items-center gap-2">
            <el-icon color="#409eff"><Activity /></el-icon>
            Java 进程管理
          </h2>
          <p class="text-sm text-slate-500 mt-1">
            监控和管理服务器上运行的其他 Java 进程。
          </p>
        </div>
        <el-button type="primary" :icon="Refresh" @click="fetchProcesses" :loading="loading">
          刷新列表
        </el-button>
      </div>
    </el-card>

    <el-card class="shadow-sm border-none">
      <el-table :data="processes" v-loading="loading" style="width: 100%" border stripe>
        <el-table-column prop="pid" label="PID" width="100" align="center" sortable />
        <el-table-column prop="mainClass" label="进程名称 / 主类" min-width="250">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <el-tag v-if="row.mainClass.includes('auto-deployment')" type="warning" size="small">
                托管
              </el-tag>
              <span class="font-mono text-xs overflow-hidden text-ellipsis whitespace-nowrap" :title="row.mainClass">
                {{ row.mainClass }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="arguments" label="JVM 参数" min-width="300">
          <template #default="{ row }">
            <el-tooltip :content="row.arguments" placement="top" :disabled="!row.arguments">
              <span class="text-xs text-slate-500 font-mono overflow-hidden text-ellipsis whitespace-nowrap block">
                {{ row.arguments || '-' }}
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-popconfirm
              title="确定要终止该 Java 进程吗？"
              confirm-button-text="确定"
              cancel-button-text="取消"
              @confirm="handleKill(row.pid)"
            >
              <template #reference>
                <el-button 
                  type="danger" 
                  size="small" 
                  plain 
                  title="终止进程"
                >
                  终止
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-4 text-xs text-slate-400 flex items-center gap-1">
        <el-icon><InfoFilled /></el-icon>
        提示：数据通过 jps 命令实时获取。终止进程可能会导致应用非正常停机，请谨慎操作。
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Activity, RefreshCw as Refresh, Info as InfoFilled } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const processes = ref([])
const loading = ref(false)

const fetchProcesses = async () => {
  loading.value = true
  try {
    const res = await api.processes.list()
    if (res.code === 200) {
      processes.value = res.data
    } else {
      ElMessage.error(res.msg || '获取进程列表失败')
    }
  } catch (error) {
    ElMessage.error('服务连接异常')
  } finally {
    loading.value = false
  }
}

const handleKill = async (pid) => {
  try {
    const res = await api.processes.kill(pid)
    if (res.code === 200) {
      ElMessage.success('进程已成功终止')
      fetchProcesses()
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (error) {
    ElMessage.error('终止请求失败')
  }
}

onMounted(() => {
  fetchProcesses()
})
</script>

<style scoped>
.process-manage {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

:deep(.el-table .el-table__cell) {
  padding: 12px 0;
}
</style>
