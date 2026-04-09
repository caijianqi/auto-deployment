<template>
  <div class="space-y-4 animate-fade-in">
    <!-- Header -->
    <div class="flex justify-between items-center mb-6">
      <div class="flex gap-4 items-center flex-1 max-w-md">
        <el-input
          v-model="searchQuery"
          placeholder="搜索应用名称..."
          prefix-icon="Search"
          clearable
        />
      </div>
      <el-button type="primary" icon="Plus" @click="handleAdd"
        >新增应用</el-button
      >
    </div>

    <!-- Main Table -->
    <el-card shadow="never" class="border-none">
      <el-table :data="filteredApps" v-loading="loading" stripe border>
        <el-table-column prop="name" label="应用名称" min-width="120" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag
              :type="row.type === 'JAR' ? 'success' : 'warning'"
              size="small"
              >{{ row.type }}</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column
          prop="path"
          label="部署路径"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="port" label="运行端口" width="120">
          <template #default="{ row }">
            <template v-if="row.actualPort && row.actualPort !== row.port">
              <el-tooltip
                :content="'配置端口: ' + (row.port || '未配置')"
                placement="top"
              >
                <span
                  class="font-bold text-orange-600 cursor-help underline decoration-dotted"
                  >{{ row.actualPort }}</span
                >
              </el-tooltip>
              <span class="text-[10px] text-orange-500 ml-1">(实测)</span>
            </template>
            <span v-else class="font-mono text-blue-600">{{
              row.port || "-"
            }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="当前版本" width="150">
          <template #default="{ row }">
            <el-tag
              v-if="row.version"
              size="small"
              type="info"
              effect="plain"
              class="font-mono"
            >
              {{ row.version }}
            </el-tag>
            <span v-else class="text-gray-400 text-xs">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="运行状态" width="120">
          <template #default="{ row }">
            <el-tag
              :type="row.type === 'VUE' ? 'primary' : getStatusType(row.status)"
              size="small"
            >
              {{ row.type === "VUE" ? "STATIC" : row.status || "STOPPED" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastDeployTime" label="最后部署" width="160">
          <template #default="{ row }">
            {{ formatTime(row.lastDeployTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button-group>
              <el-button
                v-if="row.type !== 'VUE'"
                :type="row.status === 'RUNNING' ? 'danger' : 'success'"
                size="small"
                @click="toggleApp(row)"
              >
                {{ row.status === "RUNNING" ? "停止" : "启动" }}
              </el-button>
              <el-button
                type="primary"
                size="small"
                :disabled="(row.status === 'RUNNING' || row.status === 'STARTING') && row.type !== 'VUE'"
                @click="handleUpdate(row)"
              >
                部署
              </el-button>
              <el-button
                size="small"
                :disabled="(row.status === 'RUNNING' || row.status === 'STARTING') && row.type !== 'VUE'"
                @click="handleEdit(row)"
              >
                编辑
              </el-button>
              <el-button size="small" @click="viewLogs(row)">日志</el-button>
              <el-button size="small" @click="viewBackups(row)">备份</el-button>
              <el-button
                size="small"
                type="info"
                plain
                @click="viewHistory(row)"
              >
                历史
              </el-button>
              <el-button
                type="danger"
                plain
                size="small"
                :disabled="(row.status === 'RUNNING' || row.status === 'STARTING') && row.type !== 'VUE'"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </el-button-group>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- App Dialogs and Drawers -->
    <AppEditDialog
      v-model="showAddDialog"
      ref="editDialogRef"
      :editing="!!editingApp"
      :form="appForm"
      :available-app-paths="availableAppPaths"
      :available-config-paths="availableConfigPaths"
      :jdks="jdks"
      @type-change="handleTypeChange"
      @file-change="handleNewFileChange"
      @save="saveApp"
    />

    <AppDeployDialog
      v-model="showUploadDialog"
      ref="deployDialogRef"
      :app-type="currentApp?.type"
      :uploading="uploading"
      @file-change="handleFileChange"
      @submit="submitUpload"
    />

    <AppHistoryDrawer
      v-model="showHistoryDrawer"
      :data="historyLogs"
      :loading="historyLoading"
    />

    <AppBackupDrawer
      v-model="showBackupDrawer"
      :data="backupList"
      :loading="backupLoading"
      :current-version="currentApp?.version"
      @rollback="rollbackApp"
      @delete="deleteBackup"
    />

    <AppLogDrawer
      v-model="showLogDrawer"
      :app-name="currentApp?.name"
      :logs="logLines"
      @close="handleLogClose"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from "vue";
import { Plus, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import api from "@/api";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import "dayjs/locale/zh-cn";

// Components
import AppEditDialog from "../components/app/AppEditDialog.vue";
import AppDeployDialog from "../components/app/AppDeployDialog.vue";
import AppHistoryDrawer from "../components/app/AppHistoryDrawer.vue";
import AppBackupDrawer from "../components/app/AppBackupDrawer.vue";
import AppLogDrawer from "../components/app/AppLogDrawer.vue";

dayjs.extend(relativeTime);
dayjs.locale("zh-cn");

const searchQuery = ref("");
const loading = ref(false);
const showAddDialog = ref(false);
const showUploadDialog = ref(false);
const showLogDrawer = ref(false);
const showHistoryDrawer = ref(false);
const showBackupDrawer = ref(false);
const uploading = ref(false);
const historyLoading = ref(false);
const backupLoading = ref(false);
const historyLogs = ref([]);
const backupList = ref([]);
const editingApp = ref(null);
const currentApp = ref(null);
const selectedFile = ref(null);
const selectedNewFile = ref(null);

const editDialogRef = ref(null);
const deployDialogRef = ref(null);

const logLines = ref([]);
let ws = null;

const appForm = reactive({
  id: null,
  name: "",
  type: "JAR",
  path: "",
  configPath: "",
  enableConfigPath: false,
  enablePort: false,
  port: 8081,
  jdkId: null,
});

const jdks = ref([]);
const fetchJdkList = async () => {
  try {
    const res = await api.config.getJdks();
    jdks.value = res.data || [];
  } catch (e) {
    console.error("Failed to load JDK list");
  }
};

const apps = ref([]);
const availableAppPaths = ref([]);
const availableConfigPaths = ref([]);

// Fetch Config Resources
const fetchResources = async (type) => {
  try {
    const resApp = await api.config.getResources({
      availableOnly: true,
      type,
      category: "APP",
    });
    availableAppPaths.value = resApp.data;
    if (editingApp.value && editingApp.value.type === type) {
      if (
        !availableAppPaths.value.some((p) => p.path === editingApp.value.path)
      ) {
        availableAppPaths.value.unshift({
          path: editingApp.value.path,
          remark: "当前所属路径",
        });
      }
    }

    if (type === "JAR") {
      const resConfig = await api.config.getResources({
        availableOnly: true,
        type: "JAR",
        category: "CONFIG",
      });
      availableConfigPaths.value = resConfig.data || [];
      if (editingApp.value && editingApp.value.configPath) {
        if (
          !availableConfigPaths.value.some(
            (p) => p.path === editingApp.value.configPath,
          )
        ) {
          availableConfigPaths.value.unshift({
            path: editingApp.value.configPath,
            remark: "当前所属配置路径",
          });
        }
      }
    } else {
      availableConfigPaths.value = [];
    }
  } catch (e) {
    ElMessage.error("获取可用路径池失败");
  }
};

const handleTypeChange = (val) => {
  appForm.path = "";
  appForm.configPath = "";
  appForm.enableConfigPath = false;
  appForm.enablePort = val === "JAR";
  fetchResources(val);
};

const filteredApps = computed(() => {
  return apps.value.filter((app) =>
    app.name.toLowerCase().includes(searchQuery.value.toLowerCase()),
  );
});

const fetchApps = async () => {
  loading.value = true;
  try {
    const res = await api.apps.list();
    apps.value = res.data || [];
  } catch (e) {
    ElMessage.error("获取应用列表失败");
  } finally {
    loading.value = false;
  }
};

const formatTime = (time) => {
  if (!time) return "尚未部署";
  return dayjs(time).fromNow();
};

const handleAdd = () => {
  editingApp.value = null;
  Object.assign(appForm, {
    id: null,
    name: "",
    type: "JAR",
    path: "",
    configPath: "",
    enableConfigPath: false,
    enablePort: false, // 默认不开启
    port: 8081,
    jdkId: null,
  });
  selectedNewFile.value = null;
  nextTick(() => {
    editDialogRef.value?.clearFiles();
  });
  fetchResources(appForm.type);
  showAddDialog.value = true;
};

const handleEdit = (row) => {
  editingApp.value = { ...row };
  Object.assign(appForm, row);
  appForm.enableConfigPath = !!row.configPath;
  appForm.enablePort = row.isPortEnabled === 1;
  fetchResources(appForm.type);
  fetchJdkList();
  showAddDialog.value = true;
};

const saveApp = async () => {
  if (!appForm.name) return ElMessage.warning("应用名称为必填项");
  if (!appForm.path) return ElMessage.warning("程序部署路径为必填项");
  if (appForm.enableConfigPath && !appForm.configPath)
    return ElMessage.warning("启用 YML 路径后，该项为必填项");
  if (appForm.enablePort && !appForm.port)
    return ElMessage.warning("启用运行端口后，该项为必填项");

  try {
    const payload = { ...appForm };
    if (!editingApp.value) delete payload.id;

    if (!appForm.enableConfigPath) payload.configPath = "";
    payload.isPortEnabled = appForm.enablePort ? 1 : 0;
    if (!appForm.enablePort) payload.port = null;

    const res = await api.apps.save(payload);

    if (!editingApp.value && selectedNewFile.value) {
      uploading.value = true;
      const formData = new FormData();
      formData.append("file", selectedNewFile.value);
      try {
        const deployRes = await api.apps.deploy(res.data.id, formData);
        if (deployRes.code === 200) {
          ElMessage.success("应用已创建并部署成功");
          selectedNewFile.value = null;
        } else {
          ElMessage.warning(
            "应用已创建，但部署失败: " + (deployRes.msg || "未知原因"),
          );
        }
      } catch (e) {
        const msg = e.response?.data?.msg || e.message || "网络或文件处理异常";
        ElMessage.warning("应用已创建，但初始部署失败: " + msg);
      } finally {
        uploading.value = false;
      }
    } else {
      ElMessage.success(
        editingApp.value ? "应用配置已同步" : "应用基本信息已保存",
      );
    }

    showAddDialog.value = false;
    fetchApps();
  } catch (e) {
    ElMessage.error("保存应用失败: " + (e.response?.data?.msg || e.message));
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm("确定要删除该应用吗？", "提示", {
    type: "warning",
  }).then(async () => {
    try {
      await api.apps.delete(row.id);
      ElMessage.success("删除成功");
      fetchApps();
    } catch (e) {
      ElMessage.error("删除失败: " + (e.response?.data?.msg || e.message));
    }
  });
};

const toggleApp = async (row) => {
  try {
    if (row.status === "RUNNING") {
      await api.apps.stop(row.id);
      ElMessage.success("停止指令已发送");
    } else {
      await api.apps.start(row.id);
      ElMessage.success("启动指令已发送");
    }
    setTimeout(fetchApps, 1000);
  } catch (e) {
    ElMessage.error("操作失败: " + (e.response?.data?.msg || e.message));
  }
};

const handleUpdate = (row) => {
  currentApp.value = row;
  selectedFile.value = null;
  nextTick(() => {
    deployDialogRef.value?.clearFiles();
  });
  showUploadDialog.value = true;
};

const getStatusType = (status) => {
  switch (status) {
    case "RUNNING":
      return "success";
    case "STARTING":
      return "warning";
    case "STOPPED":
      return "info";
    default:
      return "info";
  }
};

const handleNewFileChange = (file) => {
  if (!file) {
    selectedNewFile.value = null;
    return;
  }
  const ext = file.name.split(".").pop().toLowerCase();
  const targetExt = appForm.type === "JAR" ? "jar" : "zip";
  if (ext !== targetExt) {
    ElMessage.error(`请上传 .${targetExt} 格式的文件`);
    selectedNewFile.value = null;
    return;
  }
  selectedNewFile.value = file.raw;
};

const handleFileChange = (file) => {
  if (!file) {
    selectedFile.value = null;
    return;
  }
  const ext = file.name.split(".").pop().toLowerCase();
  const targetExt = currentApp.value.type === "JAR" ? "jar" : "zip";
  if (ext !== targetExt) {
    ElMessage.error(`请上传 .${targetExt} 格式的文件`);
    selectedFile.value = null;
    return;
  }
  selectedFile.value = file.raw;
};

const submitUpload = async () => {
  if (!selectedFile.value) return;
  uploading.value = true;
  const formData = new FormData();
  formData.append("file", selectedFile.value);
  try {
    const res = await api.apps.deploy(currentApp.value.id, formData);
    if (res.code === 200) {
      ElMessage.success("部署成功");
      showUploadDialog.value = false;
      fetchApps();
    } else {
      ElMessage.error("部署失败: " + (res.msg || "未知错误"));
    }
  } catch (e) {
    ElMessage.error("部署失败: " + (e.response?.data?.msg || e.message));
  } finally {
    uploading.value = false;
  }
};

const viewHistory = async (row) => {
  currentApp.value = row;
  showHistoryDrawer.value = true;
  historyLoading.value = true;
  try {
    const res = await api.logs.get(row.id);
    historyLogs.value = res.data;
  } catch (e) {
    ElMessage.error("获取历史记录失败");
  } finally {
    historyLoading.value = false;
  }
};

const viewBackups = async (row) => {
  currentApp.value = row;
  showBackupDrawer.value = true;
  fetchBackups();
};

const fetchBackups = async () => {
  backupLoading.value = true;
  try {
    const res = await api.backups.get(currentApp.value.id);
    backupList.value = res.data;
  } catch (e) {
    ElMessage.error("获取备份列表失败");
  } finally {
    backupLoading.value = false;
  }
};

const rollbackApp = async (backup) => {
  ElMessageBox.confirm(
    `确定要将应用 ${currentApp.value.name} 回滚到版本 ${backup.version} 吗？回滚期间应用将自动启停。`,
    "风险提示",
    {
      type: "warning",
      confirmButtonText: "确定回滚",
      confirmButtonClass: "el-button--warning",
    },
  ).then(async () => {
    try {
      backupLoading.value = true;
      await api.apps.rollback(currentApp.value.id, backup.id);
      ElMessage.success("回滚指令已下发并执行成功");
      showBackupDrawer.value = false;
      fetchApps();
    } catch (e) {
      ElMessage.error("回滚失败: " + (e.response?.data?.msg || e.message));
    } finally {
      backupLoading.value = false;
    }
  });
};

const deleteBackup = async (backup) => {
  ElMessageBox.confirm(
    "确定要删除该备份点吗？物理文件也将被同步清理。",
    "提示",
    { type: "danger" },
  ).then(async () => {
    try {
      await api.backups.delete(backup.id);
      ElMessage.success("备份已清理");
      fetchBackups();
    } catch (e) {
      ElMessage.error("删除备份失败");
    }
  });
};

const viewLogs = (row) => {
  currentApp.value = row;
  logLines.value = [];
  showLogDrawer.value = true;

  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  const host = window.location.host;
  const prefix = (import.meta.env.VITE_APP_API_PREFIX || '/api').replace(/\/$/, '')
  ws = new WebSocket(`${protocol}//${host}${prefix}/ws/logs/${row.id}`);

  ws.onmessage = (event) => {
    logLines.value.push(event.data);
    if (logLines.value.length > 500) logLines.value.shift();
  };
};

const handleLogClose = () => {
  if (ws) {
    ws.close();
    ws = null;
  }
  logLines.value = [];
};

onMounted(() => {
  fetchApps();
  fetchJdkList();
});
onUnmounted(handleLogClose);
</script>

<style scoped>
.font-mono {
  font-family: "Fira Code", "Roboto Mono", monospace;
}
</style>
