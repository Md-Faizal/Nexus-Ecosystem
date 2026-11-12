import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
  server: {
    host: true,
    port: 5174,
    open: '/admin.html',
    proxy: {
      '/api': { target: 'http://127.0.0.1:3002', changeOrigin: true, ws: true },
      '/uploads': { target: 'http://127.0.0.1:3002', changeOrigin: true }
    }
  },
  plugins: [react(), tailwindcss()],
  build: {
    outDir: 'dist-admin',
    emptyOutDir: true,
    rollupOptions: {
      input: {
        admin: resolve(__dirname, 'admin.html')
      }
    }
  }
})
