import { test, expect } from '@playwright/test'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import crypto from 'crypto'
import { updateLatestLogFile } from '../../src/logger'

test.describe('updateLatestLogFile', () => {
  const __filename = fileURLToPath(import.meta.url)
  const __dirname = path.dirname(__filename)
  const uniqueId = crypto.randomBytes(8).toString('hex')
  const testDir = path.join(__dirname, `test-logs-${uniqueId}`)

  test.beforeEach(async () => {
    if (fs.existsSync(testDir)) {
      fs.rmSync(testDir, { recursive: true })
    }
    fs.mkdirSync(testDir)
  })

  test.afterEach(async () => {
    if (fs.existsSync(testDir)) {
      fs.rmSync(testDir, { recursive: true })
    }
  })

  test('should create latest file with log filename', () => {
    const logFile = path.join(testDir, 'app.log')

    updateLatestLogFile(logFile)

    const latestFile = path.join(testDir, 'latest')
    expect(fs.existsSync(latestFile)).toBeTruthy()
    expect(fs.readFileSync(latestFile, 'utf8')).toBe('app.log')
  })

  test('should update existing latest file', () => {
    const logFile1 = path.join(testDir, 'app.1.log')
    const logFile2 = path.join(testDir, 'app.2.log')

    updateLatestLogFile(logFile1)
    updateLatestLogFile(logFile2)

    const latestFile = path.join(testDir, 'latest')
    expect(fs.readFileSync(latestFile, 'utf8')).toBe('app.2.log')
  })

  test('should handle nested directories', () => {
    const nestedDir = path.join(testDir, 'nested', 'logs')
    fs.mkdirSync(nestedDir, { recursive: true })

    const logFile = path.join(nestedDir, 'app.log')
    fs.writeFileSync(logFile, '')

    updateLatestLogFile(logFile)

    const latestFile = path.join(nestedDir, 'latest')
    expect(fs.existsSync(latestFile)).toBeTruthy()
    expect(fs.readFileSync(latestFile, 'utf8')).toBe('app.log')
  })

  test('should overwrite existing latest file content', () => {
    const logFile = path.join(testDir, 'new.log')
    const latestFile = path.join(testDir, 'latest')

    fs.writeFileSync(latestFile, 'old.log')

    updateLatestLogFile(logFile)

    expect(fs.readFileSync(latestFile, 'utf8')).toBe('new.log')
  })
})
