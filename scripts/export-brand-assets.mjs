import { chromium } from '../frontend/node_modules/@playwright/test/index.mjs'
import { mkdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url))
const projectRoot = path.resolve(scriptDirectory, '..')
const assetDirectory = path.join(projectRoot, 'brand-assets')

const exports = [
  ['finwatch-app-icon.svg', 'finwatch-app-icon-1024.jpg', 1024, 1024, '#0b2233'],
  ['finwatch-logo-light.svg', 'finwatch-logo-light-1600x427.jpg', 1600, 427, '#ffffff'],
  ['finwatch-logo-dark.svg', 'finwatch-logo-dark-1600x427.jpg', 1600, 427, '#07131c'],
]

await mkdir(assetDirectory, { recursive: true })
const browser = await chromium.launch({ headless: true })

try {
  for (const [source, output, width, height, background] of exports) {
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 1 })
    const svg = await readFile(path.join(assetDirectory, source), 'utf8')
    await page.setContent(`<!doctype html><style>*{box-sizing:border-box}html,body{width:100%;height:100%;margin:0;background:${background}}body{display:grid;place-items:center}svg{display:block;width:100%;height:100%}</style>${svg}`)
    await page.locator('svg').waitFor({ state: 'visible' })
    await page.screenshot({ path: path.join(assetDirectory, output), type: 'jpeg', quality: 95 })
    await page.close()
  }
} finally {
  await browser.close()
}
