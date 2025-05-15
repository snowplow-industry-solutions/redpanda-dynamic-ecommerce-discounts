#!/usr/bin/env node
import fs from 'fs/promises'
import path from 'path'
import { rpkProduce } from './utils.js'
import yargs from 'yargs'
import { hideBin } from 'yargs/helpers'

const TOPIC = 'snowplow-enriched-good'
const USER_ID = 'user1'
const PROCESSING_OVERHEAD_MS = 0

const argv = yargs(hideBin(process.argv))
  .option('file', {
    alias: 'f',
    describe: 'Path to JSONL file with events',
    type: 'string',
    demandOption: true
  })
  .option('overhead', {
    alias: 'o',
    describe: 'Processing overhead time in milliseconds',
    type: 'number',
    default: PROCESSING_OVERHEAD_MS
  })
  .option('send', {
    alias: 's',
    describe: 'Send events to Kafka topic',
    type: 'boolean',
    default: true
  })
  .help()
  .alias('help', 'h').argv

function formatTime(ms) {
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.floor((ms % 60000) / 1000)
  const milliseconds = ms % 1000
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}.${milliseconds.toString().padStart(3, '0')}`
}

async function sendEvents(filePath, overheadMs = PROCESSING_OVERHEAD_MS, sendToKafka = true) {
  try {
    const startTime = Date.now()
    const content = await fs.readFile(filePath, 'utf8')
    const events = content
      .split('\n')
      .filter(line => line.trim())
      .map(line => JSON.parse(line))

    const originalTimestamps = events.map(e => new Date(e.collector_tstamp).getTime())

    console.log(`Processing ${events.length} events from ${path.basename(filePath)}...\n`)

    const outputFilePath = filePath.replace(/\.jsonl$/, '.now.jsonl')
    await fs.writeFile(outputFilePath, '')

    let totalWaitTime = 0

    for (let i = 0; i < events.length; i++) {
      events[i].collector_tstamp = new Date().toISOString()
      const eventJson = JSON.stringify(events[i])
      await fs.appendFile(outputFilePath, eventJson + '\n')

      if (sendToKafka) {
        await rpkProduce(TOPIC, USER_ID, eventJson)
        console.log(`Sent event ${i + 1}/${events.length}: ${events[i].event_name}`)
      } else {
        console.log(`Processed event ${i + 1}/${events.length}: ${events[i].event_name} (not sent)`)
      }

      if (i < events.length - 1) {
        const currOriginal = originalTimestamps[i]
        const nextOriginal = originalTimestamps[i + 1]
        const waitTime = Math.max(0, nextOriginal - currOriginal - overheadMs)
        if (waitTime > 0) {
          const formattedWaitTime = formatTime(waitTime)
          console.log(`Waiting ${formattedWaitTime} before next event...`)
          totalWaitTime += waitTime
          await new Promise(resolve => setTimeout(resolve, waitTime))
        }
      }
    }

    const endTime = Date.now()
    const actualProcessingTime = endTime - startTime - totalWaitTime
    const formattedWaitTime = formatTime(totalWaitTime)
    const formattedProcessingTime = formatTime(actualProcessingTime)
    const formattedTotalTime = formatTime(endTime - startTime)

    const actionMsg = sendToKafka ? 'sent' : 'processed (not sent)'
    console.log(
      `\nAll events ${actionMsg}! Updated events saved to ${path.basename(outputFilePath)}`
    )
    console.log(`Wait time: ${formattedWaitTime}`)
    console.log(`Processing time: ${formattedProcessingTime}`)
    console.log(`Total time: ${formattedTotalTime}`)
  } catch (error) {
    console.error('Error processing events:', error)
    process.exit(1)
  }
}

sendEvents(argv.file, argv.overhead, argv.send)
