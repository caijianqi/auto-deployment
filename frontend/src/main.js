import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import './index.css'
import App from './App.vue'
import router from './router'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'

import axios from 'axios'

const app = createApp(App)

// Axios 全局逻辑
axios.interceptors.response.use(
  response => {
    if (response.data && response.data.code === 4016) {
      console.warn('[Axios] System not initialized, redirecting to /init')
      router.push('/init')
    }
    return response
  },
  error => {
    return Promise.reject(error)
  }
)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
