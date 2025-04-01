import { Logger } from './types'

export class IntervalTracker {
  private logger: Logger
  private duration: number
  private warningInterval: number
  private cycleData: Map<string, any>
  private lastCycleTime: number
  private startTime: number
  private interval?: NodeJS.Timeout
  private warningTimer?: NodeJS.Timeout
  private onCycleEnd?: () => void

  constructor(
    logger: Logger,
    duration: number = 5 * 60,
    warningInterval: number = 30,
    onCycleEnd?: () => void
  ) {
    this.logger = logger
    this.duration = duration * 1000
    this.warningInterval = warningInterval * 1000
    this.cycleData = new Map()
    this.lastCycleTime = Date.now()
    this.startTime = Date.now()
    this.onCycleEnd = onCycleEnd
    this.logger.info(`Starting first cycle of ${this.formatDuration()}`)
  }

  private formatTimeRemaining(remainingSeconds: number): string {
    const minutes = Math.floor(remainingSeconds / 60)
    const seconds = remainingSeconds % 60

    if (minutes > 0) {
      return `${minutes} minutes and ${seconds} seconds`
    }
    return `${seconds} seconds`
  }

  private formatDuration(): string {
    const seconds = this.duration / 1000
    if (seconds >= 60) {
      const minutes = seconds / 60
      return `${minutes}-minute`
    }
    return `${seconds}-second`
  }

  getCurrentCycleTime(): number {
    return Date.now() - this.startTime
  }

  getRemainingTime(): number {
    return this.duration - this.getCurrentCycleTime()
  }

  isNewCycle(): boolean {
    const now = Date.now()
    if (now - this.lastCycleTime >= this.duration) {
      if (this.onCycleEnd) {
        this.logger.info('Cycle completed')
        this.onCycleEnd()
      }
      this.lastCycleTime = now
      this.startTime = now
      this.logger.info(`Starting new ${this.formatDuration()} cycle`)
      return true
    }
    return false
  }

  getCycleProgress(): {
    elapsedMs: number
    remainingMs: number
    totalMs: number
    percentComplete: number
  } {
    const elapsedMs = this.getCurrentCycleTime()
    return {
      elapsedMs,
      remainingMs: this.duration - elapsedMs,
      totalMs: this.duration,
      percentComplete: Math.min(100, (elapsedMs / this.duration) * 100),
    }
  }

  setCycleData<T>(key: string, value: T): void {
    this.cycleData.set(key, value)
  }

  getCycleData<T>(key: string): T | undefined {
    return this.cycleData.get(key)
  }

  clearCycleData(): void {
    this.cycleData.clear()
  }

  start(): void {
    this.logger.info(`Starting new ${this.formatDuration()} cycle`)
    this.startTime = Date.now()
    this.clearCycleData()

    this.interval = setInterval(() => {
      this.logger.info(`Starting new ${this.formatDuration()} cycle`)
      this.startTime = Date.now()
      this.clearCycleData()
    }, this.duration)

    this.warningTimer = setInterval(() => {
      const remainingTime = this.getRemainingTime()
      const remainingSeconds = Math.floor(remainingTime / 1000)
      this.logger.info(
        `${this.formatTimeRemaining(remainingSeconds)} remaining until new cycle starts`
      )
    }, this.warningInterval)
  }

  stop(): void {
    if (this.interval) {
      clearInterval(this.interval)
    }
    if (this.warningTimer) {
      clearInterval(this.warningTimer)
    }
  }

  setOnCycleEnd(callback: () => void): void {
    this.onCycleEnd = callback
  }
}
