import { motion, AnimatePresence } from 'framer-motion'
import { alertColor } from '../utils/colors'

const BG_MAP = {
  DEVICE_JOINED: 'rgba(0,255,156,0.06)',
  DEVICE_LOST:   'rgba(255,45,85,0.06)',
  HIGH_LATENCY:  'rgba(255,184,0,0.06)',
  SCAN_STARTED:  'rgba(51,68,102,0.2)',
}

export default function AlertLog({ alerts, devices }) {
  const unacked = (alerts || []).filter(a => a.type !== 'SCAN_STARTED').length

  if (!alerts || alerts.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-32 gap-2">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="12" r="10" stroke="#1E4060" strokeWidth="1.5"/>
          <path d="M8 12l3 3 5-5" stroke="#00FF9C" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/>
        </svg>
        <span className="text-[11px]" style={{ color: '#1E4060' }}>No alerts yet</span>
      </div>
    )
  }

  return (
    <div>
      <div className="text-[10px] mb-2 italic" style={{ color: '#1E4060' }}>
        {unacked} unacknowledged alert{unacked !== 1 ? 's' : ''}
      </div>
      <div className="text-[9px] font-bold tracking-widest mb-2" style={{ color: '#1E4060' }}>TODAY</div>
      <div className="flex flex-col gap-1.5">
        <AnimatePresence initial={false}>
          {alerts.map(alert => {
            const device  = devices?.find(d => d.id === alert.deviceId)
            const color   = alertColor(alert.type)
            const bg      = BG_MAP[alert.type] || 'rgba(51,68,102,0.2)'
            return (
              <motion.div
                key={alert.id}
                initial={{ x: -20, opacity: 0 }}
                animate={{ x: 0,   opacity: 1 }}
                exit={{   x: 20,   opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="rounded-sm px-2 py-1.5"
                style={{
                  borderLeft: `3px solid ${color}`,
                  background: bg,
                }}
              >
                <div className="text-[10px] font-medium" style={{ color: '#B0D0E8' }}>
                  {alert.message}
                </div>
                {device && (
                  <div className="text-[9px] mt-0.5" style={{ color: '#4A7FA5' }}>
                    {device.hostname}
                  </div>
                )}
                <div className="text-[9px] mt-0.5" style={{ color: '#1E4060' }}>
                  {alert.timestamp}
                </div>
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
    </div>
  )
}
