// DentiAgenda — service worker v2 (primero internet, respaldo cache sin conexion)
const CACHE_NAME = "dentiagenda-v20";
const ASSETS = ["./","./index.html","./manifest.json","./favicon.png","./icon-maskable.png","./logo-dentiagenda.png","./firebase-messaging-sw.js"];
self.addEventListener("install",(e)=>{ self.skipWaiting(); e.waitUntil(caches.open(CACHE_NAME).then(c=>Promise.all(ASSETS.map(u=>c.add(u).catch(()=>{}))))); });
self.addEventListener("activate",(e)=>{ e.waitUntil(caches.keys().then(k=>Promise.all(k.filter(x=>x!==CACHE_NAME).map(x=>caches.delete(x))))); self.clients.claim(); });
self.addEventListener("fetch",(e)=>{
  if(e.request.method!=="GET") return;
  // No cachear Firebase (siempre en vivo)
  if(e.request.url.includes('firestore') || e.request.url.includes('firebase') || e.request.url.includes('googleapis')) return;
  // Primero intenta traer la version mas reciente de internet. Solo si no hay
  // conexion (o falla la red), usa la copia guardada en cache como respaldo.
  e.respondWith(
    fetch(e.request).then(res=>{
      if(res && res.status===200){
        const cl = res.clone();
        caches.open(CACHE_NAME).then(c=>c.put(e.request,cl));
      }
      return res;
    }).catch(()=>caches.match(e.request))
  );
});
