<template>
  <div class="nginx-manager p-6">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold text-gray-800">Nginx 管理</h1>
      <NginxStatus 
        :status="status"
        @start="handleStart"
        @stop="handleStop"
        @restart="handleRestart"
        @open-path-dialog="showPathDialog = true"
      />
    </div>

    <div class="grid grid-cols-1 gap-6">
      <!-- Config Editor -->
      <NginxConfigEditor 
        v-model="configContent"
        :saving="saving"
        @save="saveConfig"
      />

      <!-- Log Viewer -->
      <NginxLogViewer 
        :logs="logs"
        v-model:activeLogType="activeLogType"
        @clear="clearLogs"
      />
      
      <!-- Backup Table -->
      <NginxBackupTable 
        :data="filteredBackups"
        :loading="backupsLoading"
        v-model:backupSearch="backupSearch"
        @refresh="fetchBackups"
        @edit-remark="openRemarkEdit"
        @view="handleViewBackup"
        @restore="handleRestore"
        @delete="handleDeleteBackup"
      />

      <!-- Documentation -->
      <el-card shadow="hover">
        <template #header>
          <span class="font-bold">使用说明</span>
        </template>
        <div class="text-gray-600 text-sm space-y-2">
          <p>1. <strong>Windows 用户</strong>：请设置 Nginx 安装根目录。系统将寻找 <code>/conf/nginx.conf</code> 和 <code>/logs/access.log</code>。</p>
          <p>2. <strong>Linux 用户</strong>：若安装路径非标准（非 /etc/nginx），请在设置中指定真实路径。</p>
          <p>3. <strong>实时日志</strong>：自动追踪 access.log 和 error.log 的新增内容，切换标签可即时切换监控文件。</p>
        </div>
      </el-card>
    </div>

    <!-- Nginx Path Configuration Dialog -->
    <el-dialog v-model="showPathDialog" title="Nginx 基础路径设置" width="500px">
      <el-form label-position="top">
        <el-form-item label="安装目录 (Nginx Home)">
          <el-input 
            v-model="nginxHome" 
            placeholder="例如: D:\nginx-1.26.3 或 /usr/local/nginx" 
          />
          <div class="text-xs text-gray-400 mt-2">
            设置正确的根目录后，系统方能定位配置文件并执行控制命令。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPathDialog = false">取消</el-button>
        <el-button type="primary" @click="saveHome" :loading="homeSaving">确认保存</el-button>
      </template>
    </el-dialog>

    <!-- Backup Content Dialog -->
    <el-dialog v-model="showBackupDialog" title="备份文件内容预览" width="800px">
      <div class="editor-container border rounded overflow-hidden">
        <NginxConfigEditor 
          v-model="backupContent"
          height="500px"
          :disabled="true"
        />
      </div>
      <template #footer>
        <el-button @click="showBackupDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Remark Edit Dialog -->
    <el-dialog v-model="showRemarkDialog" title="修改备份备注" width="400px">
      <el-form label-position="top">
        <el-form-item label="备份时间">
          <el-text gray>{{ formatDateTime(editingBackup?.createTime) }}</el-text>
        </el-form-item>
        <el-form-item label="备注内容">
          <el-input v-model="newRemark" type="textarea" :rows="3" placeholder="请输入备注内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRemarkDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRemark" :loading="remarkSaving">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

// Components
import NginxStatus from '../components/nginx/NginxStatus.vue'
import NginxConfigEditor from '../components/nginx/NginxConfigEditor.vue'
import NginxLogViewer from '../components/nginx/NginxLogViewer.vue'
import NginxBackupTable from '../components/nginx/NginxBackupTable.vue'

const status = ref(false)
const showPathDialog = ref(false)
const nginxHome = ref('')
const homeSaving = ref(false)
const configContent = ref('')
const saving = ref(false)

// Log related
const activeLogType = ref('access')
const logs = ref([])
let timer = null
let logWs = null

// Backup related
const backups = ref([])
const backupsLoading = ref(false)
const backupSearch = ref('')
const showBackupDialog = ref(false)
const backupContent = ref('')

// Remark editing
const showRemarkDialog = ref(false)
const editingBackup = ref(null)
const newRemark = ref('')
const remarkSaving = ref(false)

const filteredBackups = computed(() => {
  const list = backups.value || []
  if (!backupSearch.value) return list
  const s = backupSearch.value.toLowerCase()
  return list.filter(b => (b.remark && b.remark.toLowerCase().includes(s)))
})

const formatDateTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

const fetchBackups = async () => {
  backupsLoading.value = true
  try {
    const res = await api.nginx.listBackups()
    backups.value = res.data || []
  } catch (error) {
    console.error('获取备份列表失败', error)
  } finally {
    backupsLoading.value = false
  }
}

const openRemarkEdit = (row) => {
  editingBackup.value = row
  newRemark.value = row.remark || ''
  showRemarkDialog.value = true
}

const saveRemark = async () => {
  if (!editingBackup.value) return
  remarkSaving.value = true
  try {
    await api.nginx.updateBackupRemark(editingBackup.value.id, { remark: newRemark.value })
    ElMessage.success('备注已更新')
    showRemarkDialog.value = false
    fetchBackups()
  } catch (error) {
    ElMessage.error('更新失败')
  } finally {
    remarkSaving.value = false
  }
}

const handleViewBackup = async (row) => {
  try {
    const res = await api.nginx.getBackupContent(row.id)
    backupContent.value = res.data
    showBackupDialog.value = true
  } catch (error) {
    ElMessage.error('获取备份内容失败')
  }
}

const handleRestore = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要恢复到备份 ${row.createTime} 吗？这会覆盖当前配置并重载 Nginx。`, '提示', { type: 'warning' })
    const res = await api.nginx.restoreBackup(row.id)
    ElMessage.success(res.msg || '恢复成功')
    fetchConfig()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('恢复失败: ' + (error.message || error))
    }
  }
}

const handleDeleteBackup = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该备份吗？', '提示', { type: 'warning' })
    await api.nginx.deleteBackup(row.id)
    ElMessage.success('删除成功')
    fetchBackups()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const connectLog = () => {
  if (logWs) {
    logWs.close()
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // 使用当前 host 以便通过 Vite 代理 (5173 -> 8080)
  const host = window.location.host
  const prefix = (import.meta.env.VITE_APP_API_PREFIX || '/api').replace(/\/$/, '')
  const url = `${protocol}//${host}${prefix}/ws/nginx-log/${activeLogType.value}`
  
  logWs = new WebSocket(url)
  
  logWs.onmessage = (event) => {
    logs.value.push(event.data)
    if (logs.value.length > 1000) {
      logs.value.shift()
    }
  }

  logWs.onerror = () => {
    logs.value.push('=== [错误] 无法连接到日志服务器 ===')
  }
}

watch(activeLogType, () => {
  logs.value = []
  connectLog()
})

const clearLogs = () => {
  logs.value = []
}

const fetchHome = async () => {
  try {
    const res = await api.nginx.getHome()
    nginxHome.value = res.data || ''
  } catch (error) {
    console.error('获取 Nginx 路径失败', error)
  }
}

const saveHome = async () => {
  if (!nginxHome.value) return
  homeSaving.value = true
  try {
    await api.nginx.updateHome({ path: nginxHome.value })
    ElMessage.success('Nginx 路径已保存')
    showPathDialog.value = false
    fetchConfig()
  } catch (error) {
    ElMessage.error('保存路径失败')
  } finally {
    homeSaving.value = false
  }
}

const fetchStatus = async () => {
  try {
    const res = await api.nginx.status()
    status.value = res.data
  } catch (error) {
    console.error('获取 Nginx 状态失败', error)
  }
}

const fetchConfig = async () => {
  try {
    const res = await api.nginx.getConfig()
    configContent.value = res.data || ''
    if (!res.data) {
       ElMessage.warning('未能读取到配置文件，请检查 Nginx 路径配置')
    }
  } catch (error) {
    ElMessage.error('获取配置文件失败')
  }
}

const handleStart = async () => {
  try {
    const res = await api.nginx.start()
    ElMessage.success(res.msg || '启动命令已发送')
    setTimeout(fetchStatus, 2000)
  } catch (error) {
    ElMessage.error('启动失败')
  }
}

const handleStop = async () => {
  await ElMessageBox.confirm('确定要停止 Nginx 服务吗？', '提示', { type: 'warning' })
  try {
    const res = await api.nginx.stop()
    ElMessage.success(res.msg || '停止命令已发送')
    setTimeout(fetchStatus, 1000)
  } catch (error) {
    ElMessage.error('停止失败')
  }
}

const handleRestart = async () => {
  try {
    const res = await api.nginx.restart()
    ElMessage.success(res.msg || '重载命令已发送')
    setTimeout(fetchStatus, 1000)
  } catch (error) {
    ElMessage.error('重载失败')
  }
}

const saveConfig = async () => {
  if (!configContent.value) return
  saving.value = true
  try {
    await api.nginx.saveConfig({ content: configContent.value })
    ElMessage.success('配置保存成功并已自动备份')
    fetchBackups()
  } catch (error) {
    ElMessage.error('保存失败: ' + error.message)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchHome()
  fetchStatus()
  fetchConfig()
  fetchBackups()
  connectLog()
  timer = setInterval(fetchStatus, 5000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (logWs) logWs.close()
})
</script>

<style scoped>
.nginx-manager {
  max-width: 1200px;
  margin: 0 auto;
}
</style>
