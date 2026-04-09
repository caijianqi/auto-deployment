<template>
  <div class="space-y-4 animate-fade-in">
    <!-- Header/Breadcrumbs -->
    <div class="flex items-center justify-between gap-4 bg-white p-3 rounded-lg border border-gray-100 shadow-sm">
      <div class="flex items-center gap-2 flex-1 overflow-hidden">
        <el-button 
          icon="Back" 
          circle 
          size="small" 
          :disabled="isRoot" 
          @click="goBack"
        />
        <el-divider direction="vertical" />
        <el-breadcrumb separator="/">
          <el-breadcrumb-item 
            v-for="(item, index) in breadcrumbs" 
            :key="index"
            class="cursor-pointer hover:text-blue-500"
            @click="jumpTo(index)"
          >
            {{ item || '计算机' }}
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <div class="text-xs text-gray-400">
        {{ currentPath || '根目录' }}
      </div>
    </div>

    <!-- Main Table -->
    <el-card shadow="never" class="border-none">
      <el-table 
        v-loading="loading"
        :data="fileList" 
        stripe 
        border
        highlight-current-row
        @row-dblclick="handleRowDblClick"
        height="calc(100vh - 240px)"
      >
        <el-table-column label="名称" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="flex items-center gap-2 cursor-pointer">
              <el-icon v-if="row.isDir" color="#E6a23c" :size="20"><Folder /></el-icon>
              <el-icon v-else color="#909399" :size="20"><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="120">
          <template #default="{ row }">
            {{ formatSize(row.size) }}
          </template>
        </el-table-column>
        <el-table-column prop="lastModified" label="修改时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastModified) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button 
              v-if="!row.isDir"
              type="primary" 
              link 
              size="small" 
              @click="previewFile(row)"
            >
              预览
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Preview Dialog -->
    <el-dialog
      v-model="preview.visible"
      :title="'预览: ' + preview.name"
      width="800px"
      destroy-on-close
      top="5vh"
    >
      <div v-loading="preview.loading" class="rounded-lg overflow-hidden border border-gray-700 bg-gray-900">
        <!-- Image Preview -->
        <div v-if="preview.isImg" class="flex justify-center items-center p-4 bg-gray-100 min-h-[300px]">
          <el-image 
            :src="preview.imgUrl" 
            fit="contain" 
            :preview-src-list="[preview.imgUrl]"
            class="max-h-[600px] shadow-lg"
          >
            <template #error>
              <div class="flex flex-col items-center text-gray-400">
                <el-icon :size="48"><Picture /></el-icon>
                <span class="mt-2">图片加载失败</span>
              </div>
            </template>
          </el-image>
        </div>
        
        <!-- Code/Text Preview -->
        <codemirror
          v-else
          v-model="preview.content"
          :style="{ height: '600px' }"
          :autofocus="true"
          :indent-with-tab="true"
          :tab-size="2"
          :extensions="extensions"
          disabled
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive, shallowRef } from 'vue'
import { Folder, Document, Back, Picture } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '@/api'
import dayjs from 'dayjs'

// CodeMirror for preview
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

const currentPath = ref('')
const fileList = ref([])
const loading = ref(false)

const preview = reactive({
  visible: false,
  loading: false,
  name: '',
  content: '',
  isImg: false,
  imgUrl: ''
})

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
    'properties': () => StreamLanguage.define(shell), // properties can fall back to shell or properties mode
    'log': () => StreamLanguage.define(shell) // logs often benefit from shell-like highlighting
  }

  const langFunc = map[ext]
  return langFunc ? langFunc() : null
}

// 计算面包屑
const breadcrumbs = computed(() => {
  if (!currentPath.value) return []
  // 同时支持 Windows/Linux 分隔符
  let parts = currentPath.value.split(/[\\\/]/).filter(p => p !== '')
  // 对于 Windows 盘符，不需要前导斜杠，对于 Linux，它是空的或根
  if (currentPath.value.startsWith('/') && parts.length > 0) {
      return ['', ...parts]
  }
  return parts
})

const isRoot = computed(() => !currentPath.value)

const loadFiles = async (path = '') => {
  loading.value = true
  currentPath.value = path
  try {
    const res = await api.file.list(path)
    if (res.code === 200) {
      fileList.value = res.data
    } else {
      ElMessage.error(res.msg)
    }
  } catch (e) {
    ElMessage.error('获取文件列表失败')
  } finally {
    loading.value = false
  }
}

const handleRowDblClick = (row) => {
  if (row.isDir) {
    loadFiles(row.path)
  } else {
    previewFile(row)
  }
}

const goBack = () => {
    if (!currentPath.value) return
    
    // 处理 Windows 盘符如 C:\
    if (/^[A-Z]:[\\\/]?$/i.test(currentPath.value)) {
        loadFiles('')
        return
    }

    // 取得父目录
    let parent = currentPath.value.replace(/[\\\/][^\\\/]+[\\\/]?$/, '')
    if (parent === currentPath.value) {
        // 如果没变，说明到顶级了 (Linux / 或 Windows 盘符)
        if (currentPath.value.startsWith('/') && currentPath.value.length > 1) {
            loadFiles('/')
        } else {
            loadFiles('')
        }
    } else {
        // Linux 根目录处理
        if (currentPath.value.startsWith('/') && !parent) {
            parent = '/'
        }
        loadFiles(parent)
    }
}

const jumpTo = (index) => {
    if (index === 0 && breadcrumbs.value[0] === '') {
        loadFiles('/')
        return
    }
    let parts = breadcrumbs.value.slice(0, index + 1)
    let isLinux = currentPath.value.startsWith('/')
    let path = ''
    
    if (isLinux) {
        path = '/' + parts.filter(p => p !== '').join('/')
    } else {
        path = parts.join('\\')
        // 处理盘符 C: -> C:\
        if (path.length === 2 && path.endsWith(':')) {
            path += '\\'
        }
    }
    loadFiles(path)
}

const isImage = (filename) => {
  if (!filename) return false
  const ext = filename.split('.').pop().toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'ico', 'bmp'].includes(ext)
}

const previewFile = async (row) => {
  preview.name = row.name
  preview.content = ''
  preview.isImg = isImage(row.name)
  preview.imgUrl = ''
  preview.visible = true
  preview.loading = true

  // 处理图片预览
  if (preview.isImg) {
    const token = localStorage.getItem('satoken') || ''
    // 构造下载接口 URL，注意 encodeURIComponent 处理路径中的特殊字符
    const baseUrl = api.baseURL.endsWith('/') ? api.baseURL : api.baseURL + '/'
    preview.imgUrl = `${baseUrl}file/download?path=${encodeURIComponent(row.path)}&satoken=${token}`
    preview.loading = false
    return
  }

  // 设置语言高亮
  const lang = getLanguageExtension(row.name)
  if (lang) {
    extensions.value = [basicSetup, oneDark, lang]
  } else {
    extensions.value = [basicSetup, oneDark]
  }

  try {
    const res = await api.file.getContent(row.path)
    if (res.code === 200) {
      preview.content = res.data
    } else {
      ElMessage.warning(res.msg)
      preview.visible = false
    }
  } catch (e) {
    ElMessage.error('预览失败')
    preview.visible = false
  } finally {
    preview.loading = false
  }
}

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
  animation: fadeIn 0.3s ease-in-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
