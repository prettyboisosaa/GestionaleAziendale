import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      // Il backend gira sul PC, non in un container. Passando da qui il
      // browser vede tutto su localhost:5173, quindi non serve configurare
      // CORS lato Spring.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
