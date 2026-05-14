import { useRef } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { statusColor } from '../utils/colors'
import { getLatestLatency } from '../utils/graph'
import ThreatBadge from './ThreatBadge'
import BandwidthBar from './BandwidthBar'

const TYPE_ORDER = { ROUTER: 0, HOST: 1, MOBILE: 2 }
const ITEM_HEIGHT = 68  // 62px card + 6px gap

function sortDevices(devices) {
  return [...devices].sort((a, b) => {
    const to = (TYPE_ORDER[a.type] ?? 9) - (TYPE_ORDER[b.type] ?? 9)
    if (to !== 0) return to
    return a.hostname.localeCompare(b.hostname)
  })
}

export default function DeviceList({ devices, selectedDeviceId, onSelectDevice }) {
  const parentRef = useRef(null)
  const sorted    = sortDevices(devices)

  const virtualizer = useVirtualizer({
    count:           sorted.length,
    getScrollElement: () => parentRef.current,
    estimateSize:    () => ITEM_HEIGHT,
    overscan:        5,
  })

  if (devices.length === 0) {
    return (
      <div className="text-[10px] text-center py-6" style={{ color: '#1E4060' }}>
        No devices match filter
      </div>
    )
  }

  return (
    <div ref={parentRef} style={{ height: '100%', overflow: 'auto' }}>
      <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
        {virtualizer.getVirtualItems().map(vItem => {
          const device = sorted[vItem.index]
          return (
            <div
              key={vItem.key}
              style={{
                position:  'absolute',
                top:       0,
                left:      0,
                width:     '100%',
                height:    vItem.size,
                transform: `translateY(${vItem.start}px)`,
                paddingBottom: 6,
              }}
            >
              <DeviceCard
                device={device}
                selected={device.id === selectedDeviceId}
                onSelect={() => onSelectDevice(device.id)}
              />
            </div>
          )
        })}
      </div>
    </div>
  )
}

function DeviceCard({ device, selected, onSelect }) {
  const latency   = getLatestLatency(device)
  const sColor    = statusColor(device.status)
  const isOffline = device.status === 'OFFLINE'

  return (
    <div
      onClick={onSelect}
      className="cursor-pointer rounded p-2"
      style={{
        height:      62,
        boxSizing:   'border-box',
        display:     'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        background:  selected ? '#0B1F3A' : '#0A1628',
        border:      `1px solid ${selected ? '#00D9FF' : '#0F2744'}`,
        boxShadow:   selected ? '0 0 8px rgba(0,217,255,0.25)' : 'none',
        transition:  'border-color 0.15s, background 0.15s',
      }}
    >
      <div className="flex items-center gap-1.5">
        <span className="rounded-full shrink-0"
          style={{ width: 6, height: 6, background: sColor, boxShadow: `0 0 5px ${sColor}` }} />
        <span className="text-[11px] font-medium truncate flex-1"
          style={{ color: selected ? '#E0F4FF' : '#B0D0E8' }}>
          {device.hostname}
        </span>
        {device.status === 'NEW' && (
          <span className="text-[8px] font-bold px-1 rounded-sm"
            style={{ background: 'rgba(255,184,0,0.15)', color: '#FFB800', border: '1px solid #FFB80066' }}>
            NEW
          </span>
        )}
        <ThreatBadge level={device.threatLevel} reason={device.threatReason} />
      </div>

      <div className="flex items-center gap-1">
        <span className="text-[10px]" style={{ color: '#4A7FA5' }}>{device.ip}</span>
        <span style={{ color: '#1E4060' }}>·</span>
        {isOffline
          ? <span className="text-[10px]" style={{ color: '#FF2D55' }}>offline</span>
          : <span className="text-[10px]" style={{ color: latency && latency > 20 ? '#FFB800' : '#4A7FA5' }}>
              {latency != null ? `${latency}ms` : '—'}
            </span>
        }
        <div className="ml-auto">
          <BandwidthBar bandwidth={device.bandwidth} />
        </div>
      </div>

      <div className="text-[9px] truncate" style={{ color: '#1E4060' }}>{device.vendor}</div>
    </div>
  )
}
