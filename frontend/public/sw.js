const CACHE_NAME = 'finwatch-shell-v4'
const APP_SHELL = ['/', '/offline.html', '/manifest.webmanifest', '/finwatch-icon.svg']

self.addEventListener('install', (event) => {
  event.waitUntil(precacheCurrentBuild())
  self.skipWaiting()
})

async function precacheCurrentBuild() {
  const cache = await caches.open(CACHE_NAME)
  const indexResponse = await fetch('/', { cache: 'reload' })
  if (!indexResponse.ok) throw new Error('FinWatch app shell could not be fetched')
  await cache.put('/', indexResponse.clone())
  const html = await indexResponse.text()
  const assetPaths = [...html.matchAll(/(?:src|href)="([^"#]+)"/g)]
    .map((match) => match[1])
    .filter((path) => path.startsWith('/') && !path.startsWith('/api/'))
  const paths = [...new Set([...APP_SHELL.slice(1), ...assetPaths])]
  await Promise.all(paths.map(async (path) => {
    try {
      const response = await fetch(path, { cache: 'reload' })
      if (response.ok) await cache.put(path, response)
    } catch {
      // One optional icon must not prevent the core app shell from installing.
    }
  }))
}

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))),
  )
  self.clients.claim()
})

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || new URL(event.request.url).pathname.startsWith('/api/')) return

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok && new URL(event.request.url).origin === self.location.origin) {
          const copy = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy))
        }
        return response
      })
      .catch(() => {
        if (event.request.mode === 'navigate') return caches.match('/offline.html')
        return caches.match(event.request)
      }),
  )
})
