import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173, // dont change this
    strictPort: true,
    proxy: {
      '/api': {
        // target: 'http://127.0.0.1:8080',
        target: process.env.API_TARGET || 'http://localhost:8080', // backend port
        changeOrigin: true,
      },
    },
  },
})
