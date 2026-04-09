<template>
  <div class="init-container">
    <div class="glass-card animate-in">
      <div class="header">
        <div class="logo-wrapper">
          <el-icon class="logo-icon ripple"><Monitor /></el-icon>
        </div>
        <h1>系统初始化</h1>
        <p class="subtitle">欢迎使用 Antigravity 部署平台，请设置您的管理员账号</p>
      </div>

      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" class="init-form">
        <el-form-item label="管理员账号" prop="username">
          <el-input 
            v-model="form.username" 
            placeholder="请输入账号" 
            prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item label="管理员密码" prop="password">
          <el-input 
            v-model="form.password" 
            type="password" 
            placeholder="请输入密码" 
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input 
            v-model="form.confirmPassword" 
            type="password" 
            placeholder="请再次填写密码" 
            prefix-icon="CircleCheck"
            show-password
          />
        </el-form-item>
        
        <el-button 
          type="primary" 
          class="submit-btn" 
          :loading="loading" 
          @click="handleInit"
        >
          完成初始化
        </el-button>
      </el-form>
      
      <div class="footer">
        <p>© 2024 Antigravity Deployment Engine</p>
      </div>
    </div>
    
    <!-- Background Elements -->
    <div class="bg-blobs">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '@/api'
import { Monitor, User, Lock, CircleCheck } from '@element-plus/icons-vue'
import { setInitialized } from '../router'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: '',
  confirmPassword: ''
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少为 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleInit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await api.auth.init({
          username: form.username,
          password: form.password
        })
        if (res.data.code === 200) {
          ElMessage.success('系统初始化成功')
          setInitialized(true) // 更新路由守卫中的状态位
          router.push('/login')
        } else {
          ElMessage.error(res.data.msg || '初始化失败')
        }
      } catch (error) {
        ElMessage.error('网络请求失败')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.init-container {
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #0f172a;
  overflow: hidden;
  position: relative;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

.glass-card {
  width: 440px;
  padding: 48px;
  background: rgba(30, 41, 59, 0.7);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 24px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  z-index: 10;
  position: relative;
}

.header {
  text-align: center;
  margin-bottom: 40px;
}

.logo-wrapper {
  margin-bottom: 24px;
  display: inline-block;
}

.logo-icon {
  font-size: 48px;
  color: #38bdf8;
  padding: 16px;
  background: rgba(56, 189, 248, 0.1);
  border-radius: 16px;
}

h1 {
  color: #f8fafc;
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px 0;
  letter-spacing: -0.5px;
}

.subtitle {
  color: #94a3b8;
  font-size: 14px;
  margin: 0;
}

.init-form {
  margin-bottom: 24px;
}

:deep(.el-form-item__label) {
  color: #cbd5e1;
  font-weight: 500;
  padding-bottom: 8px;
}

:deep(.el-input__wrapper) {
  background: rgba(15, 23, 42, 0.5);
  box-shadow: none !important;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 8px 12px;
  transition: all 0.3s;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #38bdf8;
  background: rgba(15, 23, 42, 0.8);
}

:deep(.el-input__inner) {
  color: #f1f5f9;
}

.submit-btn {
  width: 100%;
  height: 48px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 16px;
  background: linear-gradient(135deg, #38bdf8 0%, #2563eb 100%);
  border: none;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  margin-top: 20px;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(37, 99, 235, 0.4);
  opacity: 0.9;
}

.footer {
  text-align: center;
  margin-top: 40px;
}

.footer p {
  color: #64748b;
  font-size: 12px;
  margin: 0;
}

/* Background Blobs */
.bg-blobs {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
}

.blob {
  position: absolute;
  filter: blur(80px);
  border-radius: 50%;
  opacity: 0.4;
  animation: float 20s infinite alternate;
}

.blob-1 {
  width: 400px;
  height: 400px;
  background: #3b82f6;
  top: -100px;
  right: -100px;
}

.blob-2 {
  width: 350px;
  height: 350px;
  background: #a855f7;
  bottom: -50px;
  left: -50px;
  animation-delay: -5s;
}

.blob-3 {
  width: 300px;
  height: 300px;
  background: #ec4899;
  top: 20%;
  left: 10%;
  animation-delay: -10s;
}

@keyframes float {
  from { transform: translate(0, 0) rotate(0deg); }
  to { transform: translate(50px, 50px) rotate(15deg); }
}

.animate-in {
  animation: slideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(30px); }
  to { opacity: 1; transform: translateY(0); }
}

.ripple {
  animation: ripple 3s infinite;
}

@keyframes ripple {
  0% { box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.4); }
  70% { box-shadow: 0 0 0 20px rgba(56, 189, 248, 0); }
  100% { box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); }
}
</style>
