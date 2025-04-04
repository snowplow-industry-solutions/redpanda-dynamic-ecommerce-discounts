import { test, expect } from '@playwright/test'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import crypto from 'crypto'
import { getNextLogFile } from '../../src/logger'

test.describe('getNextLogFile', () => {
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

  test('should return original filename if file does not exist', () => {
    const logFile = path.join(testDir, 'app.log')
    expect(getNextLogFile(logFile)).toBe(logFile)
  })

  test('should return filename with counter 1 if original file exists', () => {
    const logFile = path.join(testDir, 'app.log')
    fs.writeFileSync(logFile, '')
    const expected = path.join(testDir, 'app.1.log')
    expect(getNextLogFile(logFile)).toBe(expected)
  })

  test('should find highest counter and increment it', () => {
    const logFile = path.join(testDir, 'app.log')
    const files = ['app.log', 'app.1.log', 'app.2.log', 'app.5.log']
    files.forEach(file => {
      fs.writeFileSync(path.join(testDir, file), '')
    })
    const expected = path.join(testDir, 'app.6.log')
    expect(getNextLogFile(logFile)).toBe(expected)
  })

  test('should handle non-sequential counters', () => {
    const logFile = path.join(testDir, 'app.log')
    const files = ['app.log', 'app.1.log', 'app.4.log', 'app.7.log']
    files.forEach(file => {
      fs.writeFileSync(path.join(testDir, file), '')
    })
    const expected = path.join(testDir, 'app.8.log')
    expect(getNextLogFile(logFile)).toBe(expected)
  })

  test('should handle different file extensions', () => {
    const logFile = path.join(testDir, 'app.json')
    const files = ['app.json', 'app.1.json', 'app.2.json']
    files.forEach(file => {
      fs.writeFileSync(path.join(testDir, file), '')
    })

    const expected = path.join(testDir, 'app.3.json')
    expect(getNextLogFile(logFile)).toBe(expected)
  })

  test('should ignore unrelated files', () => {
    const logFile = path.join(testDir, 'app.log')
    const files = ['app.log', 'app.1.log', 'other.log', 'other.1.log', 'app.test', 'app.log.bak']
    files.forEach(file => {
      fs.writeFileSync(path.join(testDir, file), '')
    })
    const expected = path.join(testDir, 'app.2.log')
    expect(getNextLogFile(logFile)).toBe(expected)
  })

  test('should handle files with dots in base name', () => {
    const logFile = path.join(testDir, 'app.test.log')
    const files = ['app.test.log', 'app.test.1.log', 'app.test.2.log']
    files.forEach(file => {
      fs.writeFileSync(path.join(testDir, file), '')
    })
    const expected = path.join(testDir, 'app.test.3.log')
    expect(getNextLogFile(logFile)).toBe(expected)
  })
})
