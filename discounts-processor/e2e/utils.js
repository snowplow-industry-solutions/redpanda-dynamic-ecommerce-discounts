import { exec, spawn } from 'child_process'
import { promisify } from 'util'

export const execAsync = promisify(exec)

export async function rpk(command) {
  const { stdout } = await execAsync(`docker exec redpanda rpk ${command}`)
  return stdout.trim()
}

export async function rpkProduce(topic, key, value) {
  return new Promise((resolve, reject) => {
    const proc = spawn('docker', [
      'exec',
      '-i',
      'redpanda',
      'rpk',
      'topic',
      'produce',
      topic,
      '-k',
      key
    ])
    proc.stdin.write(value + '\n')
    proc.stdin.end()
    proc.on('close', code => {
      if (code === 0) resolve()
      else reject(new Error(`rpk produce exited with code ${code}`))
    })
    proc.on('error', reject)
  })
}
