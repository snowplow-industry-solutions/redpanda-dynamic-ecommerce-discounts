export interface Product {
  id: string
  name: string
  price: number
  webpage_id: string
}

export interface User {
  id: string
  name: string
}

export type LogLevel = 'debug' | 'info' | 'warn' | 'error'

export interface LoggerConfig {
  level: LogLevel
  timestamp: boolean
  showLevel: boolean
}

export interface Config {
  kafka: {
    clientId: string
    brokers: string[]
    topic: string
  }
  logging: {
    console: LoggerConfig
    file: LoggerConfig
  }
  mocks: {
    users: User[]
    products: Product[]
  }
  simulation: {
    cycle: {
      duration: number
      warningInterval: number
    }
    longView: {
      pagePingInterval: number
      duration: number
    }
    frequentView: {
      minDuration: number
      maxDuration: number
    }
    betweenLongViewInterval: number
    betweenNormalViewInterval: number
    snowplowEcommerceActionInterval: number
  }
}

export interface Logger {
  debug: (message: string, caller?: string) => void
  info: (message: string, caller?: string) => void
  warn: (message: string, caller?: string) => void
  error: (message: string, caller?: string) => void
  cleanup: () => void
}

export interface ProductStats {
  views: number
  totalDuration: number
}

export interface IntervalTracker {
  getCycleData(arg0: string): unknown
  setCycleData(arg0: string, arg1: Map<string, ProductStats>): unknown
  start: () => void
  stop: () => void
  getCurrentCycleTime: () => number
  getCycleProgress: () => {
    elapsedMs: number
    remainingMs: number
    totalMs: number
    percentComplete: number
  }
  isNewCycle: () => boolean
  checkIfNewCycle: (callback?: () => void) => void
  setOnCycleEnd: (callback: () => void) => void
}
