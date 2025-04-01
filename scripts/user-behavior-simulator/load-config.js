import { existsSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export async function loadConfig() {
  const configPath = join(__dirname, 'config.js');
  const sampleConfigPath = join(__dirname, 'config.sample.js');

  if (!existsSync(sampleConfigPath)) {
    console.error('Error: config.sample.js not found!');
    process.exit(1);
  }

  try {
    if (existsSync(configPath)) {
      const config = await import('./config.js');
      return config.default;
    } else {
      const config = await import('./config.sample.js');
      return config.default;
    }
  } catch (error) {
    console.error('Error loading configuration:', error);
    process.exit(1);
  }
}