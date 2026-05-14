export function getLinksForDevice(links, deviceId) {
  return links.filter(l => l.source === deviceId || l.target === deviceId)
}

export function getNeighbours(links, devices, deviceId) {
  const linked = getLinksForDevice(links, deviceId)
  const neighbourIds = linked.map(l => l.source === deviceId ? l.target : l.source)
  return devices.filter(d => neighbourIds.includes(d.id))
}

export function getDeviceStatus(device) {
  return device.status || 'OFFLINE'
}

export function getLatestLatency(device) {
  if (!device.latencies || device.latencies.length === 0) return null
  return device.latencies[device.latencies.length - 1]
}

export function getAverageLatency(device) {
  if (!device.latencies || device.latencies.length === 0) return null
  const sum = device.latencies.reduce((a, b) => a + b, 0)
  return Math.round((sum / device.latencies.length) * 10) / 10
}

export function getMinLatency(device) {
  if (!device.latencies || device.latencies.length === 0) return null
  return Math.min(...device.latencies)
}

export function getMaxLatency(device) {
  if (!device.latencies || device.latencies.length === 0) return null
  return Math.max(...device.latencies)
}

export function isHighLatency(device, threshold = 20) {
  if (!device.latencies || device.latencies.length === 0) return false
  return device.latencies.some(v => v > threshold)
}
