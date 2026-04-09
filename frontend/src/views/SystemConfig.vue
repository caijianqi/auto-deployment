<template>
  <div class="space-y-6 animate-fade-in">
    <!-- Application Path Section -->
    <el-card shadow="never" class="border-none">
      <template #header>
        <div class="flex justify-between items-center">
          <div>
            <div class="text-lg font-bold flex items-center gap-2">
              <el-icon color="#409eff"><Box /></el-icon>
              应用部署路径池
            </div>
            <div class="text-xs text-gray-400 mt-1">管理 JAR 包或 VUE 静态文件的存放位置</div>
          </div>
          <div class="flex gap-2">
            <el-button type="primary" plain @click="handleAddResource('APP', 'JAR')" icon="Plus">新增 JAR 路径</el-button>
            <el-button type="warning" plain @click="handleAddResource('APP', 'VUE')" icon="Plus">新增 VUE 路径</el-button>
          </div>
        </div>
      </template>

      <el-table :data="appResources" v-loading="resLoading" stripe border>
        <el-table-column prop="path" label="部署全路径" min-width="300" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.type === 'JAR' ? 'success' : 'warning'" size="small" effect="dark">
              {{ row.type === 'JAR' ? 'SpringBoot' : 'Vue' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isUsed" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isUsed ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.isUsed ? '已占用' : '可分配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" />
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditResource(row)">编辑</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDeleteRes(row)">
              <template #reference>
                <el-button type="danger" link size="small" :disabled="row.isUsed === 1">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- YAML Config Path Section -->
    <el-card shadow="never" class="border-none">
      <template #header>
        <div class="flex justify-between items-center">
          <div>
            <div class="text-lg font-bold flex items-center gap-2">
              <el-icon color="#67c23a"><Document /></el-icon>
              YAML 配置文件路径池
            </div>
            <div class="text-xs text-gray-400 mt-1">管理外部 application.yml 或 properties 的存放位置</div>
          </div>
          <el-button type="success" @click="handleAddResource('CONFIG', 'COMMON')" icon="Plus">新增 YAML 路径</el-button>
        </div>
      </template>

      <el-table :data="configResources" v-loading="resLoading" stripe border>
        <el-table-column prop="path" label="配置全路径" min-width="300" />
        <el-table-column prop="isUsed" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isUsed ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.isUsed ? '已占用' : '可分配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="150" />
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditResource(row)">管理</el-button>
            <el-button type="success" link size="small" @click="openYamlEditor(row)">编辑内容</el-button>
            <el-popconfirm title="确定删除？" @confirm="handleDeleteRes(row)">
              <template #reference>
                <el-button type="danger" link size="small" :disabled="row.isUsed === 1">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- JDK Management Section -->
    <el-card shadow="never" class="border-none">
      <template #header>
        <div class="flex justify-between items-center">
          <div>
            <div class="text-lg font-bold flex items-center gap-2">
              <el-icon color="#e6a23c"><Tools /></el-icon>
              JDK 环境管理
            </div>
            <div class="text-xs text-gray-400 mt-1">配置不同版本的 JDK 路径供 JAR 应用选择</div>
          </div>
          <el-button type="warning" @click="handleAddJdk" icon="Plus">新增 JDK 配置</el-button>
        </div>
      </template>

      <el-table :data="jdks" v-loading="jdkLoading" stripe border>
        <el-table-column prop="name" label="JDK 名称" width="180" />
        <el-table-column prop="path" label="Java 执行路径" min-width="300" />
        <el-table-column prop="createTime" label="录入时间" width="180">
          <template #default="{ row }">
            {{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEditJdk(row)">编辑</el-button>
            <el-popconfirm title="确定删除该 JDK 配置？" @confirm="handleDeleteJdk(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- Resource Dialog -->
    <el-dialog v-model="showResDialog" :title="dialogTitle" width="550px">
      <el-form :model="resForm" label-width="100px">
        <el-form-item :label="resForm.category === 'APP' ? '部署全路径' : '配置全路径'">
          <el-input v-model="resForm.path" :placeholder="resForm.category === 'APP' ? 'd:/apps/order.jar' : 'd:/configs/order.yml'" />
        </el-form-item>
        <el-form-item label="对应类型" v-if="resForm.category === 'APP'">
          <el-radio-group v-model="resForm.type">
            <el-radio-button value="JAR">SpringBoot (JAR)</el-radio-button>
            <el-radio-button value="VUE">Vue (Static)</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="资源备注">
          <el-input v-model="resForm.remark" placeholder="描述该路径的使用意图" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showResDialog = false">取消</el-button>
        <el-button type="primary" @click="saveResource">提交</el-button>
      </template>
    </el-dialog>

    <!-- JDK Dialog -->
    <el-dialog v-model="showJdkDialog" title="JDK 环境配置" width="550px">
      <el-form :model="jdkForm" label-width="100px">
        <el-form-item label="JDK 名称" required>
          <el-input v-model="jdkForm.name" placeholder="例如: JDK17 / OpenJDK8" />
        </el-form-item>
        <el-form-item label="执行路径" required>
          <el-input v-model="jdkForm.path" placeholder="Windows: D:\Java\bin\java.exe | Linux: /usr/bin/java" />
          <div class="text-xs text-gray-400 mt-1">请填写 java 可执行文件的完整绝对路径</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showJdkDialog = false">取消</el-button>
        <el-button type="primary" @click="saveJdk">保存</el-button>
      </template>
    </el-dialog>

    <!-- YAML Online Editor Dialog -->
    <el-dialog v-model="showEditor" :title="`YAML 编辑: ${currentPath}`" width="850px" destroy-on-close top="5vh">
      <div class="editor-container" v-loading="editorLoading">
        <codemirror
          v-model="editorContent"
          placeholder="正在读取文件内容或为空..."
          :style="{ height: '550px' }"
          :autofocus="true"
          :indent-with-tab="true"
          :tab-size="2"
          :extensions="extensions"
        />
      </div>
      <template #footer>
        <div class="flex justify-between items-center w-full">
          <span class="text-xs text-gray-400">支持 YAML 语法高亮与折叠</span>
          <div>
            <el-button @click="showEditor = false">取消</el-button>
            <el-button type="primary" @click="saveFileContent" :loading="saving">保存并同步物理文件</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Box, Document, Tools } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

// CodeMirror core
import Codemirror from 'vue-codemirror6'
import { yaml } from '@codemirror/lang-yaml'
import { oneDark } from '@codemirror/theme-one-dark'
import { basicSetup } from 'codemirror'

const extensions = [basicSetup, yaml(), oneDark]

const allResources = ref([])
const resLoading = ref(false)

const jdks = ref([])
const jdkLoading = ref(false)
const showJdkDialog = ref(false)
const jdkForm = reactive({
  id: null,
  name: '',
  path: ''
})

const appResources = computed(() => allResources.value.filter(r => r.category === 'APP' || !r.category))
const configResources = computed(() => allResources.value.filter(r => r.category === 'CONFIG'))

const showResDialog = ref(false)
const resForm = reactive({
  id: null,
  path: '',
  type: 'JAR',
  category: 'APP',
  remark: ''
})

const dialogTitle = computed(() => {
  return resForm.category === 'APP' ? '管理部署路径' : '管理配置路径'
})

// Editor State
const showEditor = ref(false)
const editorLoading = ref(false)
const saving = ref(false)
const currentPath = ref('')
const editorContent = ref('')

const loadData = async () => {
  resLoading.value = true
  jdkLoading.value = true
  try {
    const res = await api.config.getResources()
    allResources.value = res.data || []
    
    const jdkRes = await api.config.getJdks()
    jdks.value = jdkRes.data || []
  } catch (e) {
    ElMessage.error('加载路径资源池失败')
  } finally {
    resLoading.value = false
    jdkLoading.value = false
  }
}

const handleAddResource = (cat, type = 'JAR') => {
  Object.assign(resForm, { 
    id: null, 
    path: '', 
    type: type, 
    category: cat, 
    remark: '' 
  })
  showResDialog.value = true
}

const handleEditResource = (row) => {
  Object.assign(resForm, row)
  showResDialog.value = true
}

const saveResource = async () => {
  try {
    await api.config.saveResource(resForm)
    ElMessage.success('配置已保存')
    showResDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteRes = async (row) => {
  try {
    await api.config.deleteResource(row.id)
    ElMessage.success('资源已移除')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败，请检查资源是否被锁定')
  }
}

// JDK Logic
const handleAddJdk = () => {
  Object.assign(jdkForm, { id: null, name: '', path: '' })
  showJdkDialog.value = true
}

const handleEditJdk = (row) => {
  Object.assign(jdkForm, row)
  showJdkDialog.value = true
}

const saveJdk = async () => {
  if (!jdkForm.name || !jdkForm.path) {
    return ElMessage.warning('请填写完整的 JDK 名称和路径')
  }
  try {
    await api.config.saveJdk(jdkForm)
    ElMessage.success('JDK 配置已成功')
    showJdkDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error('保存 JDK 配置失败')
  }
}

const handleDeleteJdk = async (row) => {
  try {
    await api.config.deleteJdk(row.id)
    ElMessage.success('JDK 配置已删除')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

// Online Editor Logic
const openYamlEditor = async (row) => {
  currentPath.value = row.path
  editorContent.value = ''
  showEditor.value = true
  editorLoading.value = true
  try {
    const res = await api.config.getFileContent(row.path)
    if (res.code === 200) {
      editorContent.value = res.data || ''
    } else {
      ElMessage.warning(res.msg || '读取物理文件失败')
    }
  } catch (e) {
    ElMessage.warning('物理连接异常，保存时将尝试新建')
  } finally {
    editorLoading.value = false
  }
}

const saveFileContent = async () => {
  if (!currentPath.value) return
  saving.value = true
  try {
    await api.config.saveFileContent({
      path: currentPath.value,
      content: editorContent.value
    })
    ElMessage.success('YAML 内容已物理同步至磁盘')
    showEditor.value = false
  } catch (e) {
    ElMessage.error('同步失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style>
.editor-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
/* Force internal scrollbar for CodeMirror 6 */
.cm-editor {
  height: 100% !important;
}
.cm-scroller {
  overflow: auto !important;
}
/* Ensure code editor looks good within dialog */
.cm-editor {
  font-family: 'Fira Code', 'Roboto Mono', 'Courier New', monospace;
  font-size: 13px;
}
</style>
