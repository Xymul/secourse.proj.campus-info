import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境通过代理访问后端，避免跨域配置；生产环境由 Nginx 反向代理 /api
//
// 为便于在服务器（如 Debian 13）上部署，以下参数均可用环境变量覆盖：
//   CAMPUS_DEV_HOST  开发服务器监听地址（默认 127.0.0.1；服务器上设为 0.0.0.0 即可对外访问）
//   CAMPUS_DEV_PORT  开发服务器端口（默认 5173）
//   CAMPUS_API_PROXY 开发代理的后端地址（默认 http://127.0.0.1:8080）
const devHost = process.env.CAMPUS_DEV_HOST || '127.0.0.1'
const devPort = Number(process.env.CAMPUS_DEV_PORT || 5173)
const apiProxy = process.env.CAMPUS_API_PROXY || 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: devPort,
    host: devHost,
    proxy: {
      '/api': {
        target: apiProxy,
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1600
  }
})
