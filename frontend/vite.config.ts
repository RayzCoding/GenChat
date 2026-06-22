import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/agent': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/file': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        bypass(req) {
          // /file-qa is a frontend route and must not use the /file API proxy
          const url = req.url ?? ''
          if (url === '/file-qa' || url.startsWith('/file-qa?')) {
            return '/index.html'
          }
        },
      },
    },
  },
})
