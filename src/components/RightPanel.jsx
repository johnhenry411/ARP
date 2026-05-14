import { motion, AnimatePresence } from 'framer-motion'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import Inspector from './Inspector'
import AlertLog  from './AlertLog'
import DnsPanel  from './DnsPanel'

export default function RightPanel({
  devices, alerts, selectedDeviceId, activeTab, onTabChange,
  onSelectDevice, onRemoveDevice, collapsed, onToggle,
  dnsPreset, onClearDnsPreset,
}) {
  const device     = devices.find(d => d.id === selectedDeviceId) || null
  const alertCount = (alerts || []).filter(a => a.type !== 'SCAN_STARTED').length

  const TABS = [
    { id: 'inspector', label: 'INSPECT' },
    { id: 'alerts',    label: 'ALERTS', badge: alertCount },
    { id: 'dns',       label: 'DNS' },
  ]

  return (
    <motion.div
      animate={{ width: collapsed ? 48 : 250 }}
      transition={{ type: 'spring', stiffness: 300, damping: 30 }}
      className="relative flex flex-col shrink-0 overflow-hidden"
      style={{ background: '#060E1C', borderLeft: '1px solid #0F2744' }}
    >
      <button
        onClick={onToggle}
        className="absolute top-2 -left-3 z-10 flex items-center justify-center rounded-full w-6 h-6"
        style={{ background: '#0A1628', border: '1px solid #0F2744', color: '#4A7FA5' }}
      >
        {collapsed ? <ChevronLeft size={10} /> : <ChevronRight size={10} />}
      </button>

      <AnimatePresence>
        {collapsed ? (
          <motion.div key="icons" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="flex flex-col items-center pt-4 gap-3">
            <span className="text-[10px]" style={{ color: '#4A7FA5' }}>⌖</span>
            {alertCount > 0 && (
              <span className="text-[9px] rounded-full w-4 h-4 flex items-center justify-center"
                style={{ background: '#FF2D5522', color: '#FF2D55', border: '1px solid #FF2D5544' }}>
                {alertCount}
              </span>
            )}
          </motion.div>
        ) : (
          <motion.div key="full" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="flex flex-col h-full overflow-hidden">
            {/* Tab bar */}
            <div className="flex shrink-0" style={{ borderBottom: '1px solid #0F2744' }}>
              {TABS.map(tab => {
                const active = activeTab === tab.id
                return (
                  <button key={tab.id} onClick={() => onTabChange(tab.id)}
                    className="flex-1 flex items-center justify-center gap-1 py-2 text-[10px] font-bold transition-colors"
                    style={{
                      color:        active ? '#00D9FF' : '#1E4060',
                      borderBottom: active ? '2px solid #00D9FF' : '2px solid transparent',
                      background:   'transparent',
                    }}>
                    {tab.label}
                    {tab.badge > 0 && (
                      <span className="rounded-full w-4 h-4 flex items-center justify-center text-[8px]"
                        style={{ background: '#FF2D5522', color: '#FF2D55', border: '1px solid #FF2D5544' }}>
                        {tab.badge}
                      </span>
                    )}
                  </button>
                )
              })}
            </div>

            {/* Content */}
            <div className="flex-1 overflow-y-auto px-3 py-3">
              <AnimatePresence mode="wait">
                {activeTab === 'inspector' ? (
                  <motion.div key="inspector" initial={{ x: 10, opacity: 0 }} animate={{ x: 0, opacity: 1 }}
                    exit={{ x: -10, opacity: 0 }} transition={{ duration: 0.15 }}>
                    <Inspector device={device} onRemove={onRemoveDevice} />
                  </motion.div>
                ) : activeTab === 'alerts' ? (
                  <motion.div key="alerts" initial={{ x: 10, opacity: 0 }} animate={{ x: 0, opacity: 1 }}
                    exit={{ x: -10, opacity: 0 }} transition={{ duration: 0.15 }}>
                    <AlertLog alerts={alerts} devices={devices} />
                  </motion.div>
                ) : (
                  <motion.div key="dns" initial={{ x: 10, opacity: 0 }} animate={{ x: 0, opacity: 1 }}
                    exit={{ x: -10, opacity: 0 }} transition={{ duration: 0.15 }}>
                    <DnsPanel
                      prefilledDomain={dnsPreset?.domain}
                      prefilledIp={dnsPreset?.ip}
                      onConsumePreset={onClearDnsPreset}
                    />
                  </motion.div>
                )}
              </AnimatePresence>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}
