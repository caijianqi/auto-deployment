<template>
  <el-container class="h-screen">
    <el-aside width="220px" class="bg-[#304156] transition-all">
      <div class="h-full flex flex-col">
        <div class="p-6 flex items-center gap-3">
          <el-icon :size="24" color="#409eff"><Rocket /></el-icon>
          <span class="text-white text-lg font-bold">部署平台</span>
        </div>
        
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
          class="border-none flex-1"
        >
          <el-menu-item index="/dashboard">
            <el-icon><LayoutDashboard /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/apps">
            <el-icon><Box /></el-icon>
            <span>应用管理</span>
          </el-menu-item>
          <el-menu-item index="/processes">
            <el-icon><Activity /></el-icon>
            <span>进程管理</span>
          </el-menu-item>
          <el-menu-item index="/resource">
            <el-icon><HardDrive /></el-icon>
            <span>路径配置</span>
          </el-menu-item>
          <el-menu-item index="/nginx">
            <el-icon><Server /></el-icon>
            <span>Nginx 管理</span>
          </el-menu-item>
          <el-menu-item index="/files">
            <el-icon><Files /></el-icon>
            <span>文件管理</span>
          </el-menu-item>
        </el-menu>
        
        <div class="p-4 border-t border-gray-700">
          <div class="flex items-center justify-between text-gray-400">
            <span class="text-sm">管理员</span>
            <div class="flex gap-2">
              <el-button link @click="showPwdDialog = true" class="!text-gray-400 hover:!text-white">修改密码</el-button>
              <el-button link @click="handleLogout" class="!text-gray-400 hover:!text-white">退出登录</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-aside>
    
    <el-container>
      <el-header class="bg-white border-b border-gray-200 flex items-center justify-between px-6">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>首页</el-breadcrumb-item>
          <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
        </el-breadcrumb>
        
        <div class="flex items-center gap-4">
          <el-icon class="cursor-pointer text-gray-500"><Search /></el-icon>
          <el-icon class="cursor-pointer text-gray-500"><Bell /></el-icon>
        </div>
      </el-header>
      
      <el-main class="bg-[#f0f2f5] p-6">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <!-- Password Change Dialog -->
    <el-dialog v-model="showPwdDialog" title="修改登陆密码" width="420px" append-to-body destroy-on-close>
      <el-form :model="pwdForm" label-width="100px" label-position="left">
        <el-form-item label="当前密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前旧密码" />
        </el-form-item>
        <el-form-item label="新设密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="设置新的登录密码" />
        </el-form-item>
        <el-form-item label="重复确认" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="showPwdDialog = false">取消</el-button>
          <el-button type="primary" @click="submitUpdatePwd" :loading="pwdLoading">确认修改</el-button>
        </div>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { 
  LayoutDashboard, 
  Box, 
  Activity,
  HardDrive,
  Rocket,
  Bell,
  Search,
  LogOut,
  Server,
  Files
} from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '概览')

const handleLogout = () => {
  localStorage.removeItem('satoken')
  ElMessage.success('已安全退出')
  router.push('/login')
}

// Password Logic
import api from '@/api'
const showPwdDialog = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const submitUpdatePwd = async () => {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    return ElMessage.warning('请填写及原密码和新密码')
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    return ElMessage.warning('两次输入的新密码不一致')
  }
  if (pwdForm.newPassword.length < 6) {
    return ElMessage.warning('新密码长度不能小于 6 位')
  }

  pwdLoading.value = true
  try {
    const res = await api.auth.updatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功，请牢记新密码')
      showPwdDialog.value = false
      // Reset form
      Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
    } else {
      ElMessage.error(res.msg || '修改失败')
    }
  } catch (e) {
    ElMessage.error('服务异常，请稍后重试')
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.custom-menu-item {
  @apply mx-0 my-1 rounded-xl transition-all h-12 flex items-center text-slate-500 hover:text-indigo-600 hover:bg-indigo-50/50 !important;
}
.custom-menu-item.is-active {
  @apply bg-indigo-50 text-indigo-700 font-semibold shadow-sm shadow-indigo-100/50 !important;
}
.custom-menu-item .el-icon {
  @apply text-lg mr-3 !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
