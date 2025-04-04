import fs from 'fs'
import path from 'path'
import { Config, Logger, LogLevel } from './types'

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

export function getNextLogFile(logFile: string): string {
  const dir = path.dirname(logFile)
  const ext = path.extname(logFile)
  const baseName = path.basename(logFile, ext)

  if (!fs.existsSync(logFile)) {
    return logFile
  }
  const files = fs
    .readdirSync(dir)
    .filter(file => file.startsWith(baseName) && file.endsWith(ext))
    .map(file => {
      const match = file.match(new RegExp(`^${baseName}(?:\\.(\\d+))?${ext}$`))
      return match ? { file, counter: match[1] ? parseInt(match[1]) : 0 } : null
    })
    .filter((item): item is { file: string; counter: number } => item !== null)
  const maxCounter = files.reduce((max, item) => Math.max(max, item.counter), 0)
  return path.join(dir, `${baseName}.${maxCounter + 1}${ext}`)
}

export function updateLatestLogFile(logFile: string): void {
  const dir = path.dirname(logFile)
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true })
  }
  const latestFile = path.join(dir, 'latest')
  const logFileName = path.basename(logFile)
  fs.writeFileSync(latestFile, logFileName)
}

export class AppLogger implements Logger {
  private logFile: string | null = null
  private consoleConfig: Config['logging']['console']
  private fileConfig: Config['logging']['file']
  private logStream: fs.WriteStream | null = null

  constructor(config: Config['logging']) {
    this.consoleConfig = config.console
    this.fileConfig = config.file

    const envLogFile = process.env.LOG_FILE
    if (envLogFile) {
      this.logFile = getNextLogFile(envLogFile)
      this.logStream = fs.createWriteStream(this.logFile, { flags: 'a' })
      updateLatestLogFile(this.logFile)
      this.info(`Log file: ${this.logFile}`)
    }
  }

  private formatConsoleMessage(level: LogLevel, message: string, caller?: string): string {
    let output = message

    if (this.consoleConfig.showLevel) {
      output = `${level.toUpperCase()}: ${output}`
    }

    if (this.consoleConfig.timestamp) {
      output = `[${new Date().toISOString()}] ${output}`
    }

    if (caller) {
      output = `${output} (${caller})`
    }

    return output
  }

  private writeToFile(data: object): void {
    if (this.logStream) {
      this.logStream.write(JSON.stringify(data) + '\n')
    }
  }

  private log(level: LogLevel, message: string, caller?: string): void {
    if (LOG_LEVELS[level] >= LOG_LEVELS[this.consoleConfig.level]) {
      console.log(this.formatConsoleMessage(level, message, caller))
    }

    if (this.logStream && LOG_LEVELS[level] >= LOG_LEVELS[this.fileConfig.level]) {
      this.writeToFile({
        timestamp: new Date().toISOString(),
        level,
        message,
        ...(caller && { caller }),
      })
    }
  }

  debug(message: string, caller?: string): void {
    this.log('debug', message, caller)
  }

  info(message: string, caller?: string): void {
    this.log('info', message, caller)
  }

  warn(message: string, caller?: string): void {
    this.log('warn', message, caller)
  }

  error(message: string, caller?: string): void {
    this.log('error', message, caller)
  }

  cleanup(): void {
    if (this.logStream) {
      this.logStream.end()
    }
  }
}

export function createLogger(config: Config['logging']): Logger {
  return new AppLogger(config)
}
