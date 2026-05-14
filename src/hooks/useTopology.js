import { useSimulation } from './useSimulation'
import { useState, useEffect, useRef, useCallback } from 'react'
import { LINKS, ALERTS } from '../data/mockData'

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

function useWebSocket(url, enabled) {
  const [devices, setDevices] = useState([])
  const [links,   setLinks]   = useState([])
  const [alerts,  setAlerts]  = useState(ALERTS)
  const [status,  setStatus]  = useState('connecting')
  const wsRef    = useRef(null)
  const retryRef = useRef(null)

  useEffect(() => {
    if (!enabled) return
    function connect() {
      setStatus('connecting')
      try {
        const ws = new WebSocket(url)
        wsRef.current = ws
        ws.onopen  = () => setStatus('open')
        ws.onclose = () => { setStatus('closed'); retryRef.current = setTimeout(connect, 5000) }
        ws.onerror = () => { ws.close() }
        ws.onmessage = (e) => {
          try {
            const msg = JSON.parse(e.data)
            if (msg.type === 'FULL_STATE') {
              setDevices(msg.devices)
              setLinks(msg.links || [])
            }
            else if (msg.type === 'SCAN_RESULT') {
              setDevices(prev => {
                let next = [...prev]
                for (const d of (msg.newDevices || [])) {
                  if (!next.find(x => x.id === d.id)) next.push(d)
                }
                for (const id of (msg.lostDevices || [])) {
                  next = next.map(x => x.id === id ? { ...x, status: 'OFFLINE' } : x)
                }
                return next
              })
            }
          } catch {}
        }
      } catch {
        setStatus('closed')
        retryRef.current = setTimeout(connect, 5000)
      }
    }
    connect()
    return () => { wsRef.current?.close(); clearTimeout(retryRef.current) }
  }, [url, enabled])

  const removeDevice = useCallback(async (id) => {
    const res = await fetch(`${import.meta.env.VITE_API_URL}/api/devices/${id}`, {
      method: 'DELETE',
    })
    if (!res.ok && res.status !== 404) {
      throw new Error(`Server error ${res.status}`)
    }
    // Backend broadcasts FULL_STATE over WebSocket — state updates automatically
  }, [])

  const clearDevices = useCallback(async () => {
    const res = await fetch(`${import.meta.env.VITE_API_URL}/api/devices`, { method: 'DELETE' })
    if (!res.ok) throw new Error(`Server error ${res.status}`)
  }, [])

  const addDevice = useCallback(async (formData) => {
    const res = await fetch(`${import.meta.env.VITE_API_URL}/api/devices`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(formData),
    })
    if (!res.ok) {
      const text = await res.text()
      throw new Error(text || `Server error ${res.status}`)
    }
    // Backend broadcasts FULL_STATE over WebSocket — state updates automatically
  }, [])

  return { devices, links, alerts, connectionStatus: status, triggerScan: () => {}, addDevice, removeDevice, clearDevices }
}

export function useTopology() {
  const mock = useSimulation()
  const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8081/topology'
  const live = useWebSocket(wsUrl, !USE_MOCK)
  return USE_MOCK ? { ...mock, connectionStatus: 'mock' } : live

}
