export class IntervalTracker {
  constructor(logger, duration = 5 * 60, warningInterval = 30) {
    this.logger = logger;
    this.duration = duration * 1000;
    this.warningInterval = warningInterval * 1000;
    this.cycleData = new Map();
    this.lastCycleTime = Date.now();
    this.startTime = Date.now();
    this.logger.info(`Starting new ${this.formatDuration()} cycle`);
  }

  formatTimeRemaining(remainingSeconds) {
    const minutes = Math.floor(remainingSeconds / 60);
    const seconds = remainingSeconds % 60;
    
    if (minutes > 0) {
      return `${minutes} minutes and ${seconds} seconds`;
    }
    return `${seconds} seconds`;
  }

  formatDuration() {
    const seconds = this.duration / 1000;
    if (seconds >= 60) {
      const minutes = seconds / 60;
      return `${minutes}-minute`;
    }
    return `${seconds}-second`;
  }

  getCurrentCycleTime() {
    return Date.now() - this.startTime;
  }

  getRemainingTime() {
    return this.duration - this.getCurrentCycleTime();
  }

  isNewCycle() {
    const now = Date.now();
    if (now - this.lastCycleTime >= this.duration) {
      this.lastCycleTime = now;
      this.startTime = now; // Reinicializa o startTime
      this.logger.info(`Starting new ${this.formatDuration()} cycle`);
      return true;
    }
    return false;
  }

  getCycleProgress() {
    const elapsedMs = this.getCurrentCycleTime();
    return {
      elapsedMs,
      remainingMs: this.duration - elapsedMs,
      totalMs: this.duration,
      percentComplete: Math.min(100, (elapsedMs / this.duration) * 100) // Limita a 100%
    };
  }

  // Métodos para gerenciar dados do ciclo
  setCycleData(key, value) {
    this.cycleData.set(key, value);
  }

  getCycleData(key) {
    return this.cycleData.get(key);
  }

  clearCycleData() {
    this.cycleData.clear();
  }

  start() {
    this.logger.info(`Starting new ${this.formatDuration()} cycle`);
    this.startTime = Date.now();
    this.clearCycleData();
    
    this.interval = setInterval(() => {
      this.logger.info(`Starting new ${this.formatDuration()} cycle`);
      this.startTime = Date.now();
      this.lastWarningTime = 0;
      this.clearCycleData();
    }, this.duration);

    this.warningTimer = setInterval(() => {
      const remainingTime = this.getRemainingTime();
      const remainingSeconds = Math.floor(remainingTime / 1000);

      this.logger.info(`${this.formatTimeRemaining(remainingSeconds)} remaining until new cycle starts`);
    }, this.warningInterval);
  }

  stop() {
    if (this.interval) {
      clearInterval(this.interval);
    }
    if (this.warningTimer) {
      clearInterval(this.warningTimer);
    }
  }
}
