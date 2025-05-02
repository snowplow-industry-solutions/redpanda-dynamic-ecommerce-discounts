import { jest } from '@jest/globals'
import { parseTimeOffset, createBaseTime } from './common.js'

describe('parseTimeOffset', () => {
  test('should parse time offset in HH:MM:SS format', () => {
    expect(parseTimeOffset('01:30:45')).toBe(5445) // 1h*3600 + 30m*60 + 45s
    expect(parseTimeOffset('00:15:30')).toBe(930) // 15m*60 + 30s
    expect(parseTimeOffset('02:00:00')).toBe(7200) // 2h*3600
  })

  test('should handle zero values', () => {
    expect(parseTimeOffset('00:00:00')).toBe(0)
  })

  test('should handle numeric input', () => {
    expect(parseTimeOffset(3600)).toBe(3600)
  })
})

describe('createBaseTime', () => {
  beforeEach(() => {
    // Mock Date.now() to return a fixed timestamp
    jest.useFakeTimers()
    jest.setSystemTime(new Date('2024-01-01T12:00:00Z'))
  })

  afterEach(() => {
    jest.useRealTimers()
  })

  test('should subtract time offset from current time', () => {
    const result = createBaseTime('01:00:00') // 1 hour offset
    expect(result.toISOString()).toBe('2024-01-01T11:00:00.000Z')
  })

  test('should handle zero offset', () => {
    const result = createBaseTime('00:00:00')
    expect(result.toISOString()).toBe('2024-01-01T12:00:00.000Z')
  })

  test('should handle minutes and seconds', () => {
    const result = createBaseTime('00:30:15') // 30 minutes and 15 seconds offset
    expect(result.toISOString()).toBe('2024-01-01T11:29:45.000Z')
  })

  test('should handle numeric offset', () => {
    const result = createBaseTime(3600) // 1 hour in seconds
    expect(result.toISOString()).toBe('2024-01-01T11:00:00.000Z')
  })
})
