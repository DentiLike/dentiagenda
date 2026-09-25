// DentiAgenda — Service Worker (CACHÉ PRIMERO para apertura instantánea)
// Abre al instante con lo guardado (aunque sea datos móviles lentos),
// y busca actualización en segundo plano sin bloquear.
const CACHE_NAME = "dentiagenda-v21";
const ASSETS = [
  "./", "./index.html", "./manifest.json",
  "./favicon.png", "./icon-maskable.png", "./logo-dentiagenda.png"
];

self.addEventListener("install", (e) => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE_NAME).then((c) =>
      Promise.all(ASSETS.map((u) => c.add(u).catch(() => {})))
    )
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// CACHÉ PRIMERO: responde al instante desde caché y actualiza en segundo plano.
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  // Firebase y librerías externas: siempre en vivo, no interceptar
  if (e.request.url.includes("firestore") ||
      e.request.url.includes("firebase") ||
      e.request.url.includes("googleapis") ||
      e.request.url.includes("gstatic")) {
    return;
  }
  e.respondWith(
    caches.match(e.request).then((cached) => {
      // actualizar en segundo plano (sin bloquear la respuesta)
      const fetchP = fetch(e.request).then((res) => {
        if (res && res.status === 200) {
          const copia = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(e.request, copia));
        }
        return res;
      }).catch(() => cached);
      // si hay caché, responde YA con ella; si no, espera la red
      return cached || fetchP;
    })
  );
});

self.addEventListener("message", (e) => {
  if (e.data === "skipWaiting") self.skipWaiting();
});
