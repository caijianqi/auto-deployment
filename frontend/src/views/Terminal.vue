<template>
  <div class="h-full flex flex-col gap-4">
    <!-- Header Card -->
    <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4 flex flex-wrap items-center justify-between gap-3">
      <!-- Left: Title & System Info -->
      <div class="flex items-center gap-3">
        <div class="w-10 h-10 rounded-lg bg-indigo-50 flex items-center justify-center text-indigo-600">
          <Terminal :size="22" />
        </div>
        <div>
          <div class="flex items-center gap-2">
            <h1 class="text-lg font-bold text-gray-800">Web 终端控制台</h1>
            <span 
              class="px-2.5 py-0.5 text-xs font-semibold rounded-full flex items-center gap-1.5"
              :class="isWindows ? 'bg-blue-50 text-blue-700 border border-blue-200' : 'bg-emerald-50 text-emerald-700 border border-emerald-200'"
            >
              <span>{{ isWindows ? '⊞ Windows' : '🐧 Linux' }}</span>
              <span class="text-[10px] text-gray-400">({{ systemInfo.os || '系统识别中' }})</span>
            </span>
            <span class="px-2 py-0.5 text-[11px] font-mono bg-purple-50 text-purple-700 border border-purple-200 rounded-full">
              PTY Native
            </span>
          </div>
          <p class="text-xs text-gray-500 mt-0.5">原生伪终端（PTY）架构，支持 Vim、Top、高亮、动态窗口自适应及毫秒级流式交互</p>
        </div>
      </div>

      <!-- Right: Controls -->
      <div class="flex flex-wrap items-center gap-2">
        <!-- Working Dir Selector -->
        <el-popover placement="bottom" :width="380" trigger="click">
          <template #reference>
            <el-button size="default" :icon="FolderOpen" class="!rounded-lg">
              <span class="max-w-[130px] truncate text-xs">{{ currentTab?.dir || '~ (用户目录)' }}</span>
            </el-button>
          </template>
          <div class="space-y-3">
            <div class="text-xs font-bold text-gray-700">新建终端工作目录</div>
            <el-input v-model="targetDir" size="small" placeholder="输入绝对路径 (如 C:\ 或 ~)" clearable />
            <div class="flex items-center justify-between pt-1">
              <span class="text-[11px] text-gray-400">默认使用用户主目录 (~)</span>
              <el-button type="primary" size="small" @click="applyNewDir">使用并新建</el-button>
            </div>
          </div>
        </el-popover>

        <!-- Snippet Drawer Trigger -->
        <el-tooltip content="常用运维指令库" placement="top">
          <el-button size="default" :icon="BookOpen" @click="showSnippetDrawer = true" class="!rounded-lg">
            常用指令
          </el-button>
        </el-tooltip>

        <!-- Font Size Adjuster -->
        <div class="flex items-center bg-gray-100 rounded-lg p-0.5 border border-gray-200 text-xs text-gray-600">
          <button 
            @click="changeFontSize(-1)" 
            class="px-2 py-1 hover:bg-white hover:text-indigo-600 rounded transition-colors text-xs font-bold"
            title="缩小字体"
          >
            A-
          </button>
          <span class="px-1 font-mono text-[11px] min-w-[28px] text-center">{{ fontSize }}px</span>
          <button 
            @click="changeFontSize(1)" 
            class="px-2 py-1 hover:bg-white hover:text-indigo-600 rounded transition-colors text-xs font-bold"
            title="放大字体"
          >
            A+
          </button>
        </div>

        <!-- Export Log -->
        <el-tooltip content="导出当前会话日志" placement="top">
          <el-button size="default" :icon="Download" @click="exportTerminalLog" class="!rounded-lg" />
        </el-tooltip>

        <!-- Clear Screen -->
        <el-tooltip content="清空终端屏幕 (Ctrl+L)" placement="top">
          <el-button size="default" :icon="Eraser" @click="clearActiveTerminal" class="!rounded-lg">清屏</el-button>
        </el-tooltip>

        <!-- Reconnect Active -->
        <el-tooltip content="重新连接当前终端" placement="top">
          <el-button size="default" :icon="RotateCcw" @click="reconnectActiveTerminal" class="!rounded-lg">重连</el-button>
        </el-tooltip>

        <!-- New Tab -->
        <el-button type="primary" size="default" :icon="Plus" @click="createNewTab()" class="!rounded-lg">
          新建终端
        </el-button>

        <!-- Fullscreen -->
        <el-button size="default" :icon="isFullScreen ? Minimize2 : Maximize2" @click="toggleFullScreen" class="!rounded-lg" />
      </div>
    </div>

    <!-- Terminal Container Card -->
    <div 
      ref="fullscreenContainer"
      class="flex-1 min-h-0 bg-[#18181b] rounded-xl shadow-lg border border-gray-800 flex flex-col overflow-hidden transition-all duration-200"
      :class="isFullScreen ? 'fixed inset-0 z-50 rounded-none border-none' : ''"
      @contextmenu.prevent="handleContextMenu"
    >
      <!-- Tabs Bar & Status -->
      <div class="bg-[#121215] px-3 pt-2 flex items-center justify-between border-b border-gray-800 select-none">
        <!-- Tabs List -->
        <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar max-w-[65%]">
          <div
            v-for="tab in tabs"
            :key="tab.id"
            @click="activeTabId = tab.id"
            @dblclick="startRenameTab(tab)"
            class="group relative flex items-center gap-2 px-3 py-1.5 rounded-t-lg text-xs font-mono transition-all cursor-pointer border-t border-x"
            :class="activeTabId === tab.id 
              ? 'bg-[#18181b] text-gray-100 border-gray-700 shadow-sm font-semibold' 
              : 'bg-transparent text-gray-400 border-transparent hover:text-gray-200 hover:bg-gray-800/40'"
          >
            <!-- Status Dot -->
            <span 
              class="w-2 h-2 rounded-full inline-block shrink-0"
              :class="{
                'bg-emerald-500 shadow-sm shadow-emerald-500/50 animate-pulse': tab.status === 'connected',
                'bg-amber-500 animate-ping': tab.status === 'connecting',
                'bg-rose-500': tab.status === 'disconnected'
              }"
            />
            
            <!-- Tab Title / Edit Input -->
            <input 
              v-if="editingTabId === tab.id"
              v-model="editingTitle"
              @blur="saveRenameTab(tab)"
              @keydown.enter.prevent="saveRenameTab(tab)"
              @keydown.esc.prevent="cancelRenameTab"
              @click.stop
              ref="renameInputRef"
              class="bg-gray-800 text-white px-1 py-0.5 rounded text-xs outline-none border border-indigo-500 w-24"
            />
            <span v-else class="truncate max-w-[120px]" :title="tab.title + ' (双击可重命名)'">{{ tab.title }}</span>

            <!-- Close Tab Button -->
            <span 
              v-if="tabs.length > 1"
              @click.stop="closeTab(tab.id)"
              class="opacity-0 group-hover:opacity-100 hover:bg-gray-700 rounded p-0.5 text-gray-400 hover:text-white transition-all ml-1 shrink-0"
              title="关闭标签页"
            >
              <X :size="12" />
            </span>
          </div>

          <!-- Add Tab Quick Button -->
          <button 
            @click="createNewTab()"
            class="px-2 py-1 rounded text-gray-400 hover:text-white hover:bg-gray-800 text-xs transition-colors shrink-0"
            title="新建终端标签"
          >
            <Plus :size="14" />
          </button>
        </div>

        <!-- Right: Active Tab Settings & Status Indicator -->
        <div class="flex items-center gap-3 py-1.5 text-xs text-gray-400 font-mono">
          <!-- Shell Type Selector -->
          <div class="flex items-center gap-1 bg-[#1e1e24] px-2 py-0.5 rounded border border-gray-700">
            <span class="text-gray-500 text-[11px]">Shell:</span>
            <select 
              v-model="currentTabShell" 
              @change="handleShellChange"
              class="bg-transparent text-gray-200 text-xs focus:outline-none cursor-pointer"
            >
              <option v-for="s in availableShells" :key="s.value" :value="s.value" class="bg-[#1e1e24] text-gray-200">
                {{ s.name }}
              </option>
            </select>
          </div>

          <!-- Charset Selector -->
          <div class="flex items-center gap-1 bg-[#1e1e24] px-2 py-0.5 rounded border border-gray-700">
            <span class="text-gray-500 text-[11px]">编码:</span>
            <select 
              v-model="currentTabCharset" 
              @change="handleCharsetChange"
              class="bg-transparent text-gray-200 text-xs focus:outline-none cursor-pointer"
            >
              <option value="UTF-8" class="bg-[#1e1e24] text-gray-200">UTF-8</option>
              <option value="GBK" class="bg-[#1e1e24] text-gray-200">GBK</option>
              <option value="GB2312" class="bg-[#1e1e24] text-gray-200">GB2312</option>
            </select>
          </div>

          <!-- Dimension Badge (Cols x Rows) -->
          <div class="hidden sm:block text-[11px] text-gray-500 bg-[#1e1e24] px-2 py-0.5 rounded border border-gray-700/60">
            {{ currentTab?.cols || 80 }}×{{ currentTab?.rows || 24 }}
          </div>

          <!-- Connection Status Pill -->
          <div 
            class="flex items-center gap-1.5 px-2 py-0.5 rounded text-[11px] font-medium"
            :class="{
              'bg-emerald-950/60 text-emerald-400 border border-emerald-800/40': currentTab?.status === 'connected',
              'bg-amber-950/60 text-amber-400 border border-amber-800/40': currentTab?.status === 'connecting',
              'bg-rose-950/60 text-rose-400 border border-rose-800/40': currentTab?.status === 'disconnected'
            }"
          >
            <span class="w-1.5 h-1.5 rounded-full" :class="currentTab?.status === 'connected' ? 'bg-emerald-400' : currentTab?.status === 'connecting' ? 'bg-amber-400' : 'bg-rose-400'" />
            <span>{{ currentTab?.status === 'connected' ? '在线' : currentTab?.status === 'connecting' ? '连接中' : '已断开' }}</span>
          </div>
        </div>
      </div>

      <!-- Terminal Body Content -->
      <div class="flex-1 relative overflow-hidden bg-[#18181b] p-2">
        <div 
          v-for="tab in tabs" 
          :key="tab.id"
          v-show="activeTabId === tab.id"
          :ref="el => setTabDomRef(el, tab.id)"
          class="w-full h-full terminal-wrapper"
        />
      </div>
    </div>

    <!-- Right-click Context Menu -->
    <div 
      v-if="contextMenu.visible"
      :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
      class="fixed z-50 bg-[#1e1e24] border border-gray-700 rounded-lg shadow-2xl py-1 text-xs text-gray-200 w-44 font-mono select-none"
      @click.stop
    >
      <div @click="copySelection()" class="flex items-center gap-2 px-3 py-2 hover:bg-indigo-600 hover:text-white cursor-pointer transition-colors">
        <Copy :size="14" />
        <span>复制选中内容</span>
      </div>
      <div @click="pasteFromClipboard()" class="flex items-center gap-2 px-3 py-2 hover:bg-indigo-600 hover:text-white cursor-pointer transition-colors">
        <Clipboard :size="14" />
        <span>粘贴到终端</span>
      </div>
      <div class="h-px bg-gray-700 my-1"></div>
      <div @click="clearActiveTerminal()" class="flex items-center gap-2 px-3 py-2 hover:bg-indigo-600 hover:text-white cursor-pointer transition-colors">
        <Eraser :size="14" />
        <span>清空屏幕 (Clear)</span>
      </div>
      <div @click="reconnectActiveTerminal()" class="flex items-center gap-2 px-3 py-2 hover:bg-indigo-600 hover:text-white cursor-pointer transition-colors">
        <RotateCcw :size="14" />
        <span>重新连接会话</span>
      </div>
      <div @click="exportTerminalLog()" class="flex items-center gap-2 px-3 py-2 hover:bg-indigo-600 hover:text-white cursor-pointer transition-colors">
        <Download :size="14" />
        <span>导出终端日志</span>
      </div>
    </div>

    <!-- Snippets Drawer -->
    <el-drawer 
      v-model="showSnippetDrawer" 
      title="常用运维指令库" 
      direction="rtl" 
      size="380px"
      :destroy-on-close="false"
    >
      <div class="space-y-4 text-xs">
        <p class="text-gray-500">点击指令右侧按钮可直接填充或发送至当前活跃终端：</p>

        <div v-for="group in snippetGroups" :key="group.category" class="space-y-2">
          <div class="font-bold text-gray-800 flex items-center gap-1.5 border-b pb-1">
            <span class="w-1.5 h-3 bg-indigo-500 rounded-full inline-block"></span>
            <span>{{ group.category }}</span>
          </div>
          <div class="space-y-1.5">
            <div 
              v-for="item in group.items" 
              :key="item.cmd"
              class="p-2 bg-gray-50 hover:bg-indigo-50/60 rounded border border-gray-100 transition-colors group flex items-center justify-between gap-2"
            >
              <div class="min-w-0 flex-1">
                <div class="font-semibold text-gray-700 text-[11px] truncate">{{ item.name }}</div>
                <div class="font-mono text-gray-500 text-[10px] truncate mt-0.5" :title="item.cmd">{{ item.cmd }}</div>
              </div>
              <div class="flex items-center gap-1 shrink-0">
                <el-tooltip content="仅填充到终端" placement="top">
                  <button 
                    @click="injectSnippet(item.cmd, false)"
                    class="p-1 text-gray-400 hover:text-indigo-600 hover:bg-white rounded border border-transparent hover:border-gray-200 transition-all"
                  >
                    <ArrowRight :size="13" />
                  </button>
                </el-tooltip>
                <el-tooltip content="填充并立即执行" placement="top">
                  <button 
                    @click="injectSnippet(item.cmd, true)"
                    class="p-1 text-gray-400 hover:text-emerald-600 hover:bg-white rounded border border-transparent hover:border-gray-200 transition-all"
                  >
                    <Play :size="13" />
                  </button>
                </el-tooltip>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Terminal as Xterm } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'
import api from '@/api'
import { ElMessage } from 'element-plus'
import { 
  Terminal, 
  Plus, 
  X, 
  Eraser, 
  RotateCcw, 
  Maximize2, 
  Minimize2, 
  FolderOpen,
  Copy,
  Clipboard,
  Download,
  BookOpen,
  ArrowRight,
  Play
} from 'lucide-vue-next'

const route = useRoute()

// System info
const systemInfo = reactive({
  os: '',
  isWindows: true,
  isLinux: false,
  userHome: '',
  currentDir: '',
  defaultCharset: 'UTF-8'
})

const isWindows = computed(() => systemInfo.isWindows)

const availableShells = ref([
  { name: 'PowerShell', value: 'powershell' },
  { name: 'CMD', value: 'cmd' }
])

// Target dir for new tab
const targetDir = ref('')

// Tabs State
let nextTabNumber = 1
const tabs = ref([])
const activeTabId = ref('')
const tabDomRefs = new Map()

// Font Size
const fontSize = ref(13)

// Snippet Drawer
const showSnippetDrawer = ref(false)

// Tab Rename
const editingTabId = ref('')
const editingTitle = ref('')
const renameInputRef = ref(null)

// Right Click Context Menu
const contextMenu = reactive({
  visible: false,
  x: 0,
  y: 0
})

const setTabDomRef = (el, id) => {
  if (el) {
    tabDomRefs.set(id, el)
  } else {
    tabDomRefs.delete(id)
  }
}

const currentTab = computed(() => tabs.value.find(t => t.id === activeTabId.value))

const currentTabShell = computed({
  get: () => currentTab.value?.shell || 'powershell',
  set: (val) => {
    if (currentTab.value) currentTab.value.shell = val
  }
})

const currentTabCharset = computed({
  get: () => currentTab.value?.charset || (isWindows.value ? 'GBK' : 'UTF-8'),
  set: (val) => {
    if (currentTab.value) currentTab.value.charset = val
  }
})

// Fullscreen
const isFullScreen = ref(false)
const fullscreenContainer = ref(null)

const toggleFullScreen = () => {
  isFullScreen.value = !isFullScreen.value
  nextTick(() => {
    fitActiveTerminal()
  })
}

// Snippet presets
const snippetGroups = computed(() => {
  if (isWindows.value) {
    return [
      {
        category: '系统排障 & 资源',
        items: [
          { name: '查看系统所有进程', cmd: 'Get-Process | Sort-Object CPU -Descending | Select-Object -First 15' },
          { name: '查看磁盘空间使用', cmd: 'Get-PSDrive -PSProvider FileSystem' },
          { name: '查看网络监听端口', cmd: 'netstat -ano | findstr LISTENING' },
          { name: '测试网络连通性', cmd: 'Test-NetConnection -ComputerName 127.0.0.1 -Port 8080' }
        ]
      },
      {
        category: '应用 & 容器运维',
        items: [
          { name: '列出运行中 Docker 容器', cmd: 'docker ps -a --format "table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Ports}}"' },
          { name: '检查所有运行中的 Java 进程', cmd: 'jps -l -v' },
          { name: '快速测试 HTTP 服务', cmd: 'Invoke-WebRequest -Uri http://localhost:8080 -UseBasicParsing' }
        ]
      }
    ]
  } else {
    return [
      {
        category: '系统负载 & 监控',
        items: [
          { name: '实时性能监控 (Top)', cmd: 'top' },
          { name: '查看内存使用状况', cmd: 'free -h' },
          { name: '查看各挂载磁盘空间', cmd: 'df -h' },
          { name: '查看正在监听的端口', cmd: 'ss -tulpn' },
          { name: '查看系统最近日志', cmd: 'journalctl -xe -n 50' }
        ]
      },
      {
        category: 'Nginx & Docker 运维',
        items: [
          { name: '测试 Nginx 配置文件语法', cmd: 'nginx -t' },
          { name: '平滑重载 Nginx 服务', cmd: 'nginx -s reload' },
          { name: '查看当前 Docker 容器', cmd: 'docker ps --format "table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Ports}}"' },
          { name: '查看 Docker 资源占用 (即时)', cmd: 'docker stats --no-stream' },
          { name: '检查所有 Java 进程', cmd: 'ps aux | grep java' }
        ]
      }
    ]
  }
})

// Fetch Terminal System Info
const fetchTerminalInfo = async () => {
  try {
    const res = await api.terminal.info()
    if (res.code === 200 && res.data) {
      Object.assign(systemInfo, res.data)
      if (res.data.shells && res.data.shells.length > 0) {
        availableShells.value = res.data.shells
      }
      if (!targetDir.value) {
        targetDir.value = res.data.userHome || '~'
      }
    }
  } catch (e) {
    console.error('Failed to get terminal info:', e)
  }
}

// Send resize event to backend PTY
const sendResize = (tab) => {
  if (!tab || !tab.xterm || !tab.socket || tab.socket.readyState !== WebSocket.OPEN) return
  const cols = tab.xterm.cols
  const rows = tab.xterm.rows
  if (!cols || !rows || cols <= 0 || rows <= 0) return
  tab.cols = cols
  tab.rows = rows
  try {
    // 发送标准 JSON 控制帧（前后端原生支持，绝不会漏入终端 stdin 被回显）
    tab.socket.send(JSON.stringify({
      type: 'resize',
      cols: cols,
      rows: rows
    }))
  } catch (ignored) {}
}

// Create New Terminal Tab
const createNewTab = async (customConfig = {}) => {
  const tabId = 'term-' + Date.now() + '-' + Math.random().toString(36).substring(2, 6)
  const defaultShell = availableShells.value[0]?.value || (isWindows.value ? 'powershell' : 'bash')
  const defaultCharset = isWindows.value ? (systemInfo.defaultCharset || 'GBK') : 'UTF-8'
  
  const shellName = customConfig.shell || defaultShell
  const tabTitle = `${shellName.toUpperCase()} #${nextTabNumber++}`
  const initialDir = customConfig.dir || targetDir.value || systemInfo.userHome || '~'
  const charset = customConfig.charset || defaultCharset

  const newTab = {
    id: tabId,
    title: tabTitle,
    shell: shellName,
    dir: initialDir,
    charset: charset,
    status: 'connecting',
    cols: 80,
    rows: 24,
    xterm: null,
    fitAddon: null,
    socket: null,
    resizeObserver: null,
    heartbeatTimer: null
  }

  tabs.value.push(newTab)
  activeTabId.value = tabId

  await nextTick()
  // 确保等宽字体已就绪，防止字符度量不准导致计算的 cols/rows 偏小或偏大
  if (document.fonts && document.fonts.ready) {
    try {
      await document.fonts.ready
    } catch (ignored) {}
  }
  initTerminalInstance(newTab)
}

// Initialize Xterm Instance with Native PTY streaming
const initTerminalInstance = (tab) => {
  const container = tabDomRefs.get(tab.id)
  if (!container) return

  // 1. Create Xterm Instance with rich dark theme
  const xterm = new Xterm({
    cursorBlink: true,
    cursorStyle: 'bar',
    fontSize: fontSize.value,
    fontFamily: 'Consolas, Monaco, "Courier New", "Fira Code", monospace',
    lineHeight: 1.2,
    theme: {
      background: '#18181b',
      foreground: '#f4f4f5',
      cursor: '#38bdf8',
      cursorAccent: '#18181b',
      selectionBackground: 'rgba(56, 189, 248, 0.3)',
      black: '#27272a',
      red: '#ef4444',
      green: '#10b981',
      yellow: '#f59e0b',
      blue: '#3b82f6',
      magenta: '#a855f7',
      cyan: '#06b6d4',
      white: '#e4e4e7',
      brightBlack: '#71717a',
      brightRed: '#f87171',
      brightGreen: '#34d399',
      brightYellow: '#fbbf24',
      brightBlue: '#60a5fa',
      brightMagenta: '#c084fc',
      brightCyan: '#22d3ee',
      brightWhite: '#ffffff'
    },
    convertEol: false, // 严禁二次转换 \n 为 \r\n，避免真实 PTY 阶梯状错位与 vim 画面破坏
    windowsMode: isWindows.value,
    scrollback: 5000,
    allowProposedApi: true
  })

  const fitAddon = new FitAddon()
  xterm.loadAddon(fitAddon)
  xterm.loadAddon(new WebLinksAddon())

  xterm.open(container)
  try {
    fitAddon.fit()
  } catch (ignored) {}

  tab.xterm = xterm
  tab.fitAddon = fitAddon
  tab.cols = xterm.cols || 80
  tab.rows = xterm.rows || 24

  // 2. Setup ResizeObserver
  const resizeObserver = new ResizeObserver(() => {
    if (activeTabId.value === tab.id) {
      try {
        fitAddon.fit()
        sendResize(tab)
      } catch (ignored) {}
    }
  })
  resizeObserver.observe(container)
  tab.resizeObserver = resizeObserver

  // 3. Setup Xterm Resize Listener
  xterm.onResize(() => {
    sendResize(tab)
  })

  // 4. Selection Copy on Select
  xterm.onSelectionChange(() => {
    const selection = xterm.getSelection()
    if (selection && selection.length > 0) {
      navigator.clipboard?.writeText(selection).catch(() => {})
    }
  })

  // 5. 键盘快捷键拦截优化（Ctrl+V 粘贴 与 Ctrl+C 复制/中断）
  xterm.attachCustomKeyEventHandler((event) => {
    if (event.type !== 'keydown') return true

    // Ctrl+V 或 Cmd+V (Mac) 安全粘贴
    if ((event.ctrlKey || event.metaKey) && (event.key === 'v' || event.key === 'V') && !event.altKey) {
      pasteFromClipboard(tab)
      return false // 阻止浏览器原生粘贴行为，避免未受控的双重注入
    }

    // Ctrl+C：当有文本被选中时，优先执行复制到剪贴板，避免直接触发 SIGINT 中断前台任务
    if ((event.ctrlKey || event.metaKey) && (event.key === 'c' || event.key === 'C') && !event.altKey) {
      if (xterm.hasSelection()) {
        copySelection()
        return false
      }
    }

    return true
  })

  // 6. Connect WebSocket with Safe Handshake
  connectWebSocket(tab)

  // 7. Native PTY Data Stream Direct Pass-Through
  // 所有按键、退格、方向键、Tab 键、Ctrl+C 全部原生送交 PTY
  xterm.onData((data) => {
    if (tab.socket && tab.socket.readyState === WebSocket.OPEN) {
      tab.socket.send(data)
    }
  })
}

// Connect WebSocket for Tab (with Sa-Token Authentication & initial cols/rows)
const connectWebSocket = (tab) => {
  if (tab.socket) {
    try {
      tab.socket.close()
    } catch (e) {}
  }

  tab.status = 'connecting'

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const prefix = (import.meta.env.VITE_APP_API_PREFIX || '/api').replace(/\/$/, '')
  const token = localStorage.getItem('satoken') || ''

  const query = new URLSearchParams({
    shell: tab.shell || '',
    dir: tab.dir || '',
    charset: tab.charset || '',
    cols: String(tab.xterm ? tab.xterm.cols : 80),
    rows: String(tab.xterm ? tab.xterm.rows : 24),
    satoken: token
  }).toString()

  const url = `${protocol}//${host}${prefix}/ws/terminal?${query}`
  const socket = new WebSocket(url)

  socket.onopen = () => {
    tab.status = 'connected'
    if (tab.xterm) {
      tab.xterm.focus()
    }
    // 延迟一帧确保 DOM 容器尺寸绝对稳定后同步尺寸
    requestAnimationFrame(() => {
      if (tab.fitAddon) {
        try {
          tab.fitAddon.fit()
        } catch (ignored) {}
      }
      sendResize(tab)
    })

    // 心跳维持（每 30 秒，使用标准 JSON）
    tab.heartbeatTimer = setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'ping' }))
      }
    }, 30000)
  }

  socket.onmessage = (event) => {
    if (tab.xterm) {
      if (event.data === '{"type":"pong"}' || event.data.includes('"type":"pong"')) return
      tab.xterm.write(event.data)
    }
  }

  socket.onerror = () => {
    tab.status = 'disconnected'
    if (tab.heartbeatTimer) {
      clearInterval(tab.heartbeatTimer)
      tab.heartbeatTimer = null
    }
    if (tab.xterm) {
      tab.xterm.write('\r\n\u001b[31m[错误] WebSocket 连接异常或鉴权失败\u001b[0m\r\n')
    }
  }

  socket.onclose = (event) => {
    tab.status = 'disconnected'
    if (tab.heartbeatTimer) {
      clearInterval(tab.heartbeatTimer)
      tab.heartbeatTimer = null
    }
    if (tab.xterm) {
      if (event.code === 4401 || event.code === 1008) {
        tab.xterm.write('\r\n\u001b[31m[权限拒绝] 未检测到有效登录状态，连接已中断\u001b[0m\r\n')
      } else {
        tab.xterm.write(`\r\n\u001b[33m[连接断开] 代码: ${event.code} 原因: ${event.reason || '会话已终止'}\u001b[0m\r\n`)
      }
    }
  }

  tab.socket = socket
}

// Adjust font size
const changeFontSize = (delta) => {
  const newSize = Math.max(12, Math.min(22, fontSize.value + delta))
  if (newSize === fontSize.value) return
  fontSize.value = newSize
  tabs.value.forEach(tab => {
    if (tab.xterm) {
      tab.xterm.options.fontSize = newSize
      if (tab.fitAddon) {
        try {
          tab.fitAddon.fit()
          sendResize(tab)
        } catch (ignored) {}
      }
    }
  })
}

// Context Menu Handling
const handleContextMenu = (e) => {
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.visible = true
}

const closeContextMenu = () => {
  contextMenu.visible = false
}

const copySelection = () => {
  const tab = currentTab.value
  if (tab && tab.xterm) {
    const text = tab.xterm.getSelection()
    if (text) {
      navigator.clipboard.writeText(text).then(() => {
        ElMessage.success('已复制到剪贴板')
      }).catch(() => {})
    } else {
      ElMessage.info('未选中文本')
    }
  }
  closeContextMenu()
}

// 模拟原生击键流分发（绕过 Bracketed Paste 压制，实现逐行自动回车执行）
const smoothPaste = async (tab, rawText) => {
  if (!tab || !tab.socket || tab.socket.readyState !== WebSocket.OPEN || !rawText) return

  // 1. 规范化不同操作系统的换行符为统一的 \n
  const normalized = rawText.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

  // 2. 单行文本直接通过原生按键通道注入
  if (!normalized.includes('\n')) {
    tab.socket.send(normalized)
    tab.xterm?.focus()
    return
  }

  // 3. 多行命令切片分发：绕过 xterm.paste 的转义括号，直接发送原生按键流并自动触发回车 \r 执行
  const lines = normalized.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.trim().length === 0 && i === lines.length - 1) {
      // 若最后一行是空行则忽略
      continue
    }

    // 每一行均追加标准回车符 \r，通知 Shell 立即执行当前命令
    tab.socket.send(line + '\r')

    // 给后台 Bash 留出执行、输出及刷新行提示符的时间窗口（50ms）
    if (i < lines.length - 1) {
      await new Promise(resolve => setTimeout(resolve, 50))
    }
  }

  tab.xterm?.focus()
}

const pasteFromClipboard = async (targetTab = null) => {
  // 兼容被 @click 默认传递事件对象的情况：若 targetTab 不是合法 Tab 对象，则回退到当前活跃 Tab
  const tab = (targetTab && targetTab.xterm) ? targetTab : currentTab.value
  closeContextMenu()

  if (!tab || !tab.xterm) {
    return
  }

  // 终端立即重新聚焦
  tab.xterm.focus()

  try {
    if (!navigator.clipboard || !navigator.clipboard.readText) {
      ElMessage.warning('当前浏览器环境（非 HTTPS/安全上下文）限制直接读取剪贴板，请直接在终端内按快捷键 Ctrl+V 粘贴')
      return
    }

    const text = await navigator.clipboard.readText()
    if (text) {
      // 采用平滑流控分发粘贴内容
      await smoothPaste(tab, text)
    } else {
      ElMessage.info('剪贴板中暂无文本内容')
    }
  } catch (e) {
    console.warn('Clipboard read error:', e)
    ElMessage.warning('未能获取剪贴板内容，请直接在终端中使用 Ctrl+V 快捷键')
    tab.xterm.focus()
  }
}

// Export terminal log as file
const exportTerminalLog = () => {
  const tab = currentTab.value
  if (!tab || !tab.xterm) return
  
  let content = ''
  const buffer = tab.xterm.buffer.active
  for (let i = 0; i < buffer.length; i++) {
    const line = buffer.getLine(i)
    if (line) {
      content += line.translateToString(true) + '\n'
    }
  }

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `terminal_${tab.title.replace(/[\s#]/g, '_')}_${new Date().getTime()}.log`
  a.click()
  URL.revokeObjectURL(a)
  ElMessage.success('终端日志已导出')
  closeContextMenu()
}

// Inject command snippet
const injectSnippet = (cmd, autoExecute = false) => {
  const tab = currentTab.value
  if (!tab || !tab.socket || tab.socket.readyState !== WebSocket.OPEN) {
    ElMessage.warning('当前终端尚未就绪')
    return
  }
  const textToSend = autoExecute ? (cmd + '\n') : cmd
  tab.socket.send(textToSend)
  if (tab.xterm) {
    tab.xterm.focus()
  }
  ElMessage.success(autoExecute ? '命令已执行' : '命令已填充至终端')
}

// Tab rename
const startRenameTab = (tab) => {
  editingTabId.value = tab.id
  editingTitle.value = tab.title
  nextTick(() => {
    renameInputRef.value?.focus()
    renameInputRef.value?.select()
  })
}

const saveRenameTab = (tab) => {
  if (editingTitle.value && editingTitle.value.trim().length > 0) {
    tab.title = editingTitle.value.trim()
  }
  editingTabId.value = ''
}

const cancelRenameTab = () => {
  editingTabId.value = ''
}

// Fit Active Terminal
const fitActiveTerminal = () => {
  const tab = currentTab.value
  if (tab && tab.fitAddon) {
    try {
      tab.fitAddon.fit()
      sendResize(tab)
    } catch (ignored) {}
  }
}

// Clear Terminal Screen
const clearActiveTerminal = () => {
  const tab = currentTab.value
  if (tab && tab.xterm) {
    tab.xterm.clear()
    tab.xterm.focus()
  }
  closeContextMenu()
}

// Reconnect Active Terminal
const reconnectActiveTerminal = () => {
  const tab = currentTab.value
  if (tab) {
    if (tab.xterm) {
      tab.xterm.clear()
    }
    connectWebSocket(tab)
  }
  closeContextMenu()
}

// Change Shell on Active Tab
const handleShellChange = () => {
  if (currentTab.value) {
    currentTab.value.title = `${currentTab.value.shell.toUpperCase()} #${currentTab.value.id.slice(-3)}`
    reconnectActiveTerminal()
  }
}

// Change Charset on Active Tab
const handleCharsetChange = () => {
  if (currentTab.value) {
    reconnectActiveTerminal()
  }
}

// Apply New Directory
const applyNewDir = () => {
  if (!targetDir.value) {
    ElMessage.warning('请输入有效路径')
    return
  }
  createNewTab({ dir: targetDir.value })
}

// Close a Tab
const closeTab = (tabId) => {
  const idx = tabs.value.findIndex(t => t.id === tabId)
  if (idx === -1) return

  const tabToClose = tabs.value[idx]
  if (tabToClose.heartbeatTimer) {
    clearInterval(tabToClose.heartbeatTimer)
    tabToClose.heartbeatTimer = null
  }
  if (tabToClose.socket) {
    try { tabToClose.socket.close() } catch (e) {}
  }
  if (tabToClose.resizeObserver) {
    try { tabToClose.resizeObserver.disconnect() } catch (e) {}
  }
  if (tabToClose.xterm) {
    try { tabToClose.xterm.dispose() } catch (e) {}
  }

  tabs.value.splice(idx, 1)

  if (activeTabId.value === tabId && tabs.value.length > 0) {
    const newIdx = Math.max(0, idx - 1)
    activeTabId.value = tabs.value[newIdx].id
  }
}

// Watch Active Tab change to refit & focus
watch(activeTabId, () => {
  nextTick(() => {
    const tab = currentTab.value
    if (tab && tab.fitAddon && tab.xterm) {
      try {
        tab.fitAddon.fit()
        sendResize(tab)
        tab.xterm.focus()
      } catch (ignored) {}
    }
  })
})

const handleWindowResize = () => {
  fitActiveTerminal()
}

const handleGlobalClick = () => {
  if (contextMenu.visible) {
    closeContextMenu()
  }
}

onMounted(async () => {
  await fetchTerminalInfo()
  window.addEventListener('resize', handleWindowResize)
  window.addEventListener('click', handleGlobalClick)

  const queryDir = route.query.dir ? String(route.query.dir) : ''
  const queryShell = route.query.shell ? String(route.query.shell) : ''

  if (queryDir) {
    targetDir.value = queryDir
  } else if (!targetDir.value) {
    targetDir.value = systemInfo.userHome || '~'
  }

  await createNewTab({
    dir: queryDir || systemInfo.userHome || '~',
    shell: queryShell
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleWindowResize)
  window.removeEventListener('click', handleGlobalClick)
  tabs.value.forEach(tab => {
    if (tab.heartbeatTimer) {
      clearInterval(tab.heartbeatTimer)
      tab.heartbeatTimer = null
    }
    if (tab.socket) {
      try { tab.socket.close() } catch (e) {}
    }
    if (tab.resizeObserver) {
      try { tab.resizeObserver.disconnect() } catch (e) {}
    }
    if (tab.xterm) {
      try { tab.xterm.dispose() } catch (e) {}
    }
  })
})
</script>

<style scoped>
.terminal-wrapper {
  padding: 4px;
  box-sizing: border-box;
}

:deep(.xterm) {
  height: 100%;
}

:deep(.xterm-viewport) {
  scrollbar-width: thin;
  scrollbar-color: #3f3f46 transparent;
}

:deep(.xterm-viewport::-webkit-scrollbar) {
  width: 6px;
}

:deep(.xterm-viewport::-webkit-scrollbar-thumb) {
  background-color: #3f3f46;
  border-radius: 4px;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
