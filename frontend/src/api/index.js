import axios from 'axios'

const api = axios.create({
  // 生产环境 VITE_APP_API_PREFIX 为 /auto/api，末尾加 / 确保 Axios 拼接成功
  baseURL: (import.meta.env.VITE_APP_API_PREFIX || '/api').endsWith('/') 
    ? (import.meta.env.VITE_APP_API_PREFIX || '/api') 
    : (import.meta.env.VITE_APP_API_PREFIX || '/api') + '/',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('satoken')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
}, error => {
  return Promise.reject(error)
})

api.interceptors.response.use(response => {
  const res = response.data
  // 如果业务码是 401，也视为未登录
  if (res.code === 401) {
    localStorage.removeItem('satoken')
    if (!window.location.hash.includes('#/login')) {
      window.location.hash = '#/login'
    }
  }
  return res
}, error => {
  if (error.response && error.response.status === 401) {
    localStorage.removeItem('satoken')
    if (!window.location.hash.includes('#/login')) {
      window.location.hash = '#/login'
    }
  }
  return Promise.reject(error)
})

export default {
  baseURL: api.defaults.baseURL,
  auth: {
    status: () => api.get('auth/status'),
    init: (data) => api.post('auth/init', data),
    login: (data) => api.post('auth/login', data),
    logout: () => api.post('auth/logout'),
    updatePassword: (data) => api.put('auth/password', data)
  },
  apps: {
    list: () => api.get('apps'),
    save: (data) => api.post('apps', data),
    delete: (id) => api.delete(`apps/${id}`),
    start: (id) => api.post(`apps/${id}/start`),
    stop: (id) => api.post(`apps/${id}/stop`),
    deploy: (id, formData) => api.post(`apps/${id}/deploy`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
    rollback: (id, backupId) => api.post(`apps/${id}/rollback`, null, { params: { backupId } })
  },
  logs: {
    get: (appId) => api.get(`logs/app/${appId}`),
    listLatest: () => api.get('logs/all/latest'),
    listAll: (params) => api.get('logs/all/list', { params })
  },
  backups: {
    get: (appId) => api.get(`backups/${appId}`),
    delete: (id) => api.delete(`backups/${id}`)
  },
  config: {
    getResources: (params) => api.get('config/resources', { params }),
    saveResource: (data) => api.post('config/resources', data),
    deleteResource: (id) => api.delete(`config/resources/${id}`),
    getJdks: () => api.get('config/jdks'),
    saveJdk: (data) => api.post('config/jdks', data),
    deleteJdk: (id) => api.delete(`config/jdks/${id}`),
    getFileContent: (path) => api.get('config/resources/file-content', { params: { path } }),
    saveFileContent: (data) => api.post('config/resources/file-content', data),
    getStats: () => api.get('config/system-stats')
  },
  nginx: {
    status: () => api.get('nginx/status'),
    getHome: () => api.get('nginx/home'),
    updateHome: (data) => api.post('nginx/home', data),
    start: () => api.post('nginx/start'),
    stop: () => api.post('nginx/stop'),
    restart: () => api.post('nginx/restart'),
    getConfig: () => api.get('nginx/config'),
    saveConfig: (data) => api.post('nginx/config', data),
    listBackups: () => api.get('nginx/backups'),
    restoreBackup: (id) => api.post(`nginx/backups/${id}/restore`),
    deleteBackup: (id) => api.delete(`nginx/backups/${id}`),
    getBackupContent: (id) => api.get(`nginx/backups/${id}/content`),
    updateBackupRemark: (id, data) => api.put(`nginx/backups/${id}/remark`, data)
  },
  processes: {
    list: () => api.get('processes'),
    kill: (pid) => api.post(`processes/${pid}/kill`)
  },
  file: {
    roots: () => api.get('file/roots'),
    list: (path) => api.get('file/list', { params: { path } }),
    getContent: (path) => api.get('file/content', { params: { path } }),
    saveContent: (data) => api.post('file/save-content', data),
    createFolder: (data) => api.post('file/create-folder', data),
    createFile: (data) => api.post('file/create-file', data),
    upload: (formData, params) => api.post('file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params
    }),
    rename: (data) => api.post('file/rename', data),
    delete: (data) => api.post('file/delete', data),
    batchDelete: (data) => api.post('file/batch-delete', data),
    unzip: (data) => api.post('file/unzip', data),
    zip: (data) => api.post('file/zip', data)
  },
  terminal: {
    info: () => api.get('terminal/info'),
    exec: (data) => api.post('terminal/exec', data),
    complete: (data) => api.post('terminal/complete', data)
  }
}
