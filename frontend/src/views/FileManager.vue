<template>
  <div class="space-y-4 animate-fade-in">
    <!-- Header/Breadcrumbs & Actions Bar -->
    <div class="bg-white p-3 rounded-xl border border-gray-100 shadow-sm flex flex-wrap items-center justify-between gap-3">
      <!-- Left: Navigation & Breadcrumbs -->
      <div class="flex items-center gap-2 flex-1 min-w-[280px] overflow-hidden">
        <el-tooltip content="返回上一级" placement="top">
          <el-button 
            icon="Back" 
            circle 
            size="small" 
            :disabled="isRoot" 
            @click="goBack"
            class="!border-gray-200 hover:!border-indigo-500"
          />
        </el-tooltip>
        
        <el-tooltip content="根目录 (计算机)" placement="top">
          <el-button 
            icon="HomeFilled" 
            circle 
            size="small" 
            @click="loadFiles('')"
            class="!border-gray-200 hover:!border-indigo-500"
          />
        </el-tooltip>

        <el-divider direction="vertical" />
        
        <el-breadcrumb separator="/" class="overflow-x-auto no-scrollbar whitespace-nowrap py-1">
          <el-breadcrumb-item 
            class="cursor-pointer hover:text-indigo-600 font-medium"
            @click="loadFiles('')"
          >
            计算机
          </el-breadcrumb-item>
          <el-breadcrumb-item 
            v-for="(item, index) in breadcrumbs" 
            :key="index"
            class="cursor-pointer hover:text-indigo-600 font-medium"
            @click="jumpTo(index)"
          >
            {{ item }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- Right: Action Buttons & Search Filter -->
      <div class="flex flex-wrap items-center gap-2">
        <!-- Search Filter -->
        <el-input
          v-model="searchKeyword"
          size="default"
          placeholder="搜索当前目录..."
          prefix-icon="Search"
          clearable
          class="!w-44"
        />

        <!-- New File Button -->
        <el-button 
          type="default" 
          size="default" 
          :disabled="isRoot"
          :icon="DocumentAdd" 
          @click="openNewFileDialog"
          class="!rounded-lg"
        >
          新建文件
        </el-button>

        <!-- New Folder Button -->
        <el-button 
          type="default" 
          size="default" 
          :disabled="isRoot"
          :icon="FolderAdd" 
          @click="openNewFolderDialog"
          class="!rounded-lg"
        >
          新建文件夹
        </el-button>

        <!-- Upload File Button -->
        <el-button 
          type="primary" 
          size="default" 
          :disabled="isRoot"
          :icon="Upload" 
          @click="showUploadDialog = true"
          class="!rounded-lg"
        >
          上传文件
        </el-button>

        <!-- Open Terminal in Dir -->
        <el-tooltip content="在当前目录打开 Web 终端" placement="top">
          <el-button 
            v-if="currentPath"
            type="info" 
            plain 
            size="default" 
            :icon="Terminal"
            @click="openInTerminal(currentPath)"
            class="!rounded-lg"
          >
            终端
          </el-button>
        </el-tooltip>

        <!-- Refresh -->
        <el-tooltip content="刷新当前目录" placement="top">
          <el-button 
            size="default" 
            :icon="Refresh" 
            @click="loadFiles(currentPath)"
            class="!rounded-lg"
          />
        </el-tooltip>
      </div>
    </div>

    <!-- Batch Actions Floating Bar -->
    <transition name="el-zoom-in-top">
      <div 
        v-if="selectedRows.length > 0"
        class="bg-indigo-50 border border-indigo-100 rounded-xl p-3 flex items-center justify-between shadow-sm animate-fade-in"
      >
        <div class="flex items-center gap-2 text-indigo-900 text-sm font-medium">
          <Check :size="16" class="text-indigo-600" />
          <span>已选中 <strong class="text-indigo-600">{{ selectedRows.length }}</strong> 项</span>
        </div>
        <div class="flex items-center gap-2">
          <el-button 
            type="primary" 
            size="small" 
            :icon="Box" 
            @click="handleBatchZip"
            class="!rounded-lg"
          >
            压缩为 ZIP
          </el-button>
          <el-button 
            type="danger" 
            size="small" 
            :icon="Delete" 
            @click="handleBatchDelete"
            class="!rounded-lg"
          >
            批量删除
          </el-button>
          <el-button size="small" @click="clearSelection" class="!rounded-lg">
            取消选择
          </el-button>
        </div>
      </div>
    </transition>

    <!-- Main File Table Card -->
    <el-card shadow="never" class="!rounded-xl border border-gray-100">
      <el-table 
        ref="fileTableRef"
        v-loading="loading"
        :data="filteredFileList" 
        stripe 
        highlight-current-row
        @selection-change="handleSelectionChange"
        @row-dblclick="handleRowDblClick"
        height="calc(100vh - 250px)"
        class="w-full"
      >
        <!-- Selection Checkbox -->
        <el-table-column 
          v-if="!isRoot" 
          type="selection" 
          width="45" 
          align="center" 
        />

        <!-- File Name & Icon -->
        <el-table-column label="名称" min-width="320" show-overflow-tooltip sortable prop="name">
          <template #default="{ row }">
            <div class="flex items-center gap-2.5 cursor-pointer select-none group py-0.5" @click="handleRowClick(row)">
              <!-- Dynamic File/Folder Icon -->
              <span class="text-lg flex items-center justify-center">
                <el-icon v-if="row.isDir" color="#f59e0b" :size="20"><Folder /></el-icon>
                <el-icon v-else-if="isZipFile(row.name)" color="#8b5cf6" :size="20"><Box /></el-icon>
                <el-icon v-else-if="isImage(row.name)" color="#10b981" :size="20"><Picture /></el-icon>
                <el-icon v-else-if="isCodeFile(row.name)" color="#3b82f6" :size="20"><Document /></el-icon>
                <el-icon v-else color="#9ca3af" :size="20"><Document /></el-icon>
              </span>
              <span class="text-sm font-medium text-gray-700 group-hover:text-indigo-600 transition-colors">
                {{ row.name }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- Size -->
        <el-table-column prop="size" label="大小" width="130" sortable>
          <template #default="{ row }">
            <span class="text-xs text-gray-500 font-mono">
              {{ row.isDir ? '-' : formatSize(row.size) }}
            </span>
          </template>
        </el-table-column>

        <!-- Last Modified Date -->
        <el-table-column prop="lastModified" label="修改时间" width="180" sortable>
          <template #default="{ row }">
            <span class="text-xs text-gray-500 font-mono">
              {{ formatDate(row.lastModified) }}
            </span>
          </template>
        </el-table-column>

        <!-- Actions Column -->
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-1">
              <!-- Edit / View File -->
              <el-button 
                v-if="!row.isDir && !isImage(row.name)"
                type="primary" 
                link 
                size="small" 
                @click.stop="openEditDialog(row)"
              >
                编辑
              </el-button>

              <!-- Image Preview -->
              <el-button 
                v-if="!row.isDir && isImage(row.name)"
                type="primary" 
                link 
                size="small" 
                @click.stop="previewFile(row)"
              >
                预览
              </el-button>

              <!-- Enter Directory -->
              <el-button 
                v-if="row.isDir"
                type="primary" 
                link 
                size="small" 
                @click.stop="loadFiles(row.path)"
              >
                打开
              </el-button>

              <!-- Download File -->
              <el-button 
                v-if="!row.isDir"
                type="success" 
                link 
                size="small" 
                @click.stop="downloadFile(row)"
              >
                下载
              </el-button>

              <!-- Unzip Archive -->
              <el-button 
                v-if="!row.isDir && isZipFile(row.name)"
                type="warning" 
                link 
                size="small" 
                @click.stop="handleUnzip(row)"
              >
                解压
              </el-button>

              <!-- Rename -->
              <el-button 
                v-if="!isRoot"
                type="default" 
                link 
                size="small" 
                @click.stop="openRenameDialog(row)"
              >
                重命名
              </el-button>

              <!-- Delete -->
              <el-button 
                v-if="!isRoot"
                type="danger" 
                link 
                size="small" 
                @click.stop="handleDeleteSingle(row)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 1. Edit & Preview Dialog (with CodeMirror) -->
    <el-dialog
      v-model="editorDialog.visible"
      :title="editorDialog.isEditing ? ('编辑文件: ' + editorDialog.name) : ('预览: ' + editorDialog.name)"
      width="85%"
      destroy-on-close
      top="4vh"
      class="!rounded-2xl"
    >
      <div v-loading="editorDialog.loading" class="flex flex-col gap-3">
        <!-- Top Toolbar inside Dialog -->
        <div class="flex items-center justify-between bg-gray-50 px-4 py-2 rounded-lg border border-gray-200">
          <div class="flex items-center gap-2 text-xs text-gray-500 font-mono truncate max-w-[60%]">
            <span class="px-2 py-0.5 bg-gray-200 rounded text-gray-700 font-semibold">{{ editorDialog.extension.toUpperCase() || 'TXT' }}</span>
            <span class="truncate">{{ editorDialog.path }}</span>
          </div>
          <div class="flex items-center gap-3">
            <el-switch 
              v-model="editorDialog.isEditing" 
              active-text="编辑模式" 
              inactive-text="只读预览"
              inline-prompt
            />
            <el-button 
              type="primary" 
              size="small" 
              :loading="editorDialog.saving"
              :disabled="!editorDialog.isEditing"
              @click="saveEditorContent"
              class="!rounded-lg"
            >
              💾 保存修改
            </el-button>
          </div>
        </div>

        <!-- Image Preview -->
        <div v-if="editorDialog.isImg" class="flex justify-center items-center p-6 bg-gray-900 rounded-xl min-h-[350px]">
          <el-image 
            :src="editorDialog.imgUrl" 
            fit="contain" 
            :preview-src-list="[editorDialog.imgUrl]"
            class="max-h-[600px] shadow-lg rounded"
          >
            <template #error>
              <div class="flex flex-col items-center text-gray-400">
                <el-icon :size="48"><Picture /></el-icon>
                <span class="mt-2 text-sm">图片加载失败</span>
              </div>
            </template>
          </el-image>
        </div>
        
        <!-- CodeMirror Editor -->
        <div v-else class="rounded-xl overflow-hidden border border-gray-800 shadow-inner">
          <codemirror
            v-model="editorDialog.content"
            :style="{ height: '620px' }"
            :autofocus="true"
            :indent-with-tab="true"
            :tab-size="2"
            :extensions="extensions"
            :disabled="!editorDialog.isEditing"
          />
        </div>
      </div>

      <template #footer>
        <div class="flex justify-between items-center">
          <span class="text-xs text-gray-400">提示：修改完成后点击「保存修改」即可实时更新服务器文件内容</span>
          <div class="flex gap-2">
            <el-button @click="editorDialog.visible = false">关闭</el-button>
            <el-button 
              v-if="editorDialog.isEditing && !editorDialog.isImg" 
              type="primary" 
              :loading="editorDialog.saving" 
              @click="saveEditorContent"
            >
              保存并关闭
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 2. Upload File Dialog -->
    <el-dialog
      v-model="showUploadDialog"
      title="上传文件至当前目录"
      width="520px"
      destroy-on-close
      append-to-body
      class="!rounded-2xl"
    >
      <div class="space-y-4">
        <div class="bg-gray-50 p-3 rounded-lg border border-gray-100 text-xs text-gray-600 font-mono truncate">
          目标目录: <strong>{{ currentPath }}</strong>
        </div>

        <el-upload
          ref="uploadRef"
          drag
          multiple
          :auto-upload="false"
          :on-change="handleUploadFileChange"
          :on-remove="handleUploadFileRemove"
          :file-list="uploadFileList"
          class="w-full"
        >
          <el-icon class="el-icon--upload text-indigo-500"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em class="text-indigo-600">点击选择文件</em>
          </div>
          <template #tip>
            <div class="el-upload__tip text-xs text-gray-400">
              支持单次选择多个文件同时上传
            </div>
          </template>
        </el-upload>

        <div class="flex items-center justify-between pt-2">
          <el-checkbox v-model="uploadOverwrite">若存在同名文件则直接覆盖</el-checkbox>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button 
            type="primary" 
            :loading="uploading" 
            :disabled="uploadFileList.length === 0"
            @click="submitUploadFiles"
          >
            开始上传 ({{ uploadFileList.length }})
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 3. New Folder Dialog -->
    <el-dialog
      v-model="newFolderDialog.visible"
      title="新建文件夹"
      width="420px"
      destroy-on-close
      append-to-body
      class="!rounded-2xl"
    >
      <div class="space-y-3">
        <div class="text-xs text-gray-500">在当前目录 <strong>{{ currentPath }}</strong> 下创建新文件夹：</div>
        <el-input 
          v-model="newFolderDialog.name" 
          placeholder="请输入文件夹名称 (如 my_folder)" 
          clearable 
          @keyup.enter="submitCreateFolder"
        />
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="newFolderDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="newFolderDialog.loading" @click="submitCreateFolder">确定创建</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 4. New File Dialog -->
    <el-dialog
      v-model="newFileDialog.visible"
      title="新建空白文件"
      width="450px"
      destroy-on-close
      append-to-body
      class="!rounded-2xl"
    >
      <div class="space-y-3">
        <div class="text-xs text-gray-500">在当前目录 <strong>{{ currentPath }}</strong> 下创建新文件：</div>
        <el-input 
          v-model="newFileDialog.name" 
          placeholder="请输入文件名及后缀 (如 script.sh 或 config.json)" 
          clearable 
        />
        <el-input
          v-model="newFileDialog.content"
          type="textarea"
          :rows="4"
          placeholder="可选：初始文本内容..."
        />
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="newFileDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="newFileDialog.loading" @click="submitCreateFile">确定创建</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 5. Rename Dialog -->
    <el-dialog
      v-model="renameDialog.visible"
      title="重命名"
      width="420px"
      destroy-on-close
      append-to-body
      class="!rounded-2xl"
    >
      <div class="space-y-3">
        <div class="text-xs text-gray-500">原名称: <strong>{{ renameDialog.oldName }}</strong></div>
        <el-input 
          v-model="renameDialog.newName" 
          placeholder="请输入新的名称" 
          clearable 
          @keyup.enter="submitRename"
        />
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="renameDialog.visible = false">取消</el-button>
          <el-button type="primary" :loading="renameDialog.loading" @click="submitRename">确认修改</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { 
  Folder, 
  Document, 
  Back, 
  Picture, 
  HomeFilled, 
  Refresh, 
  Search, 
  Delete, 
  Upload, 
  DocumentAdd, 
  FolderAdd, 
  UploadFilled 
} from '@element-plus/icons-vue'
import { Terminal, Box, Check } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import dayjs from 'dayjs'

const router = useRouter()

const openInTerminal = (path) => {
  if (!path) return
  router.push({
    path: '/terminal',
    query: { dir: path }
  })
}

// CodeMirror for preview & edit
import Codemirror from 'vue-codemirror6'
import { oneDark } from '@codemirror/theme-one-dark'
import { basicSetup } from 'codemirror'

// Language Extensions
import { javascript } from '@codemirror/lang-javascript'
import { java } from '@codemirror/lang-java'
import { yaml } from '@codemirror/lang-yaml'
import { json } from '@codemirror/lang-json'
import { xml } from '@codemirror/lang-xml'
import { html } from '@codemirror/lang-html'
import { css } from '@codemirror/lang-css'
import { python } from '@codemirror/lang-python'
import { markdown } from '@codemirror/lang-markdown'
import { StreamLanguage } from '@codemirror/language'
import { shell } from '@codemirror/legacy-modes/mode/shell'

const extensions = shallowRef([basicSetup, oneDark])

// Main File List States
const currentPath = ref('')
const fileList = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const selectedRows = ref([])
const fileTableRef = ref(null)

// Filtered File List based on search keyword
const filteredFileList = computed(() => {
  if (!searchKeyword.value.trim()) {
    return fileList.value
  }
  const kw = searchKeyword.value.trim().toLowerCase()
  return fileList.value.filter(item => item.name.toLowerCase().includes(kw))
})

// Check if current view is Root (Computer)
const isRoot = computed(() => !currentPath.value)

// Breadcrumbs Parsing
const breadcrumbs = computed(() => {
  if (!currentPath.value) return []
  return currentPath.value.split(/[\\\/]/).filter(p => p !== '')
})

// Check file types
const isImage = (filename) => {
  if (!filename) return false
  const ext = filename.split('.').pop().toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'ico', 'bmp'].includes(ext)
}

const isZipFile = (filename) => {
  if (!filename) return false
  const ext = filename.split('.').pop().toLowerCase()
  return ['zip', 'jar', 'tar', 'gz', 'tgz', 'rar', '7z'].includes(ext)
}

const isCodeFile = (filename) => {
  if (!filename) return false
  const ext = filename.split('.').pop().toLowerCase()
  return ['java', 'vue', 'js', 'ts', 'jsx', 'tsx', 'json', 'yml', 'yaml', 'xml', 'html', 'css', 'scss', 'py', 'sh', 'bat', 'properties', 'sql', 'md', 'log'].includes(ext)
}

const getLanguageExtension = (filename) => {
  if (!filename) return null
  const ext = filename.split('.').pop().toLowerCase()
  
  const map = {
    'js': javascript,
    'ts': javascript,
    'mjs': javascript,
    'cjs': javascript,
    'java': java,
    'yml': yaml,
    'yaml': yaml,
    'json': json,
    'xml': xml,
    'html': html,
    'htm': html,
    'vue': html,
    'css': css,
    'scss': css,
    'less': css,
    'py': python,
    'md': markdown,
    'sh': () => StreamLanguage.define(shell),
    'bat': () => StreamLanguage.define(shell),
    'properties': () => StreamLanguage.define(shell),
    'log': () => StreamLanguage.define(shell)
  }

  const langFunc = map[ext]
  return langFunc ? langFunc() : null
}

// Load Files in Directory
const loadFiles = async (path = '') => {
  loading.value = true
  currentPath.value = path
  selectedRows.value = []
  searchKeyword.value = ''

  try {
    const res = await api.file.list(path)
    if (res.code === 200) {
      fileList.value = res.data
    } else {
      ElMessage.error(res.msg || '获取文件列表失败')
    }
  } catch (e) {
    ElMessage.error('获取文件列表失败')
  } finally {
    loading.value = false
  }
}

// Double click row
const handleRowDblClick = (row) => {
  if (row.isDir) {
    loadFiles(row.path)
  } else {
    openEditDialog(row)
  }
}

// Single click row on name
const handleRowClick = (row) => {
  if (row.isDir) {
    loadFiles(row.path)
  }
}

// Table Selection
const handleSelectionChange = (val) => {
  selectedRows.value = val
}

const clearSelection = () => {
  if (fileTableRef.value) {
    fileTableRef.value.clearSelection()
  }
  selectedRows.value = []
}

// Navigation Back
const goBack = () => {
  if (!currentPath.value) return
  
  if (/^[A-Z]:[\\\/]?$/i.test(currentPath.value)) {
    loadFiles('')
    return
  }

  let parent = currentPath.value.replace(/[\\\/][^\\\/]+[\\\/]?$/, '')
  if (parent === currentPath.value) {
    if (currentPath.value.startsWith('/') && currentPath.value.length > 1) {
      loadFiles('/')
    } else {
      loadFiles('')
    }
  } else {
    if (currentPath.value.startsWith('/') && !parent) {
      parent = '/'
    }
    loadFiles(parent)
  }
}

// Jump to Breadcrumb Index
const jumpTo = (index) => {
  let parts = breadcrumbs.value.slice(0, index + 1)
  let isLinux = currentPath.value.startsWith('/')
  let path = ''
  
  if (isLinux) {
    path = '/' + parts.join('/')
  } else {
    path = parts.join('\\')
    if (path.length === 2 && path.endsWith(':')) {
      path += '\\'
    }
  }
  loadFiles(path)
}

// ----------------------------------------------------
// 1. Editor & Preview Modal State
// ----------------------------------------------------
const editorDialog = reactive({
  visible: false,
  loading: false,
  saving: false,
  isEditing: true,
  name: '',
  path: '',
  extension: '',
  content: '',
  isImg: false,
  imgUrl: ''
})

const openEditDialog = async (row) => {
  editorDialog.name = row.name
  editorDialog.path = row.path
  editorDialog.extension = row.name.split('.').pop().toLowerCase()
  editorDialog.content = ''
  editorDialog.isImg = isImage(row.name)
  editorDialog.imgUrl = ''
  editorDialog.isEditing = true
  editorDialog.visible = true
  editorDialog.loading = true

  // Image Preview
  if (editorDialog.isImg) {
    const token = localStorage.getItem('satoken') || ''
    const baseUrl = api.baseURL.endsWith('/') ? api.baseURL : api.baseURL + '/'
    editorDialog.imgUrl = `${baseUrl}file/download?path=${encodeURIComponent(row.path)}&satoken=${token}`
    editorDialog.loading = false
    return
  }

  // Code Highlight Mode
  const lang = getLanguageExtension(row.name)
  if (lang) {
    extensions.value = [basicSetup, oneDark, lang]
  } else {
    extensions.value = [basicSetup, oneDark]
  }

  try {
    const res = await api.file.getContent(row.path)
    if (res.code === 200) {
      editorDialog.content = res.data
    } else {
      ElMessage.warning(res.msg || '无法读取文件内容')
      editorDialog.visible = false
    }
  } catch (e) {
    ElMessage.error('读取文件内容失败')
    editorDialog.visible = false
  } finally {
    editorDialog.loading = false
  }
}

const previewFile = (row) => {
  openEditDialog(row)
}

const saveEditorContent = async () => {
  if (!editorDialog.path) return
  editorDialog.saving = true
  try {
    const res = await api.file.saveContent({
      path: editorDialog.path,
      content: editorDialog.content
    })
    if (res.code === 200) {
      ElMessage.success('保存成功')
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    editorDialog.saving = false
  }
}

// ----------------------------------------------------
// 2. Download File
// ----------------------------------------------------
const downloadFile = (row) => {
  const token = localStorage.getItem('satoken') || ''
  const baseUrl = api.baseURL.endsWith('/') ? api.baseURL : api.baseURL + '/'
  const downloadUrl = `${baseUrl}file/download?path=${encodeURIComponent(row.path)}&satoken=${token}`
  window.open(downloadUrl, '_blank')
}

// ----------------------------------------------------
// 3. New Folder Dialog
// ----------------------------------------------------
const newFolderDialog = reactive({
  visible: false,
  loading: false,
  name: ''
})

const openNewFolderDialog = () => {
  if (isRoot.value) {
    ElMessage.warning('请先进入具体磁盘或目录后再创建文件夹')
    return
  }
  newFolderDialog.name = ''
  newFolderDialog.visible = true
}

const submitCreateFolder = async () => {
  if (!newFolderDialog.name.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  newFolderDialog.loading = true
  try {
    const res = await api.file.createFolder({
      path: currentPath.value,
      folderName: newFolderDialog.name.trim()
    })
    if (res.code === 200) {
      ElMessage.success('文件夹创建成功')
      newFolderDialog.visible = false
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建文件夹失败')
  } finally {
    newFolderDialog.loading = false
  }
}

// ----------------------------------------------------
// 4. New File Dialog
// ----------------------------------------------------
const newFileDialog = reactive({
  visible: false,
  loading: false,
  name: '',
  content: ''
})

const openNewFileDialog = () => {
  if (isRoot.value) {
    ElMessage.warning('请先进入具体磁盘或目录后再创建文件')
    return
  }
  newFileDialog.name = ''
  newFileDialog.content = ''
  newFileDialog.visible = true
}

const submitCreateFile = async () => {
  if (!newFileDialog.name.trim()) {
    ElMessage.warning('请输入文件名')
    return
  }
  newFileDialog.loading = true
  try {
    const res = await api.file.createFile({
      path: currentPath.value,
      fileName: newFileDialog.name.trim(),
      content: newFileDialog.content
    })
    if (res.code === 200) {
      ElMessage.success('文件创建成功')
      newFileDialog.visible = false
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '创建失败')
    }
  } catch (e) {
    ElMessage.error('创建文件失败')
  } finally {
    newFileDialog.loading = false
  }
}

// ----------------------------------------------------
// 5. Upload Files Modal
// ----------------------------------------------------
const showUploadDialog = ref(false)
const uploading = ref(false)
const uploadOverwrite = ref(false)
const uploadFileList = ref([])
const uploadRef = ref(null)

const handleUploadFileChange = (file, fileList) => {
  uploadFileList.value = fileList
}

const handleUploadFileRemove = (file, fileList) => {
  uploadFileList.value = fileList
}

const submitUploadFiles = async () => {
  if (uploadFileList.value.length === 0) return
  uploading.value = true

  let successCount = 0
  let errorCount = 0

  for (const item of uploadFileList.value) {
    try {
      const formData = new FormData()
      formData.append('file', item.raw)
      formData.append('path', currentPath.value)
      formData.append('overwrite', uploadOverwrite.value)

      const res = await api.file.upload(formData)
      if (res.code === 200) {
        successCount++
      } else {
        errorCount++
      }
    } catch (e) {
      errorCount++
    }
  }

  uploading.value = false
  showUploadDialog.value = false
  uploadFileList.value = []

  if (errorCount === 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
  } else {
    ElMessage.warning(`上传完成: ${successCount} 成功, ${errorCount} 失败 (可能文件已存在)`)
  }
  loadFiles(currentPath.value)
}

// ----------------------------------------------------
// 6. Rename Dialog
// ----------------------------------------------------
const renameDialog = reactive({
  visible: false,
  loading: false,
  path: '',
  oldName: '',
  newName: ''
})

const openRenameDialog = (row) => {
  renameDialog.path = row.path
  renameDialog.oldName = row.name
  renameDialog.newName = row.name
  renameDialog.visible = true
}

const submitRename = async () => {
  if (!renameDialog.newName.trim()) {
    ElMessage.warning('请输入有效名称')
    return
  }
  if (renameDialog.newName.trim() === renameDialog.oldName) {
    renameDialog.visible = false
    return
  }
  renameDialog.loading = true
  try {
    const res = await api.file.rename({
      path: renameDialog.path,
      newName: renameDialog.newName.trim()
    })
    if (res.code === 200) {
      ElMessage.success('重命名成功')
      renameDialog.visible = false
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '重命名失败')
    }
  } catch (e) {
    ElMessage.error('重命名失败')
  } finally {
    renameDialog.loading = false
  }
}

// ----------------------------------------------------
// 7. Delete Operations
// ----------------------------------------------------
const handleDeleteSingle = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除 ${row.isDir ? '文件夹' : '文件'}「${row.name}」吗？此操作不可恢复！`,
      '危险删除警告',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    const res = await api.file.delete({ path: row.path })
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (e) {
    // canceled
  }
}

const handleBatchDelete = async () => {
  if (selectedRows.value.length === 0) return

  try {
    await ElMessageBox.confirm(
      `确定要批量删除选中的 ${selectedRows.value.length} 个文件/文件夹吗？此操作不可逆！`,
      '批量删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    const paths = selectedRows.value.map(r => r.path)
    const res = await api.file.batchDelete({ paths })
    if (res.code === 200) {
      ElMessage.success(res.msg || '批量删除完成')
      clearSelection()
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '批量删除失败')
    }
  } catch (e) {
    // canceled
  }
}

// ----------------------------------------------------
// 8. Archive & Unzip Operations
// ----------------------------------------------------
const handleUnzip = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要将压缩包「${row.name}」解压到当前目录吗？`,
      '解压确认',
      { confirmButtonText: '立即解压', cancelButtonText: '取消', type: 'info' }
    )

    const loadingInstance = ElMessage.info({ message: '正在解压中，请稍候...', duration: 0 })
    try {
      const res = await api.file.unzip({
        path: row.path,
        targetDir: currentPath.value
      })
      loadingInstance.close()
      if (res.code === 200) {
        ElMessage.success('解压成功')
        loadFiles(currentPath.value)
      } else {
        ElMessage.error(res.msg || '解压失败')
      }
    } catch (err) {
      loadingInstance.close()
      ElMessage.error('解压发生异常')
    }
  } catch (e) {}
}

const handleBatchZip = async () => {
  if (selectedRows.value.length === 0) return

  const paths = selectedRows.value.map(r => r.path)
  const loadingInstance = ElMessage.info({ message: '正在压缩中，请稍候...', duration: 0 })
  try {
    const res = await api.file.zip({ paths })
    loadingInstance.close()
    if (res.code === 200) {
      ElMessage.success(res.msg || '压缩成功')
      clearSelection()
      loadFiles(currentPath.value)
    } else {
      ElMessage.error(res.msg || '压缩失败')
    }
  } catch (e) {
    loadingInstance.close()
    ElMessage.error('压缩发生异常')
  }
}

// Utilities
const formatSize = (size) => {
  if (size === null || size === undefined) return '-'
  if (size === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(size) / Math.log(k))
  return parseFloat((size / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatDate = (date) => {
  if (!date) return '-'
  return dayjs(date).format('YYYY-MM-DD HH:mm:ss')
}

onMounted(() => {
  loadFiles('')
})
</script>

<style scoped>
.animate-fade-in {
  animation: fadeIn 0.25s ease-in-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
