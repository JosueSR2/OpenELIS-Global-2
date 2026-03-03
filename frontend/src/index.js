import React from "react";
import ReactDOM from "react-dom";
import "./index.css";
import App from "./App";
import reportWebVitals from "./reportWebVitals";
import * as ServiceWorker from "./serviceWorkerRegistration";

// Forzar actualización cuando hay cambios en el build
const checkForUpdates = () => {
  // Leer versión del build (timestamp del build)
  const buildVersion = sessionStorage.getItem("buildVersion");
  fetch("/index.html?t=" + new Date().getTime(), { cache: "no-store" })
    .then((response) => response.text())
    .then((html) => {
      const parser = new DOMParser();
      const doc = parser.parseFromString(html, "text/html");
      const currentVersion =
        doc.head.querySelector("meta[name='build-version']")?.content || "0";

      if (buildVersion && buildVersion !== currentVersion) {
        // Nueva versión disponible - recargar toda la página
        console.log("Nueva versión disponible. Recargando...");
        window.location.reload(true);
      } else if (!buildVersion) {
        // Primera carga - guardar versión
        sessionStorage.setItem("buildVersion", currentVersion);
      }
    })
    .catch((err) => console.error("Error checking for updates:", err));
};

// Verificar actualizaciones cada 5 minutos
setInterval(checkForUpdates, 5 * 60 * 1000);
// Verificar también al cargar
checkForUpdates();

if (process.env.NODE_ENV === "production") {
  ServiceWorker.registerServiceWorker();
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker.getRegistrations().then((registrations) => {
      registrations.forEach((registration) => registration.update());
    });
  }
} else if ("serviceWorker" in navigator) {
  // Avoid stale JS/CSS in development (bundle.js) caused by previous SW caches.
  navigator.serviceWorker.getRegistrations().then((registrations) => {
    registrations.forEach((registration) => registration.unregister());
  });
  if ("caches" in window) {
    caches.keys().then((keys) => {
      keys.forEach((key) => caches.delete(key));
    });
  }
}

ReactDOM.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
  document.getElementById("root"),
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
