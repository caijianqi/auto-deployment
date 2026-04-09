import { createRouter, createWebHashHistory } from 'vue-router'
import api from '@/api'
import Login from '@/views/Login.vue'
import Layout from '@/views/Layout.vue'
import Dashboard from '@/views/Dashboard.vue'
import AppList from '@/views/AppList.vue'
import SystemConfig from '@/views/SystemConfig.vue'
import NginxManager from '@/views/NginxManager.vue'
import ProcessManage from '@/views/ProcessManage.vue'
import FileManager from '@/views/FileManager.vue'
import Init from '@/views/Init.vue'

const routes = [
  {
    path: '/init',
    name: 'Init',
    component: Init,
    meta: { title: '系统初始化' }
  },
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: Dashboard,
        meta: { title: '仪表盘' }
      },
      {
        path: 'apps',
        name: 'AppList',
        component: AppList,
        meta: { title: '应用管理' }
      },
      {
        path: 'processes',
        name: 'ProcessManage',
        component: ProcessManage,
        meta: { title: '进程管理' }
      },
      {
        path: 'resource',
        name: 'SystemConfig',
        component: SystemConfig,
        meta: { title: '路径配置' }
      },
      {
        path: 'nginx',
        name: 'NginxManager',
        component: NginxManager,
        meta: { title: 'Nginx 管理' }
      },
      {
        path: 'files',
        name: 'FileManager',
        component: FileManager,
        meta: { title: '文件管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 系统初始化状态
let isInitialized = null

export const setInitialized = (val) => {
  isInitialized = val
}

router.beforeEach(async (to, from, next) => {
  // 1. 仅在状态未知时进行一次同步校验
  if (isInitialized === null) {
    try {
      const res = await api.auth.status()
      isInitialized = res.code === 200 && res.data === true
    } catch (e) {
      isInitialized = false
    }
  }

  // 2. 强制引导逻辑
  if (!isInitialized && to.name !== 'Init') {
    return next({ name: 'Init' })
  }
  
  if (isInitialized && to.name === 'Init') {
    return next({ name: 'Login' })
  }

  // 3. 登录拦截
  const token = localStorage.getItem('satoken')
  if (to.name !== 'Login' && to.name !== 'Init' && !token) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
