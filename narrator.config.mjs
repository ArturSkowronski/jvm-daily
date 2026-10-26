import { scenes } from './demo/manifest.mjs'

export default {
  projectName: 'jvm-daily',
  scenes,
  playwrightConfigPath: './demo/playwright.config.ts',
  segments: [{ grepTag: '@demo', mode: 'default' }],
  outputDir: './demo-output',
  baseURL: 'http://localhost:8888',
}
