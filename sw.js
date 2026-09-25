// DentiAgenda — Service Worker (estrategia: RED PRIMERO, caché de respaldo)
// Evita quedarse en blanco con versiones viejas: siempre intenta traer lo más nuevo,
// y solo usa el caché si no hay internet.
const CACHE_NAME = "dentiagenda-v20";
const ASSETS = [
  "./", "./index.html", "./manifest.json",
  "./favicon.png", "./icon-maskable.png", "./logo-dentiagenda.png"
];

// Instalar: guarda los archivos base y activa de inmediato
self.addEventListener("install", (e) => {
  self.skipWaiting();
  e.waitUntil(
    caches.open(CACHE_NAME).then((c) =>
      Promise.all(ASSETS.map((u) => c.add(u).catch(() => {})))
    )
  );
});

// Activar: borra cachés viejos y toma control
self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch: RED PRIMERO. Si la red falla (sin internet), usa el caché.
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  // Firebase siempre en vivo, nunca cachear
  if (e.request.url.includes("firestore") ||
      e.request.url.includes("firebase") ||
      e.request.url.includes("googleapis") ||
      e.request.url.includes("gstatic")) {
    return;
  }
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        // guarda copia fresca en caché para respaldo offline
        if (res && res.status === 200) {
          const copia = res.clone();
          caches.open(CACHE_NAME).then((c) => c.put(e.request, copia));
        }
        return res;
      })
      .catch(() => caches.match(e.request)) // sin internet → usa caché
  );
});

// Permite que la app pida al SW que se actualice
self.addEventListener("message", (e) => {
  if (e.data === "skipWaiting") self.skipWaiting();
});
