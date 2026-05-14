import { useState, useEffect, useRef } from 'react'

export function useScanner(onScanCycle) {
  const [scanRunning, setScanRunning] = useState(true)
  const [secondsUntilNextScan, setSecondsUntilNextScan] = useState(27)
  const onCycleRef = useRef(onScanCycle)

  useEffect(() => { onCycleRef.current = onScanCycle }, [onScanCycle])

  useEffect(() => {
    if (!scanRunning) return
    const interval = setInterval(() => {
      setSecondsUntilNextScan(s => {
        if (s <= 1) {
          onCycleRef.current?.()
          return 30
        }
        return s - 1
      })
    }, 1000)
    return () => clearInterval(interval)
  }, [scanRunning])

  const toggleScan = () => {
    setScanRunning(r => !r)
    setSecondsUntilNextScan(30)
  }

  return { scanRunning, secondsUntilNextScan, toggleScan }
}
