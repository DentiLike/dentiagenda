// Service Worker ÚNICO de DentiAgenda: arranque instantáneo (caché) + notificaciones push (FCM).
// ⚠️ La app debe registrar SOLO este archivo. Dos service workers en el mismo alcance
//    se reemplazan entre sí y las notificaciones dejan de mostrarse.
const CACHE_NAME = "dentiagenda-v41";
const ASSETS = ["./", "./index.html", "./manifest.json"];

/* ---------- Notificaciones push (Firebase Cloud Messaging) ---------- */
try {
  importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js");
  importScripts("https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js");
  firebase.initializeApp({
    apiKey: "AIzaSyC4Kws78y5K3-1IuBibxCOo-a11ccuNYUE",
    authDomain: "misonrisa-citas.firebaseapp.com",
    projectId: "misonrisa-citas",
    storageBucket: "misonrisa-citas.firebasestorage.app",
    messagingSenderId: "601464011103",
    appId: "1:601464011103:web:e03150d9b86eb40a451eea"
  });
  const messaging = firebase.messaging();
  messaging.onBackgroundMessage((payload) => {
    // Si el mensaje trae "notification", el SDK de Firebase YA la muestra solo.
    // Mostrarla aquí también la duplicaba. Solo atendemos mensajes de solo-datos.
    if (payload.notification) return;
    const d = payload.data || {};
    return self.registration.showNotification(d.title || "DentiAgenda", {
      body: d.body || "", icon: "favicon.png", badge: "favicon.png", data: d
    });
  });
} catch (e) { /* sin red al instalar: la app sigue; el push se activa en la siguiente versión */ }

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((lista) => {
      for (const c of lista) { if ("focus" in c) return c.focus(); }
      if (clients.openWindow) return clients.openWindow("./");
    })
  );
});

/* ---------- Caché: arranque instantáneo ---------- */
self.addEventListener("install", (e) => {
  self.skipWaiting();
  e.waitUntil(caches.open(CACHE_NAME).then((c) => Promise.all(ASSETS.map((u) => c.add(u).catch(() => {})))));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))));
  self.clients.claim();
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  const u = e.request.url;
  if (u.includes("firestore") || u.includes("firebase") || u.includes("googleapis") || u.includes("gstatic")) return;

  if (e.request.mode === "navigate") {
    e.respondWith(caches.match("./index.html").then((cached) => {
      const red = fetch(e.request).then((res) => {
        if (res && res.status === 200) { const copia = res.clone(); caches.open(CACHE_NAME).then((c) => c.put("./index.html", copia)); }
        return res;
      }).catch(() => cached);
      return cached || red;
    }));
    return;
  }
  e.respondWith(caches.match(e.request).then((cached) => {
    const red = fetch(e.request).then((res) => {
      if (res && res.status === 200) { const copia = res.clone(); caches.open(CACHE_NAME).then((c) => c.put(e.request, copia)); }
      return res;
    }).catch(() => cached);
    return cached || red;
  }));
});

self.addEventListener("message", (e) => { if (e.data === "skipWaiting") self.skipWaiting(); });
