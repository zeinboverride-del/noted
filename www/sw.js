const CACHE_NAME = 'noted-quest-cache-v1';
const urlsToCache = [
  './',
  './index.html',
  './indexan.css',
  './indexan.js',
  './manifest.json',
  './icon-192.png',
  './HASIL_HASIL/fontawesome/css/all.min.css' // Pastikan folder fontawesome ke-cache
];

// Pasang Service Worker dan simpan file ke cache (memory HP)
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        return cache.addAll(urlsToCache);
      })
  );
});

// Ambil data dari cache kalau lagi offline
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request)
      .then(response => {
        // Kalau file ada di cache, pakai yang di cache. Kalau gak ada, baru download dari internet.
        return response || fetch(event.request);
      })
  );
});

self.addEventListener('notificationclick', event => {
    event.notification.close(); // Tutup notif pas di-klik
  
    // Buka atau fokus ke aplikasi Noted pas notif di-klik
    event.waitUntil(
      clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clientList => {
        for (let i = 0; i < clientList.length; i++) {
          let client = clientList[i];
          if (client.url.includes('index.html') && 'focus' in client) {
            return client.focus();
          }
        }
        if (clients.openWindow) {
          return clients.openWindow('./index.html');
        }
      })
    );
  });