import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["icon.svg"],
      manifest: {
        name: "Bus Ticketing - Passenger",
        short_name: "BusTicket",
        description: "Scan a bus, pay, and get your QR ticket.",
        theme_color: "#1d4ed8",
        background_color: "#ffffff",
        display: "standalone",
        start_url: "/",
        icons: [
          { src: "icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" }
        ]
      },
      workbox: {
        // Cache the app shell so it loads even on a weak connection.
        // Ticket purchase itself still needs a live connection (it's a real payment).
        globPatterns: ["**/*.{js,css,html,svg}"]
      }
    })
  ],
  server: {
    port: 5173
  }
});
