<template>
  <div class="min-h-screen w-full flex items-center justify-center bg-gray-50 px-4">
    <el-card class="w-full max-w-sm" shadow="always">
      <div class="text-center mb-8">
        <el-icon :size="48" color="#409eff"><Rocket /></el-icon>
        <h1 class="text-2xl font-bold text-gray-800 mt-4">自动部署平台</h1>
        <p class="text-gray-400 text-sm mt-2">高效应用生命周期管理</p>
      </div>
      
      <el-form :model="loginForm" label-position="top">
        <el-form-item label="账号">
          <el-input 
            v-model="loginForm.username" 
            placeholder="请输入账号" 
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input 
            v-model="loginForm.password" 
            type="password" 
            placeholder="请输入密码" 
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        
        <el-button 
          v-if="!loading"
          type="primary" 
          class="w-full mt-4" 
          @click="handleLogin"
        >
          立即登录
        </el-button>
        <el-button 
          v-else
          type="primary" 
          class="w-full mt-4" 
          loading
        >
          登录中...
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Rocket, User, Lock } from 'lucide-vue-next'
import api from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const loginForm = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
    if (!loginForm.username || !loginForm.password) {
        ElMessage.warning('请输入账号和密码')
        return
    }
    loading.value = true
    try {
        const res = await api.auth.login(loginForm)
        localStorage.setItem('satoken', res.data.tokenValue)
        ElMessage.success('登录成功')
        router.push('/')
    } catch (e) {
        ElMessage.error('登录失败: ' + (e.response?.data?.msg || '服务异常'))
    } finally {
        loading.value = false
    }
}
</script>

<style scoped>
:deep(.custom-input .el-input__wrapper) {
  @apply rounded-xl bg-slate-50/50 shadow-none border border-slate-200 transition-all focus-within:border-indigo-500 focus-within:bg-white !important;
}
</style>
