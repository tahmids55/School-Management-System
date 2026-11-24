import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Third-party code is split into three long-lived chunks so browsers cache them between releases.
function manualChunks(id: string): string | undefined {
  if (!id.includes('node_modules')) return undefined;
  if (/[\\/]node_modules[\\/](recharts|d3-[^\\/]+|victory-vendor)[\\/]/.test(id)) return 'charts';
  if (/[\\/]node_modules[\\/](antd|@ant-design|rc-[^\\/]+|@rc-component)[\\/]/.test(id)) return 'antd';
  if (/[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|@remix-run|scheduler)[\\/]/.test(id)) {
    return 'react';
  }
  return undefined;
}

export default defineConfig(({ mode }) => {
  // VITE_PROXY_TARGET comes from the environment (or a local, uncommitted .env file).
  const env = loadEnv(mode, '.', 'VITE_');
  const target = env.VITE_PROXY_TARGET || 'http://localhost:8080';
  const proxied = { target, changeOrigin: true };

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': proxied,
        '/swagger-ui': proxied,
        '/v3/api-docs': proxied,
      },
    },
    build: {
      // Spring Boot serves this folder, so one jar contains the API and the UI.
      outDir: '../src/main/resources/static',
      emptyOutDir: true,
      rollupOptions: {
        output: { manualChunks },
      },
    },
  };
});
