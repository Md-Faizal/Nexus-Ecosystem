import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'
import { fileURLToPath } from 'url'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig({
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
