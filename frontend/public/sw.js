const CACHE_NAME = 'finwatch-shell-v12'
const APP_SHELL = [
  '/',
  '/offline.html',
  '/manifest.webmanifest',
  '/favicon.png',
  '/apple-touch-icon.png',
  '/finwatch-logo.png',
  '/finwatch-logo-dark.png',
  '/finwatch-icon-64.png',
  '/finwatch-icon-128.png',
  '/finwatch-icon-192.png',
  '/finwatch-icon-256.png',
  '/finwatch-icon-512.png',
  '/finwatch-icon-maskable-512.png',
]

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
  const manifestPaths = await loadBuildManifestPaths()
  const paths = [...new Set([...APP_SHELL.slice(1), ...assetPaths, ...manifestPaths])]
  await Promise.all(paths.map(async (path) => {
    try {
      const response = await fetch(path, { cache: 'reload' })
      if (response.ok) await cache.put(path, response)
    } catch {
      // One optional icon must not prevent the core app shell from installing.
    }
  }))
}

async function loadBuildManifestPaths() {
  try {
    const response = await fetch('/.vite/manifest.json', { cache: 'reload' })
    if (!response.ok) return []
    const manifest = await response.json()
    const paths = ['/.vite/manifest.json']
    for (const entry of Object.values(manifest)) {
      if (typeof entry?.file === 'string') paths.push(`/${entry.file}`)
      for (const path of [...(entry?.css ?? []), ...(entry?.assets ?? [])]) {
        if (typeof path === 'string') paths.push(`/${path}`)
      }
    }
    return paths
  } catch {
    return []
  }
}

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))),
  )
  self.clients.claim()
})

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  if (event.request.method !== 'GET' || url.pathname.startsWith('/api/')) return
  if (url.pathname.startsWith('/src/') || url.pathname.startsWith('/@') || url.pathname.startsWith('/node_modules/')) return

  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          if (response.ok && url.origin === self.location.origin) {
            const copy = response.clone()
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy))
          }
          return response
        })
        .catch(() => caches.match('/').then((shell) => shell || caches.match('/offline.html'))),
    )
    return
  }

  event.respondWith(
    caches.match(event.request, { ignoreVary: true }).then((cached) => cached || fetch(event.request).then((response) => {
      if (response.ok && url.origin === self.location.origin) {
        const copy = response.clone()
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy))
      }
      return response
    })),
  )
})
