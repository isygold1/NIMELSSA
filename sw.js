/* NIMELSSA Vault — Service Worker
 * Cache-first strategy for core assets; network-first for dynamic content.
 * Author: AGBOOLA ISRAEL OLUWAGBOGO <israelagboola53@gmail.com>
 */

const CACHE_NAME = 'nimelssa-vault-v1';
const PRECACHE_URLS = [
  'index.html',
  'manifest.json',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png'
];

// Install: pre-cache core static assets
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(PRECACHE_URLS);
    })
  );
  self.skipWaiting();
});

// Activate: clean old caches
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => caches.delete(name))
      );
    })
  );
  self.clients.claim();
});

// Fetch: cache-first for static, network-first for others
self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);

  // Only handle same-origin requests
  if (url.origin !== self.location.origin) return;

  // For API-like calls (future), use network-first
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(networkFirst(request));
    return;
  }

  // Cache-first for everything else
  event.respondWith(cacheFirst(request));
});

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (err) {
    return new Response('Offline', { status: 503 });
  }
}

async function networkFirst(request) {
  try {
    const response = await fetch(request);
    if (response.ok) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(request, response.clone());
    }
    return response;
  } catch (err) {
    const cached = await caches.match(request);
    return cached || new Response('Offline', { status: 503 });
  }
}
