import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { resolve } from "path";

export default defineConfig({
  plugins: [react()],
  define: {
    global: "globalThis",
  },
  server: {
    host: "0.0.0.0",
    port: 8081,
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
    rollupOptions: {
      input: {
        index: resolve(__dirname, "index.html"),
        login: resolve(__dirname, "login/index.html"),
        platformLogin: resolve(__dirname, "platform-login/index.html"),
        platform: resolve(__dirname, "platform/index.html"),
        admin: resolve(__dirname, "admin/index.html"),
        customer: resolve(__dirname, "customer/index.html"),
        q: resolve(__dirname, "q/index.html"),
        kitchen: resolve(__dirname, "kitchen/index.html"),
        cashier: resolve(__dirname, "cashier/index.html"),
      },
    },
  },
});
