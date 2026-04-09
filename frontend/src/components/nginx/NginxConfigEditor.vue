<template>
  <el-card shadow="hover" class="config-card">
    <template #header>
      <div class="flex justify-between items-center">
        <span class="font-bold">nginx.conf 配置文件</span>
        <el-button type="primary" @click="$emit('save')" :loading="saving">保存配置</el-button>
      </div>
    </template>
    <div class="editor-container">
      <codemirror
        :model-value="modelValue"
        @update:model-value="$emit('update:modelValue', $event)"
        placeholder="正在加载配置文件..."
        :style="{ height: height || '600px' }"
        :autofocus="true"
        :indent-with-tab="true"
        :tab-size="4"
        :extensions="extensions"
      />
    </div>
  </el-card>
</template>

<script setup>
import Codemirror from 'vue-codemirror6'
import { StreamLanguage } from '@codemirror/language'
import { nginx } from '@codemirror/legacy-modes/mode/nginx'
import { oneDark } from '@codemirror/theme-one-dark'
import { basicSetup } from 'codemirror'

const extensions = [basicSetup, StreamLanguage.define(nginx), oneDark]

defineProps({
  modelValue: String,
  saving: Boolean,
  height: String
})

defineEmits(['update:modelValue', 'save'])
</script>

<style scoped>
.config-card :deep(.el-card__header) {
  background-color: #f8f9fa;
}
.editor-container :deep(.cm-editor) {
  border-radius: 4px;
  overflow: hidden;
}
</style>
