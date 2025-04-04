import fs from 'fs'
import { LoggerConfig, Logger, LogLevel } from './types.js'

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

export class AppLogger implements Logger {
  private config: LoggerConfig
  private currentLevel: number
  private logFile: fs.WriteStream | null = null

  constructor(config: LoggerConfig) {
    this.config = config
    this.currentLevel = LOG_LEVELS[config.level]
    
    const logFilePath = process.env.LOG_FILE
    if (logFilePath) {
      this.logFile = fs.createWriteStream(logFilePath, { flags: 'a' })
    }
  }

  private formatMessage(level: LogLevel, message: string): string {
    const parts: string[] = []

    if (this.config.timestamp) {
      parts.push(`[${new Date().toISOString()}]`)
    }

    if (this.config.showLevel) {
      parts.push(`${level.toUpperCase()}:`)
    }

    parts.push(message)

    return parts.join(' ')
  }

  private log(level: LogLevel, message: string): void {
    if (LOG_LEVELS[level] >= this.currentLevel) {
      const formattedMessage = this.formatMessage(level, message)
      
      // Write to console
      console.log(formattedMessage)
      
      // Write to file if available
      if (this.logFile) {
        this.logFile.write(formattedMessage + '\n')
      }
    }
  }

  debug(message: string): void {
    this.log('debug', message)
  }

  info(message: string): void {
    this.log('info', message)
  }

  warn(message: string): void {
    this.log('warn', message)
  }

  error(message: string): void {
    this.log('error', message)
  }

  cleanup(): void {
    if (this.logFile) {
      this.logFile.end()
    }
  }
}

export function createLogger(config: LoggerConfig): Logger {
  return new AppLogger(config)
}
