import { Logger, LogLevel, LoggerConfig } from './types'

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

export class AppLogger implements Logger {
  private config: LoggerConfig
  private currentLevel: number

  constructor(config: LoggerConfig) {
    this.config = config
    this.currentLevel = LOG_LEVELS[config.level]
  }

  private shouldLog(level: LogLevel): boolean {
    return LOG_LEVELS[level] >= this.currentLevel
  }

  private formatMessage(level: LogLevel, message: string): string {
    const parts: string[] = []

    if (this.config.timestamp) {
      parts.push(`[${new Date().toISOString()}]`)
    }

    parts.push(`${level.toUpperCase()}:`)
    parts.push(message)

    return parts.join(' ')
  }

  debug(message: string): void {
    if (this.shouldLog('debug')) {
      console.log(this.formatMessage('debug', message))
    }
  }

  info(message: string): void {
    if (this.shouldLog('info')) {
      console.log(this.formatMessage('info', message))
    }
  }

  warn(message: string): void {
    if (this.shouldLog('warn')) {
      console.warn(this.formatMessage('warn', message))
    }
  }

  error(message: string): void {
    if (this.shouldLog('error')) {
      console.error(this.formatMessage('error', message))
    }
  }
}

export function createLogger(config: LoggerConfig): Logger {
  return new AppLogger(config)
}
