// Service Worker ÚNICO de DentiAgenda: arranque instantáneo (caché) + notificaciones push (FCM).
// ⚠️ La app debe registrar SOLO este archivo. Dos service workers en el mismo alcance
//    se reemplazan entre sí y las notificaciones dejan de mostrarse.
const CACHE_NAME = "dentiagenda-v55";
const ASSETS = ["./", "./index.html", "./manifest.json", "./badge.png"];


/* ---------- Avisos propios (van ANTES de cargar Firebase a propósito) ----------
   Los eventos "push" y "notificationclick" los atiende primero este código:
   - se muestra SIEMPRE el logo de DentiAgenda (icono grande + silueta en la barra),
   - al tocar el aviso se abre/enfoca la app y se lleva a la pantalla correcta.
   Sin esto, el SDK de Firebase atendía el toque antes y, sin enlace en el mensaje, no hacía nada. */
function daAccionDeAviso(d) {
  const t = ((d.title || "") + " " + (d.body || "") + " " + (d.tipo || "")).toLowerCase();
  if (d.accion) return String(d.accion);
  if (t.indexOf("solicitud") !== -1) return "solicitudes";
  return "hoy";
}
self.addEventListener("push", (event) => {
  let p = null;
  try { p = event.data ? event.data.json() : null; } catch (e) { p = null; }
  if (!p || typeof p !== "object") return;               // algo raro: que lo maneje el SDK como antes
  const n = p.notification || {}, d = p.data || {};
  const titulo = n.title || d.title || "";
  const cuerpo = n.body || d.body || "";
  if (!titulo && !cuerpo) return;                         // sin contenido: que lo maneje el SDK
  event.stopImmediatePropagation();                       // evita el aviso duplicado del SDK
  const datos = Object.assign({}, d, { title: titulo || "DentiAgenda", body: cuerpo });
  datos.accion = daAccionDeAviso(datos);
  const opciones = {
    body: cuerpo,
    icon: "favicon.png",
    badge: "badge.png",
    tag: d.tag || n.tag || undefined,
    data: datos
  };
  event.waitUntil(
    Promise.all([
      self.registration.showNotification(titulo || "DentiAgenda", opciones),
      // si la app está abierta, que además muestre su aviso dentro de la pantalla
      clients.matchAll({ type: "window", includeUncontrolled: true }).then((lista) => {
        lista.forEach((c) => { try { c.postMessage({ tipo: "da-push", titulo: titulo || "DentiAgenda", cuerpo: cuerpo, datos: datos }); } catch (e) {} });
      })
    ])
  );
});
self.addEventListener("notificationclick", (event) => {
  event.stopImmediatePropagation();                       // que el SDK no lo intercepte
  event.notification.close();
  const d = event.notification.data || {};
  const accion = d.accion || daAccionDeAviso(d);
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then((lista) => {
      for (const c of lista) {
        if ("focus" in c) {
          try { c.postMessage({ tipo: "da-push-click", accion: accion, datos: d }); } catch (e) {}
          return c.focus();
        }
      }
      if (clients.openWindow) return clients.openWindow("./?push=" + encodeURIComponent(accion));
    })
  );
});

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
      body: d.body || "", icon: "favicon.png", badge: "badge.png", data: d
    });
  });
} catch (e) { /* sin red al instalar: la app sigue; el push se activa en la siguiente versión */ }

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
