import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { BrowserRouter } from 'react-router'
import { PwaInstallNotice, PwaInstallProvider } from './components/PwaInstallButton.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <PwaInstallProvider>
      <BrowserRouter>
        <App />
      </BrowserRouter>
      <PwaInstallNotice />
    </PwaInstallProvider>
  </StrictMode>,
)

if ('serviceWorker' in navigator) {
  if (import.meta.env.PROD) {
    window.addEventListener('load', () => {
      navigator.serviceWorker.register('/sw.js').catch(() => {
        // The app remains usable online when service worker registration fails.
      })
    })
  } else {
    void Promise.all([
      navigator.serviceWorker.getRegistrations().then((registrations) => Promise.all(
        registrations
          .filter((registration) => registration.active?.scriptURL.endsWith('/sw.js'))
          .map((registration) => registration.unregister()),
      )),
      caches.keys().then((keys) => Promise.all(
        keys.filter((key) => key.startsWith('finwatch-shell-')).map((key) => caches.delete(key)),
      )),
    ])
  }
}
