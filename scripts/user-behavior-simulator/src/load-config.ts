import { existsSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'
import { Config } from './types'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const PROJECT_ROOT = join(__dirname, '..')

export async function loadConfig(): Promise<Config> {
  const configPaths = {
    user: join(PROJECT_ROOT, 'config.ts'),
    sample: join(PROJECT_ROOT, 'config.sample.ts'),
  }

  const configExists = existsSync(configPaths.user)
  const sampleConfigExists = existsSync(configPaths.sample)

  if (!configExists && !sampleConfigExists) {
    throw new Error(
      'Configuration files not found. Please ensure either config.ts or config.sample.ts exists in the project root directory.'
    )
  }

  try {
    const configPath = configExists ? configPaths.user : configPaths.sample
    const configModule = await import(configPath)
    const config = configModule.default as Config
    validateConfig(config)
    return config
  } catch (error) {
    if (error instanceof Error) {
      throw new Error(`Failed to load configuration: ${error.message}`)
    }
    throw new Error('An unknown error occurred while loading the configuration')
  }
}

function validateConfig(config: Config): void {
  if (!config.kafka?.brokers?.length) {
    throw new Error('Kafka brokers configuration is missing or empty')
  }
  if (!config.logging?.level) {
    throw new Error('Logging level configuration is missing')
  }
  if (!config.simulation) {
    throw new Error('Simulation configuration is missing')
  }
  if (!config.mocks?.users?.length || !config.mocks?.products?.length) {
    throw new Error('Mocks configuration is missing or incomplete')
  }
}
