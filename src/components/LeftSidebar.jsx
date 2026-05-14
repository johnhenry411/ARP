import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronLeft, ChevronRight, Search, Wifi, Monitor, Smartphone, LayoutList, Plus, Trash2 } from 'lucide-react'
import { getAverageLatency } from '../utils/graph'
import { computeHealthScore } from '../utils/healthScore'
import DeviceList from './DeviceList'
import AddDeviceModal from './AddDeviceModal'

const FILTERS = [
  { id: 'ALL',    label: 'ALL',    icon: <LayoutList size={10} /> },
  { id: 'ROUTER', label: 'ROUT',   icon: <Wifi size={10} /> },
  { id: 'HOST',   label: 'HOST',   icon: <Monitor size={10} /> },
  { id: 'MOBILE', label: 'MOB',    icon: <Smartphone size={10} /> },
]

const STATUS_FILTERS = [
  { id: 'ALL',     label: 'ALL',     color: '#4A7FA5' },
  { id: 'ONLINE',  label: 'ONLINE',  color: '#00FF9C' },
  { id: 'OFFLINE', label: 'OFFLINE', color: '#FF2D55' },
  { id: 'NEW',     label: 'NEW',     color: '#FFB800' },
]

export default function LeftSidebar({
  devices, filteredDevices, collapsed, onToggle, selectedDeviceId, onSelectDevice,
  addDevice, clearDevices,
  search, onSearchChange, typeFilter, onTypeFilterChange, statusFilter, onStatusFilterChange,
}) {
  const [showAddModal,  setShowAddModal]  = useState(false)
  const [confirmClear,  setConfirmClear]  = useState(false)

  const online  = devices.filter(d => d.status !== 'OFFLINE').length
  const offline = devices.filter(d => d.status === 'OFFLINE').length
  const avgArr  = devices.flatMap(d => d.latencies || [])
  const avgMs   = avgArr.length ? Math.round(avgArr.reduce((a,b)=>a+b,0)/avgArr.length) : 0

  return (
    <motion.div
      animate={{ width: collapsed ? 48 : 190 }}
      transition={{ type: 'spring', stiffness: 300, damping: 30 }}
      className="relative flex flex-col shrink-0 overflow-hidden"
      style={{ background: '#060E1C', borderRight: '1px solid #0F2744' }}
    >
      {/* Collapse toggle */}
      <button
        onClick={onToggle}
        className="absolute top-2 -right-3 z-10 flex items-center justify-center rounded-full w-6 h-6"
        style={{ background: '#0A1628', border: '1px solid #0F2744', color: '#4A7FA5' }}
      >
        {collapsed ? <ChevronRight size={10} /> : <ChevronLeft size={10} />}
      </button>

      <AnimatePresence>
        {collapsed ? (
          <motion.div
            key="icons"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="flex flex-col items-center pt-4 gap-3"
          >
            <div className="text-[10px] font-bold" style={{ color: '#00D9FF' }}>{devices.length}</div>
            <div className="text-[10px]" style={{ color: '#00FF9C' }}>{online}</div>
            <div className="text-[10px]" style={{ color: '#FF2D55' }}>{offline}</div>
          </motion.div>
        ) : (
          <motion.div
            key="full"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="flex flex-col h-full overflow-hidden"
          >
            {/* Health summary bar */}
            <div className="px-3 pt-3 pb-2" style={{ borderBottom: '1px solid #0F2744' }}>
              <div className="text-[9px] font-bold tracking-widest mb-2" style={{ color: '#1E4060' }}>
                NETWORK STATUS
              </div>
              <div className="grid grid-cols-2 gap-1">
                <StatChip label="TOTAL"   value={devices.length} color="#4A7FA5" />
                <StatChip label="ONLINE"  value={online}          color="#00FF9C" />
                <StatChip label="OFFLINE" value={offline}         color="#FF2D55" />
                <StatChip label="AVG LAT" value={`${avgMs}ms`}    color={avgMs > 20 ? '#FFB800' : '#00D9FF'} />
              </div>
            </div>

            {/* Search */}
            <div className="px-3 py-2" style={{ borderBottom: '1px solid #0F2744' }}>
              <div className="flex items-center gap-1.5 px-2 py-1 rounded"
                style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
                <Search size={10} style={{ color: '#1E4060' }} />
                <input
                  value={search}
                  onChange={e => onSearchChange(e.target.value)}
                  placeholder="Search hosts..."
                  className="bg-transparent text-[10px] font-mono flex-1"
                  style={{ color: '#E0F4FF' }}
                />
              </div>
            </div>

            {/* Type filter chips */}
            <div className="flex items-center gap-1 px-3 pt-2 pb-1">
              {FILTERS.map(f => (
                <button
                  key={f.id}
                  onClick={() => onTypeFilterChange(f.id)}
                  className="flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[9px] font-bold transition-all"
                  style={{
                    background: typeFilter === f.id ? 'rgba(0,217,255,0.15)' : 'transparent',
                    border:     `1px solid ${typeFilter === f.id ? '#00D9FF55' : '#0F2744'}`,
                    color:      typeFilter === f.id ? '#00D9FF' : '#1E4060',
                  }}
                >
                  {f.icon}{f.label}
                </button>
              ))}
            </div>

            {/* Status filter chips */}
            <div className="flex items-center gap-1 px-3 pb-2" style={{ borderBottom: '1px solid #0F2744' }}>
              {STATUS_FILTERS.map(f => (
                <button
                  key={f.id}
                  onClick={() => onStatusFilterChange(f.id)}
                  className="px-1.5 py-0.5 rounded text-[9px] font-bold transition-all"
                  style={{
                    background: statusFilter === f.id ? `${f.color}22` : 'transparent',
                    border:     `1px solid ${statusFilter === f.id ? f.color + '66' : '#0F2744'}`,
                    color:      statusFilter === f.id ? f.color : '#1E4060',
                  }}
                >
                  {f.label}
                </button>
              ))}
            </div>

            {/* Add / Clear buttons */}
            <div className="flex gap-1.5 mx-3 my-2">
              <button
                onClick={() => setShowAddModal(true)}
                className="flex flex-1 items-center justify-center gap-1 py-1 rounded text-[9px] font-bold"
                style={{ background: 'rgba(0,217,255,0.08)', border: '1px dashed #00D9FF44', color: '#00D9FF', cursor: 'pointer', letterSpacing: '0.08em' }}
              >
                <Plus size={9} /> ADD
              </button>

              {confirmClear ? (
                <button
                  onClick={async () => { await clearDevices(); setConfirmClear(false) }}
                  className="flex flex-1 items-center justify-center gap-1 py-1 rounded text-[9px] font-bold"
                  style={{ background: 'rgba(255,45,85,0.15)', border: '1px solid #FF2D55', color: '#FF2D55', cursor: 'pointer', letterSpacing: '0.08em' }}
                  onBlur={() => setConfirmClear(false)}
                  autoFocus
                >
                  CONFIRM
                </button>
              ) : (
                <button
                  onClick={() => setConfirmClear(true)}
                  className="flex items-center justify-center px-2 py-1 rounded"
                  style={{ background: 'transparent', border: '1px solid #1E4060', color: '#1E4060', cursor: 'pointer' }}
                  title="Clear all devices"
                >
                  <Trash2 size={9} />
                </button>
              )}
            </div>

            {/* Showing count when filtered */}
            {filteredDevices.length !== devices.length && (
              <div className="px-3 pb-1 text-[9px]" style={{ color: '#4A7FA5' }}>
                Showing {filteredDevices.length} of {devices.length}
              </div>
            )}

            {/* Device list */}
            <div className="flex-1 overflow-hidden min-h-0 px-3 pt-2">
              <DeviceList
                devices={filteredDevices}
                selectedDeviceId={selectedDeviceId}
                onSelectDevice={onSelectDevice}
              />
            </div>

            {showAddModal && (
              <AddDeviceModal
                devices={devices}
                onAdd={async (data) => { await addDevice(data); setShowAddModal(false) }}
                onClose={() => setShowAddModal(false)}
              />
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

function StatChip({ label, value, color }) {
  return (
    <div className="flex flex-col items-center justify-center rounded py-1"
      style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
      <span className="text-[11px] font-bold" style={{ color, textShadow: `0 0 6px ${color}44` }}>
        {value}
      </span>
      <span className="text-[8px]" style={{ color: '#1E4060' }}>{label}</span>
    </div>
  )
}
