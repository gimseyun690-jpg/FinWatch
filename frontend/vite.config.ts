import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure(proxy) {
          // The browser talks to Vite on the same origin during development.
          // Forwarding that Origin header to Spring can trigger unnecessary
          // CORS processing, so let the dev proxy make a normal server request.
          proxy.on('proxyReq', (proxyRequest) => {
            proxyRequest.removeHeader('origin')
          })
        },
      },
    },
  },
})
