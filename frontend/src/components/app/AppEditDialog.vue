<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)"
    :title="editing ? '编辑应用' : '新增应用'" 
    width="500px"
  >
    <el-form :model="form" label-width="120px" ref="formRef">
      <el-form-item label="应用名称" required>
        <el-input v-model="form.name" placeholder="请输入应用标识" />
      </el-form-item>
      <el-form-item label="应用类型" required>
        <el-select v-model="form.type" class="w-full" @change="$emit('type-change', $event)">
          <el-option label="SpringBoot (JAR)" value="JAR" />
          <el-option label="Vue (Static)" value="VUE" />
        </el-select>
      </el-form-item>
      <el-form-item label="程序部署路径" required>
        <el-select v-model="form.path" class="w-full" placeholder="请选择程序存放路径">
          <el-option 
            v-for="p in availableAppPaths" 
            :key="p.path" 
            :label="p.path" 
            :value="p.path"
          >
            <span style="float: left">{{ p.path }}</span>
            <span style="float: right; color: #8492a6; font-size: 13px">{{ p.remark }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      
      <template v-if="form.type === 'JAR'">
        <el-form-item label="JDK 版本">
          <el-select v-model="form.jdkId" class="w-full" placeholder="默认系统 JDK" clearable>
            <el-option 
              v-for="jdk in jdks" 
              :key="jdk.id" 
              :label="jdk.name" 
              :value="jdk.id"
            />
          </el-select>
          <div class="text-[10px] text-gray-400 mt-1">若不选择，则调用操作系统环境变量中的 java</div>
        </el-form-item>

        <el-form-item label="使用 YML 路径">
          <el-switch v-model="form.enableConfigPath" />
        </el-form-item>
        
        <el-form-item label="YML 路径" v-if="form.enableConfigPath" required>
          <el-select v-model="form.configPath" class="w-full" placeholder="请选择配置文件存放路径">
            <el-option 
              v-for="p in availableConfigPaths" 
              :key="p.path" 
              :label="p.path" 
              :value="p.path"
            >
              <span style="float: left">{{ p.path }}</span>
              <span style="float: right; color: #8492a6; font-size: 13px">{{ p.remark }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="开启运行端口">
          <el-switch v-model="form.enablePort" />
        </el-form-item>

        <el-form-item label="运行端口" v-if="form.enablePort" required>
          <el-input-number v-model="form.port" :min="1" :max="65535" />
        </el-form-item>
      </template>

      <el-form-item v-if="!editing" label="初始部署包">
        <el-upload
          ref="uploadRef"
          action="#"
          :auto-upload="false"
          :limit="1"
          :accept="form.type === 'JAR' ? '.jar' : '.zip'"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-button type="primary" size="small">选择文件</el-button>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="$emit('save')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  editing: Boolean,
  form: Object,
  availableAppPaths: Array,
  availableConfigPaths: Array,
  jdks: Array
})

const emit = defineEmits(['update:modelValue', 'save', 'type-change', 'file-change'])

const uploadRef = ref(null)

const handleFileChange = (file) => {
  emit('file-change', file)
}

const handleFileRemove = () => {
  emit('file-change', null)
}

const clearFiles = () => {
    uploadRef.value?.clearFiles()
}

defineExpose({ clearFiles })
</script>
