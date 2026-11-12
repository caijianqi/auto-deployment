import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Layout from '@/views/Layout.vue'
import Dashboard from '@/views/Dashboard.vue'
import AppList from '@/views/AppList.vue'
import SystemConfig from '@/views/SystemConfig.vue'
import NginxManager from '@/views/NginxManager.vue'
import ProcessManage from '@/views/ProcessManage.vue'
import FileManager from '@/views/FileManager.vue'
import Terminal from '@/views/Terminal.vue'

const routes = [
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
      },
      {
        path: 'terminal',
        name: 'Terminal',
        component: Terminal,
        meta: { title: '终端控制台' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('satoken')
  if (to.name !== 'Login' && !token) {
    next({ name: 'Login' })
  } else if (to.name === 'Login' && token) {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
