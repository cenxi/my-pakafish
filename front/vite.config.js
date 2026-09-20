import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8040'
  const cmplEngineTarget = env.VITE_CMPL_ENGINE_TARGET || 'http://localhost:8040'

  return {
    base: './',
    build: {
      target: 'esnext',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('element-plus') || id.includes('@element-plus/icons-vue')) {
                return 'vendor-element-plus'
              }
              if (id.includes('vue') || id.includes('pinia') || id.includes('axios') || id.includes('dayjs')) {
                return 'vendor-core'
              }
            }
          }
        }
      }
    },
    optimizeDeps: {
      esbuildOptions: {
        target: 'esnext'
      }
    },
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia'],
        dts: false
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: false
      })
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      }
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `
            @use "@/assets/styles/variables.scss" as *;
          `
        }
      }
    },
    server: {
      host: '0.0.0.0',
      port: 3000,
      proxy: {
       '/engine': {
                target: cmplEngineTarget,
                changeOrigin: true,
                rewrite: (path) => path.replace(/^\/engine/, '')
        },
        '/cmpl': {
          target: proxyTarget,
          changeOrigin: true
        }
      }
    }
  }
})
