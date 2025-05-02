import { exec } from 'child_process'
import { promisify } from 'util'

export const execAsync = promisify(exec)

export async function rpk(command) {
  const { stdout } = await execAsync(`docker exec redpanda rpk ${command}`)
  return stdout.trim()
}
