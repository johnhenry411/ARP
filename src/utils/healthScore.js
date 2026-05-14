import { isHighLatency } from './graph'

export function computeHealthScore(devices) {
  if (!devices || devices.length === 0) return 100
  const offline      = devices.filter(d => d.status === 'OFFLINE').length
  const highLatency  = devices.filter(d => isHighLatency(d, 20)).length
  const score = 100 - (offline * 15) - (highLatency * 5)
  return Math.max(0, Math.min(100, score))
}

export function healthColor(score) {
  if (score >= 70) return '#00D9FF'
  if (score >= 40) return '#FFB800'
  return '#FF2D55'
}
