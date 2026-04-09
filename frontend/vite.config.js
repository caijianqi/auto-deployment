import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: './',
    plugins: [vue()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/api/ws': {
          target: 'http://localhost:8080',
          ws: true,
          changeOrigin: true,
        },
        '/auto/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/auto/api/ws': {
          target: 'http://localhost:8080',
          ws: true,
          changeOrigin: true,
        },
      },
    },
    build: {
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('element-plus')) {
                return 'vendor-element-plus';
              }
              return 'vendor'; // all other node_modules
            }
          },
        },
      },
    },
  }
})
