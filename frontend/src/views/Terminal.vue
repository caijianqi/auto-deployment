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
          </div>
          <p class="text-xs text-gray-500 mt-0.5">跨平台交互式终端，支持 PowerShell / CMD / Bash，毫秒级低延迟执行与流式回显</p>
        </div>
      </div>

      <!-- Right: Controls -->
      <div class="flex flex-wrap items-center gap-2">
        <!-- Working Dir Selector -->
        <el-popover placement="bottom" :width="380" trigger="click">
          <template #reference>
            <el-button size="default" :icon="FolderOpen" class="!rounded-lg">
              <span class="max-w-[140px] truncate text-xs">{{ currentTab?.dir || '~ (用户目录)' }}</span>
            </el-button>
          </template>
          <div class="space-y-3">
            <div class="text-xs font-bold text-gray-700">切换工作目录 (新建终端生效)</div>
            <el-input v-model="targetDir" size="small" placeholder="输入绝对路径 (如 C:\ 或 ~)" clearable />
            <div class="flex items-center justify-between pt-1">
              <span class="text-[11px] text-gray-400">默认使用用户主目录 (~)</span>
              <el-button type="primary" size="small" @click="applyNewDir">使用并新建</el-button>
            </div>
          </div>
        </el-popover>

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
    >
      <!-- Tabs Bar & Status -->
      <div class="bg-[#121215] px-3 pt-2 flex items-center justify-between border-b border-gray-800 select-none">
        <!-- Tabs List -->
        <div class="flex items-center gap-1.5 overflow-x-auto no-scrollbar max-w-[70%]">
          <div
            v-for="tab in tabs"
            :key="tab.id"
            @click="activeTabId = tab.id"
            class="group relative flex items-center gap-2 px-3 py-1.5 rounded-t-lg text-xs font-mono transition-all cursor-pointer border-t border-x"
            :class="activeTabId === tab.id 
              ? 'bg-[#18181b] text-gray-100 border-gray-700 shadow-sm font-semibold' 
              : 'bg-transparent text-gray-400 border-transparent hover:text-gray-200 hover:bg-gray-800/40'"
          >
            <!-- Status Dot -->
            <span 
              class="w-2 h-2 rounded-full inline-block"
              :class="{
                'bg-emerald-500 shadow-sm shadow-emerald-500/50 animate-pulse': tab.status === 'connected',
                'bg-amber-500 animate-ping': tab.status === 'connecting',
                'bg-rose-500': tab.status === 'disconnected'
              }"
            />
            
            <!-- Tab Title -->
            <span class="truncate max-w-[120px]">{{ tab.title }}</span>

            <!-- Close Tab Button -->
            <span 
              v-if="tabs.length > 1"
              @click.stop="closeTab(tab.id)"
              class="opacity-0 group-hover:opacity-100 hover:bg-gray-700 rounded p-0.5 text-gray-400 hover:text-white transition-all ml-1"
            >
              <X :size="12" />
            </span>
          </div>

          <!-- Add Tab Quick Button -->
          <button 
            @click="createNewTab()"
            class="px-2 py-1 rounded text-gray-400 hover:text-white hover:bg-gray-800 text-xs transition-colors"
            title="新建终端标签"
          >
            <Plus :size="14" />
          </button>
        </div>

        <!-- Right: Active Tab Settings & Status Indicator -->
        <div class="flex items-center gap-3 py-1.5 text-xs text-gray-400 font-mono">
          <!-- Shell Type Selector for Active Tab -->
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
  FolderOpen
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
    xterm: null,
    fitAddon: null,
    socket: null,
    resizeObserver: null,
    // Line buffer state
    inputBuffer: '',
    cursorPos: 0,
    history: [],
    historyIndex: -1,
    // Tab In-Place cycling state
    completionState: {
      active: false,
      tokenStartIndex: 0,
      beforeText: '',
      afterText: '',
      candidates: [],
      currentIndex: 0
    }
  }

  tabs.value.push(newTab)
  activeTabId.value = tabId

  await nextTick()
  initTerminalInstance(newTab)
}

// Calculate terminal display width for a character (handles ASCII, CJK wide chars, emojis)
const getCharWidth = (char) => {
  const code = char.codePointAt(0)
  if (code < 32 || (code >= 0x7f && code < 0xa0)) {
    return 0
  }
  // CJK ranges & fullwidth forms
  if (
    (code >= 0x1100 && code <= 0x115f) ||
    (code >= 0x2e80 && code <= 0xa4cf && code !== 0x303f) ||
    (code >= 0xac00 && code <= 0xd7a3) ||
    (code >= 0xf900 && code <= 0xfaff) ||
    (code >= 0xfe10 && code <= 0xfe19) ||
    (code >= 0xfe30 && code <= 0xfe6f) ||
    (code >= 0xff00 && code <= 0xff60) ||
    (code >= 0xffe0 && code <= 0xffe6) ||
    (code >= 0x20000 && code <= 0x2fffd) ||
    (code >= 0x30000 && code <= 0x3fffd)
  ) {
    return 2
  }
  // Emoji ranges
  if (
    (code >= 0x1f300 && code <= 0x1f64f) ||
    (code >= 0x1f680 && code <= 0x1f6ff) ||
    (code >= 0x2600 && code <= 0x26ff) ||
    (code >= 0x2700 && code <= 0x27bf)
  ) {
    return 2
  }
  return 1
}

// Calculate terminal display width for a string
const getStringWidth = (str) => {
  if (!str) return 0
  let width = 0
  for (const char of str) {
    width += getCharWidth(char)
  }
  return width
}

// Replace current input on screen cleanly with a new buffer and cursor position
const redrawInput = (tab, newBuffer, newCursorPos) => {
  const xterm = tab.xterm
  if (!xterm) return

  // Move cursor from current position back to start of input
  const currentBefore = tab.inputBuffer.slice(0, tab.cursorPos)
  const currentBeforeWidth = getStringWidth(currentBefore)
  let cmd = ''
  if (currentBeforeWidth > 0) {
    cmd += '\b'.repeat(currentBeforeWidth)
  }
  // Clear from cursor to end of line
  cmd += '\x1b[K'
  // Write new buffer content
  cmd += newBuffer
  xterm.write(cmd)

  // Move cursor back if newCursorPos is before end of newBuffer
  const newAfter = newBuffer.slice(newCursorPos)
  const newAfterWidth = getStringWidth(newAfter)
  if (newAfterWidth > 0) {
    xterm.write('\b'.repeat(newAfterWidth))
  }

  tab.inputBuffer = newBuffer
  tab.cursorPos = newCursorPos
}

// Execute command from input buffer
const executeCommand = (tab, cmdToSend) => {
  const xterm = tab.xterm
  if (cmdToSend.trim().length > 0) {
    if (tab.history.length === 0 || tab.history[tab.history.length - 1] !== cmdToSend) {
      tab.history.push(cmdToSend)
      if (tab.history.length > 200) tab.history.shift()
    }
  }
  tab.historyIndex = -1
  tab.inputBuffer = ''
  tab.cursorPos = 0
  if (xterm) {
    xterm.write('\r\n')
  }
  if (tab.socket && tab.socket.readyState === WebSocket.OPEN) {
    tab.socket.send(cmdToSend + '\n')
  }
}

// Handle multi-line pasted text or command batch
const handleMultiLineInput = (tab, text) => {
  const xterm = tab.xterm
  if (!xterm) return

  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
  const lines = normalized.split('\n')

  const before = tab.inputBuffer.slice(0, tab.cursorPos)
  const after = tab.inputBuffer.slice(tab.cursorPos)

  // Line 0 merges with what was before the cursor
  const firstLine = before + lines[0]
  xterm.write(lines[0])
  executeCommand(tab, firstLine)

  // Intermediate lines execute directly
  for (let i = 1; i < lines.length - 1; i++) {
    const line = lines[i]
    xterm.write(line)
    executeCommand(tab, line)
  }

  // Last line
  const lastLine = lines[lines.length - 1]
  if (lastLine.length > 0 || after.length > 0) {
    const finalBuffer = lastLine + after
    const finalCursorPos = lastLine.length
    tab.inputBuffer = finalBuffer
    tab.cursorPos = finalCursorPos
    xterm.write(finalBuffer)
    const afterWidth = getStringWidth(after)
    if (afterWidth > 0) {
      xterm.write('\b'.repeat(afterWidth))
    }
  }
}

// Initialize Xterm and WebSocket for a Tab
const initTerminalInstance = (tab) => {
  const container = tabDomRefs.get(tab.id)
  if (!container) return

  // 1. Create Xterm Instance with rich dark theme
  const xterm = new Xterm({
    cursorBlink: true,
    cursorStyle: 'bar',
    fontSize: 13,
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
    convertEol: true,
    scrollback: 5000,
    allowProposedApi: true
  })

  const fitAddon = new FitAddon()
  xterm.loadAddon(fitAddon)
  xterm.loadAddon(new WebLinksAddon())

  xterm.open(container)
  fitAddon.fit()

  tab.xterm = xterm
  tab.fitAddon = fitAddon

  // 2. Setup ResizeObserver
  const resizeObserver = new ResizeObserver(() => {
    if (activeTabId.value === tab.id) {
      try {
        fitAddon.fit()
      } catch (ignored) {}
    }
  })
  resizeObserver.observe(container)
  tab.resizeObserver = resizeObserver

  // 3. Connect WebSocket
  connectWebSocket(tab)

  // 4. Handle Terminal User Input -> Full Line Editor with Backspace/Delete/History/Paste support
  xterm.onData((data) => {
    if (!tab.socket || tab.socket.readyState !== WebSocket.OPEN) {
      return
    }

    // 非 Tab 键输入时，退出 Tab 补全轮播状态
    if (data !== '\t' && data !== '\x09') {
      if (tab.completionState) {
        tab.completionState.active = false
      }
    }

    // A. 包含换行符（回车键或多行粘贴/带换行的单行粘贴）
    if (data.includes('\r') || data.includes('\n')) {
      const normalized = data.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
      const lines = normalized.split('\n')
      if (lines.length === 1 || (lines.length === 2 && lines[0] === '' && lines[1] === '')) {
        // 单纯按下回车键
        executeCommand(tab, tab.inputBuffer)
        return
      }
      // 多行文本粘贴
      handleMultiLineInput(tab, data)
      return
    }

    // B. Backspace Key (\x7f or \x08 or ASCII 127) -> Accurately delete character before cursor
    if (data === '\x7f' || data === '\x08') {
      if (tab.cursorPos > 0) {
        const before = tab.inputBuffer.slice(0, tab.cursorPos)
        const after = tab.inputBuffer.slice(tab.cursorPos)

        // 处理代理对（如 Emoji / 拓展 Unicode）
        let delLen = 1
        if (tab.cursorPos >= 2) {
          const code = tab.inputBuffer.charCodeAt(tab.cursorPos - 1)
          const prevCode = tab.inputBuffer.charCodeAt(tab.cursorPos - 2)
          if (code >= 0xDC00 && code <= 0xDFFF && prevCode >= 0xD800 && prevCode <= 0xDBFF) {
            delLen = 2
          }
        }

        const deletedChar = before.slice(-delLen)
        const newBefore = before.slice(0, -delLen)
        const charWidth = getStringWidth(deletedChar)
        const afterWidth = getStringWidth(after)

        tab.inputBuffer = newBefore + after
        tab.cursorPos -= delLen

        // 视觉擦除：光标左移对应字宽 -> 清除右侧到行尾 -> 重绘 after 内容 -> 光标移回正确插入点
        let redraw = '\b'.repeat(charWidth) + '\x1b[K' + after
        if (afterWidth > 0) {
          redraw += '\b'.repeat(afterWidth)
        }
        xterm.write(redraw)
      }
      return
    }

    // C. Delete Key (\x1b[3~) -> Delete character at cursor
    if (data === '\x1b[3~') {
      if (tab.cursorPos < tab.inputBuffer.length) {
        const before = tab.inputBuffer.slice(0, tab.cursorPos)
        const after = tab.inputBuffer.slice(tab.cursorPos)

        let delLen = 1
        if (after.length >= 2) {
          const code = after.charCodeAt(0)
          const nextCode = after.charCodeAt(1)
          if (code >= 0xD800 && code <= 0xDBFF && nextCode >= 0xDC00 && nextCode <= 0xDFFF) {
            delLen = 2
          }
        }

        const newAfter = after.slice(delLen)
        const newAfterWidth = getStringWidth(newAfter)

        tab.inputBuffer = before + newAfter

        let redraw = '\x1b[K' + newAfter
        if (newAfterWidth > 0) {
          redraw += '\b'.repeat(newAfterWidth)
        }
        xterm.write(redraw)
      }
      return
    }

    // Tab Key (\t or \x09) -> Intelligent Command & Path Autocompletion
    if (data === '\t' || data === '\x09') {
      handleTabCompletion(tab)
      return
    }

    // D. Left Arrow (\x1b[D)
    if (data === '\x1b[D') {
      if (tab.cursorPos > 0) {
        const before = tab.inputBuffer.slice(0, tab.cursorPos)
        let step = 1
        if (tab.cursorPos >= 2) {
          const code = tab.inputBuffer.charCodeAt(tab.cursorPos - 1)
          const prevCode = tab.inputBuffer.charCodeAt(tab.cursorPos - 2)
          if (code >= 0xDC00 && code <= 0xDFFF && prevCode >= 0xD800 && prevCode <= 0xDBFF) {
            step = 2
          }
        }
        const prevChar = before.slice(-step)
        const charWidth = getStringWidth(prevChar)
        tab.cursorPos -= step
        if (charWidth > 0) {
          xterm.write('\b'.repeat(charWidth))
        }
      }
      return
    }

    // E. Right Arrow (\x1b[C)
    if (data === '\x1b[C') {
      if (tab.cursorPos < tab.inputBuffer.length) {
        const after = tab.inputBuffer.slice(tab.cursorPos)
        let step = 1
        if (after.length >= 2) {
          const code = after.charCodeAt(0)
          const nextCode = after.charCodeAt(1)
          if (code >= 0xD800 && code <= 0xDBFF && nextCode >= 0xDC00 && nextCode <= 0xDFFF) {
            step = 2
          }
        }
        const nextChar = after.slice(0, step)
        const charWidth = getStringWidth(nextChar)
        tab.cursorPos += step
        if (charWidth > 0) {
          xterm.write('\x1b[' + charWidth + 'C')
        }
      }
      return
    }

    // F. Home Key (\x1b[H or \x1b[1~ or \x1bOH)
    if (data === '\x1b[H' || data === '\x1b[1~' || data === '\x1bOH') {
      if (tab.cursorPos > 0) {
        const before = tab.inputBuffer.slice(0, tab.cursorPos)
        const width = getStringWidth(before)
        if (width > 0) {
          xterm.write('\b'.repeat(width))
        }
        tab.cursorPos = 0
      }
      return
    }

    // G. End Key (\x1b[F or \x1b[4~ or \x1bOF)
    if (data === '\x1b[F' || data === '\x1b[4~' || data === '\x1bOF') {
      if (tab.cursorPos < tab.inputBuffer.length) {
        const after = tab.inputBuffer.slice(tab.cursorPos)
        const width = getStringWidth(after)
        if (width > 0) {
          xterm.write('\x1b[' + width + 'C')
        }
        tab.cursorPos = tab.inputBuffer.length
      }
      return
    }

    // H. Up Arrow (\x1b[A) -> Previous command in history
    if (data === '\x1b[A') {
      if (tab.history.length > 0) {
        if (tab.historyIndex === -1) {
          tab.historyIndex = tab.history.length - 1
        } else if (tab.historyIndex > 0) {
          tab.historyIndex--
        }
        const prevCmd = tab.history[tab.historyIndex]
        redrawInput(tab, prevCmd, prevCmd.length)
      }
      return
    }

    // I. Down Arrow (\x1b[B) -> Next command in history
    if (data === '\x1b[B') {
      if (tab.historyIndex !== -1) {
        if (tab.historyIndex < tab.history.length - 1) {
          tab.historyIndex++
          const nextCmd = tab.history[tab.historyIndex]
          redrawInput(tab, nextCmd, nextCmd.length)
        } else {
          tab.historyIndex = -1
          redrawInput(tab, '', 0)
        }
      }
      return
    }

    // J. Ctrl+C (\x03)
    if (data === '\x03') {
      xterm.write('^C\r\n')
      tab.socket.send('\x03\r\n')
      tab.inputBuffer = ''
      tab.cursorPos = 0
      tab.historyIndex = -1
      return
    }

    // K. Ctrl+L (\x0c) -> Clear Screen
    if (data === '\x0c') {
      xterm.clear()
      if (tab.inputBuffer) {
        xterm.write(tab.inputBuffer)
        const after = tab.inputBuffer.slice(tab.cursorPos)
        const afterWidth = getStringWidth(after)
        if (afterWidth > 0) {
          xterm.write('\b'.repeat(afterWidth))
        }
      }
      return
    }

    // L. Ignore other escape sequences
    if (data.startsWith('\x1b')) {
      return
    }

    // M. Printable Characters / Chinese / Single-line Paste Text
    const cleanData = data.replace(/[\x00-\x08\x0b-\x1a\x1c-\x1f\x7f]/g, '')
    if (cleanData.length === 0) return

    const before = tab.inputBuffer.slice(0, tab.cursorPos)
    const after = tab.inputBuffer.slice(tab.cursorPos)
    tab.inputBuffer = before + cleanData + after
    tab.cursorPos += cleanData.length

    if (after.length === 0) {
      xterm.write(cleanData)
    } else {
      const afterWidth = getStringWidth(after)
      xterm.write('\x1b[K' + cleanData + after)
      if (afterWidth > 0) {
        xterm.write('\b'.repeat(afterWidth))
      }
    }
  })
}

// Connect WebSocket for Tab
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
  const query = new URLSearchParams({
    shell: tab.shell || '',
    dir: tab.dir || '',
    charset: tab.charset || ''
  }).toString()

  const url = `${protocol}//${host}${prefix}/ws/terminal?${query}`
  const socket = new WebSocket(url)

  socket.onopen = () => {
    tab.status = 'connected'
    if (tab.xterm) {
      tab.xterm.focus()
    }
    // 每 30 秒发送心跳，防止 Nginx 代理 60 秒超时断开
    tab.heartbeatTimer = setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send('{"type":"ping"}')
      }
    }, 30000)
  }

  socket.onmessage = (event) => {
    if (tab.xterm) {
      // 过滤心跳 pong 响应，不显示到终端
      if (event.data === '{"type":"pong"}') return
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
      tab.xterm.write('\r\n\u001b[31m[错误] WebSocket 连接发生错误\u001b[0m\r\n')
    }
  }

  socket.onclose = (event) => {
    tab.status = 'disconnected'
    if (tab.heartbeatTimer) {
      clearInterval(tab.heartbeatTimer)
      tab.heartbeatTimer = null
    }
    if (tab.xterm) {
      tab.xterm.write(`\r\n\u001b[33m[连接断开] 代码: ${event.code} 原因: ${event.reason || '会话已终止'}\u001b[0m\r\n`)
    }
  }

  tab.socket = socket
}

// Handle Tab Autocompletion (In-Place native shell style cycling)
const handleTabCompletion = async (tab) => {
  const xterm = tab.xterm
  if (!xterm) return

  // 1. 如果当前已经处于 Tab 补全轮播状态，连续按 Tab 直接轮流替换下一个候选项
  if (tab.completionState && tab.completionState.active && tab.completionState.candidates.length > 0) {
    const candidates = tab.completionState.candidates
    tab.completionState.currentIndex = (tab.completionState.currentIndex + 1) % candidates.length
    const match = candidates[tab.completionState.currentIndex]

    const newBuffer = tab.completionState.beforeText + match + tab.completionState.afterText
    const newCursorPos = tab.completionState.beforeText.length + match.length
    redrawInput(tab, newBuffer, newCursorPos)
    return
  }

  // 2. 首次按下 Tab 键：请求后端匹配候选列表
  const currentLine = tab.inputBuffer
  const cursorPos = tab.cursorPos

  try {
    const res = await api.terminal.complete({
      dir: tab.dir,
      line: currentLine,
      cursorPos: cursorPos
    })

    if (res.code === 200 && res.data) {
      const { lastToken, tokenStartIndex, candidates } = res.data
      if (!candidates || candidates.length === 0) {
        return
      }

      const startIdx = typeof tokenStartIndex === 'number' ? tokenStartIndex : (cursorPos - lastToken.length)
      const before = currentLine.slice(0, startIdx)
      const after = currentLine.slice(cursorPos)

      // 记录当前补全状态以便后续连续按 Tab 时原地轮播
      tab.completionState = {
        active: true,
        tokenStartIndex: startIdx,
        beforeText: before,
        afterText: after,
        candidates: candidates,
        currentIndex: 0
      }

      const match = candidates[0]
      const newBuffer = before + match + after
      const newCursorPos = before.length + match.length
      redrawInput(tab, newBuffer, newCursorPos)
    }
  } catch (e) {
    console.error('Tab completion error:', e)
  }
}

// Fit Active Terminal
const fitActiveTerminal = () => {
  const tab = currentTab.value
  if (tab && tab.fitAddon) {
    try {
      tab.fitAddon.fit()
    } catch (ignored) {}
  }
}

// Clear Terminal Screen
const clearActiveTerminal = () => {
  const tab = currentTab.value
  if (tab && tab.xterm) {
    tab.xterm.clear()
    tab.inputBuffer = ''
    tab.cursorPos = 0
    tab.xterm.focus()
  }
}

// Reconnect Active Terminal
const reconnectActiveTerminal = () => {
  const tab = currentTab.value
  if (tab) {
    if (tab.xterm) {
      tab.xterm.clear()
    }
    tab.inputBuffer = ''
    tab.cursorPos = 0
    tab.historyIndex = -1
    connectWebSocket(tab)
  }
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
  // Cleanup resources
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

  // Switch active tab if closed active
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
        tab.xterm.focus()
      } catch (ignored) {}
    }
  })
})

// Window Resize Event
const handleWindowResize = () => {
  fitActiveTerminal()
}

onMounted(async () => {
  await fetchTerminalInfo()
  window.addEventListener('resize', handleWindowResize)

  // Check URL query parameters (e.g. ?dir=...&shell=...)
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
  // Cleanup all tabs
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
