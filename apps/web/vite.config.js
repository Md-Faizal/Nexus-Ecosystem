import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Shared dev proxy for local Rust core-hub backend (:3002)
const apiProxy = {
  '/api': { target: 'http://127.0.0.1:3002', changeOrigin: true, ws: true },
  '/save-message': { target: 'http://127.0.0.1:3002', changeOrigin: true },
  '/uploads': { target: 'http://127.0.0.1:3002', changeOrigin: true }
}

// Serve standalone admin.html when visiting /admin in dev
const adminDevRewrite = () => ({
  name: 'admin-dev-rewrite',
  configureServer(server) {
    server.middlewares.use((req, _res, next) => {
      if (req.url === '/admin' || req.url === '/admin/') {
        req.url = '/admin.html'
      }
      next()
    })
  }
})

// https://vite.dev/config/
export default defineConfig({
  server: {
    host: true,
    proxy: apiProxy
  },
  plugins: [react(), tailwindcss(), adminDevRewrite()],
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/framer-motion/') || id.includes('node_modules/gsap/')) return 'vendor-motion';
        }
      }
    }
  }
})
