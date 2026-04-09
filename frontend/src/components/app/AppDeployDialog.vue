<template>
  <el-dialog :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" title="上传部署包" width="400px">
    <el-upload
      ref="uploadRef"
      class="upload-demo"
      drag
      action="#"
      :auto-upload="false"
      :on-change="handleFileChange"
      :limit="1"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        拖拽文件到此处 或 <em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip text-center">
          当前应用类型: <el-tag size="small">{{ appType }}</el-tag>
          (仅限 .{{ appType === 'JAR' ? 'jar' : 'zip' }} 文件)
        </div>
      </template>
    </el-upload>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="$emit('submit')" :loading="uploading">开始上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'

defineProps({
  modelValue: Boolean,
  appType: String,
  uploading: Boolean
})

const emit = defineEmits(['update:modelValue', 'submit', 'file-change'])

const uploadRef = ref(null)

const handleFileChange = (file) => {
  emit('file-change', file)
}

const clearFiles = () => {
  uploadRef.value?.clearFiles()
}

defineExpose({ clearFiles })
</script>
