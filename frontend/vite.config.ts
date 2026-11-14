/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The browser only calls our own origin at /api/...; in dev Vite forwards it to the
// backend (Vercel does the same in production via vercel.json), so the refresh cookie
// stays first-party.
const BACKEND = "https://jfj-s529.onrender.com";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": { target: BACKEND, changeOrigin: true, secure: true },
      "/actuator": { target: BACKEND, changeOrigin: true, secure: true },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: { map: ["leaflet", "react-leaflet", "react-leaflet-cluster"] },
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});
