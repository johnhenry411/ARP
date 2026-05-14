import { useState, useCallback } from 'react'
import { DEVICES, LINKS, ALERTS } from '../data/mockData'

let alertIdCounter = 10
let deviceIdCounter = 200

function randomLatencyDelta() {
  return Math.floor(Math.random() * 7) - 3
}

export function useSimulation() {
  const [devices, setDevices] = useState(DEVICES)
  const [alerts, setAlerts] = useState(ALERTS)

  const triggerScan = useCallback(() => {
    setDevices(prev => prev.map(device => {
      if (device.status === 'OFFLINE' || device.latencies.length === 0) return device
      const newLatencies = device.latencies.map(v =>
        Math.max(1, v + randomLatencyDelta())
      )
      // occasionally add a spike
      if (Math.random() < 0.1) {
        newLatencies[newLatencies.length - 1] = 60 + Math.floor(Math.random() * 40)
      }
      return { ...device, latencies: newLatencies }
    }))

    // occasionally surface a "ghost" NEW device briefly
    if (Math.random() < 0.25) {
      const ghostId = `ghost-${Date.now()}`
      const ghost = {
        id: ghostId, ip: `192.168.1.${Math.floor(Math.random()*50)+100}`,
        mac: 'FF:FF:FF:FF:FF:FF', hostname: `unknown-${Math.floor(Math.random()*99)}`,
        vendor: 'Unknown', type: 'HOST', status: 'NEW',
        latencies: [20, 22, 18], bandwidth: 10, threatLevel: 'low',
        firstSeen: new Date().toTimeString().slice(0,8),
        isGhost: true,
      }
      setDevices(prev => [...prev, ghost])
      setAlerts(prev => [{
        id: `a${++alertIdCounter}`, type: 'DEVICE_JOINED', deviceId: ghostId,
        message: `${ghost.hostname} joined`, timestamp: ghost.firstSeen,
      }, ...prev])
      // remove after 15s
      setTimeout(() => setDevices(prev => prev.filter(d => d.id !== ghostId)), 15000)
    }

    // random high-latency alert
    if (Math.random() < 0.3) {
      const online = DEVICES.filter(d => d.status === 'ONLINE')
      if (online.length > 0) {
        const d = online[Math.floor(Math.random() * online.length)]
        const ms = 50 + Math.floor(Math.random() * 100)
        setAlerts(prev => [{
          id: `a${++alertIdCounter}`, type: 'HIGH_LATENCY', deviceId: d.id,
          message: `${d.hostname} ${ms}ms spike`,
          timestamp: new Date().toTimeString().slice(0,8),
        }, ...prev.slice(0, 19)])
      }
    }
  }, [])

  const removeDevice = useCallback((id) => {
    setDevices(prev => prev.filter(d => d.id !== id))
    setAlerts(prev => [{
      id: `a${++alertIdCounter}`, type: 'DEVICE_LOST', deviceId: id,
      message: `Device ${id} removed`, timestamp: new Date().toTimeString().slice(0, 8),
    }, ...prev])
  }, [])

  const addDevice = useCallback((formData) => {
    const prefix = formData.type === 'ROUTER' ? 'gw' : formData.type === 'MOBILE' ? 'mob' : 'host'
    const newDevice = {
      id: `${prefix}-${++deviceIdCounter}`,
      ip: formData.ip,
      mac: formData.mac,
      hostname: formData.hostname,
      vendor: formData.vendor || 'Unknown',
      type: formData.type || 'HOST',
      status: 'NEW',
      latencies: [],
      bandwidth: formData.bandwidth || 100,
      threatLevel: 'none',
      firstSeen: new Date().toTimeString().slice(0, 8),
      gatewayId: formData.gatewayId || null,
    }
    setDevices(prev => [...prev, newDevice])
    setAlerts(prev => [{
      id: `a${++alertIdCounter}`, type: 'DEVICE_JOINED', deviceId: newDevice.id,
      message: `${newDevice.hostname} joined`, timestamp: newDevice.firstSeen,
    }, ...prev])
  }, [])

  return { devices, links: LINKS, alerts, triggerScan, addDevice, removeDevice }
}
