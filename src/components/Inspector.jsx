import { useState, useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import { Trash2, Pencil, ShieldAlert, RefreshCw } from 'lucide-react'
import { BarChart, Bar, Cell } from 'recharts'
import { nodeColor, statusColor } from '../utils/colors'
import { getAverageLatency, getMinLatency, getMaxLatency } from '../utils/graph'
import LatencyChart from './LatencyChart'
import ThreatBadge from './ThreatBadge'

function FieldRow({ label, value, valueColor }) {
  return (
    <div className="flex justify-between items-center py-1.5"
      style={{ borderBottom: '1px solid #0A1628' }}>
      <span className="text-[10px]" style={{ color: '#1E4060' }}>{label}</span>
      <span className="text-[10px] font-mono" style={{ color: valueColor || '#B0D0E8' }}>{value}</span>
    </div>
  )
}

function BandwidthChart({ history }) {
  if (!history || history.length === 0) return (
    <div className="text-[10px]" style={{ color: '#1E4060' }}>No data yet — traffic will appear here</div>
  )
  const data = history.map((value, index) => ({ index, value }))
  const max  = Math.max(...history, 1)
  const cur  = history[history.length - 1]
  const min  = Math.min(...history)

  return (
    <>
      <div style={{ filter: 'drop-shadow(0 0 3px rgba(0,255,156,0.25))' }}>
        <BarChart width={200} height={44} data={data} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
          <Bar dataKey="value" radius={[2, 2, 0, 0]} barSize={8} isAnimationActive={false}>
            {data.map((entry, i) => (
              <Cell key={i}
                fill={entry.value > 5000 ? '#FF2D55' : entry.value > 500 ? '#FFB800' : '#00FF9C'} />
            ))}
          </Bar>
        </BarChart>
      </div>
      <div className="text-[9px] mt-1 flex gap-2" style={{ color: '#4A7FA5' }}>
        <span>cur <span style={{ color: cur > 5000 ? '#FF2D55' : '#00FF9C' }}>{cur} Kbps</span></span>
        <span>min <span style={{ color: '#00FF9C' }}>{min}</span></span>
        <span>max <span style={{ color: max > 5000 ? '#FF2D55' : '#4A7FA5' }}>{max}</span></span>
      </div>
    </>
  )
}

const RISK_COLOR = {
  CRITICAL: '#FF2D55',
  HIGH:     '#FF6B2D',
  MEDIUM:   '#FFB800',
  LOW:      '#C8FF00',
  CLEAN:    '#00FF9C',
}

function RiskBadge({ level }) {
  if (!level) return null
  const color = RISK_COLOR[level] || '#4A7FA5'
  return (
    <span className="text-[8px] font-bold px-1.5 py-0.5 rounded-sm ml-1"
      style={{ background: color + '22', border: `1px solid ${color}66`, color }}>
      {level}
    </span>
  )
}

function SecuritySection({ device, onRescan }) {
  const scan = device?.securityScan
  const [scanning, setScanning] = useState(false)

  const handleRescan = async () => {
    setScanning(true)
    try {
      await fetch(`${import.meta.env.VITE_API_URL}/api/devices/${device.id}/security-scan`, {
        method: 'POST',
      })
    } catch { /* ignore — WS will update when done */ }
    setTimeout(() => setScanning(false), 3000)
  }

  return (
    <div className="mt-3 px-1">
      <div className="flex items-center justify-between mb-2">
        <div className="text-[9px] font-bold tracking-widest flex items-center gap-1"
          style={{ color: '#1E4060' }}>
          <ShieldAlert size={9} /> SECURITY SCAN
          {scan && <RiskBadge level={scan.riskLevel} />}
        </div>
        <button
          onClick={handleRescan}
          disabled={scanning}
          className="flex items-center gap-0.5 text-[8px] font-bold px-1.5 py-0.5 rounded"
          style={{ background: 'rgba(0,217,255,0.08)', border: '1px solid #00D9FF33',
                   color: '#00D9FF', cursor: scanning ? 'wait' : 'pointer' }}
        >
          <RefreshCw size={8} className={scanning ? 'animate-spin' : ''} />
          {scanning ? 'SCANNING…' : 'SCAN NOW'}
        </button>
      </div>

      {!scan ? (
        <div className="text-[10px]" style={{ color: '#1E4060' }}>
          Scan pending — will run automatically
        </div>
      ) : (
        <>
          <div className="text-[8px] mb-2" style={{ color: '#1E4060' }}>
            Last scanned {scan.timestamp}
          </div>

          {/* Open ports */}
          {scan.openPorts?.length > 0 && (
            <div className="mb-2">
              <div className="text-[8px] font-bold mb-1" style={{ color: '#4A7FA5' }}>
                OPEN PORTS ({scan.openPorts.length})
              </div>
              <div className="flex flex-col gap-0.5">
                {scan.openPorts.map(p => (
                  <div key={p.port} className="flex items-baseline gap-1 text-[9px]">
                    <span className="font-mono w-8 shrink-0" style={{ color: '#00D9FF' }}>{p.port}</span>
                    <span style={{ color: '#B0D0E8' }}>{p.service}</span>
                    {p.version && (
                      <span className="text-[8px] truncate" style={{ color: '#4A7FA5' }}>{p.version}</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* CVE matches */}
          {scan.cveMatches?.length > 0 && (
            <div className="mb-2">
              <div className="text-[8px] font-bold mb-1" style={{ color: '#4A7FA5' }}>
                CVEs ({scan.cveMatches.length})
              </div>
              <div className="flex flex-col gap-1">
                {scan.cveMatches.slice(0, 5).map(c => (
                  <div key={c.cveId} className="rounded p-1"
                    style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
                    <div className="flex items-center gap-1 mb-0.5">
                      <span className="text-[8px] font-bold font-mono" style={{ color: '#00D9FF' }}>
                        {c.cveId}
                      </span>
                      <span className="text-[7px] font-bold px-1 rounded-sm"
                        style={{
                          background: (RISK_COLOR[c.severity] || '#4A7FA5') + '22',
                          color: RISK_COLOR[c.severity] || '#4A7FA5',
                          border: `1px solid ${(RISK_COLOR[c.severity] || '#4A7FA5')}44`,
                        }}>
                        {c.severity} {c.cvssScore.toFixed(1)}
                      </span>
                      <span className="text-[7px] ml-auto" style={{ color: '#4A7FA5' }}>
                        {c.affectedPort}
                      </span>
                    </div>
                    <div className="text-[8px]" style={{ color: '#4A7FA5' }}>{c.description}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Credential findings */}
          {scan.credentialFindings?.filter(f => f.vulnerable).length > 0 && (
            <div className="mb-2">
              <div className="text-[8px] font-bold mb-1" style={{ color: '#FF2D55' }}>
                CREDENTIAL VULNERABILITIES
              </div>
              <div className="flex flex-col gap-0.5">
                {scan.credentialFindings.filter(f => f.vulnerable).map((f, i) => (
                  <div key={i} className="rounded p-1"
                    style={{ background: 'rgba(255,45,85,0.06)', border: '1px solid rgba(255,45,85,0.25)' }}>
                    <div className="text-[8px] font-bold" style={{ color: '#FF2D55' }}>
                      {f.service} :{f.port}
                      {f.username && <span style={{ color: '#FFB800' }}> {f.username}/{f.password}</span>}
                    </div>
                    <div className="text-[8px]" style={{ color: '#4A7FA5' }}>{f.note}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ARP anomalies */}
          {scan.arpAnomalies?.length > 0 && (
            <div className="mb-2">
              <div className="text-[8px] font-bold mb-1" style={{ color: '#FF6B2D' }}>
                ARP ANOMALIES
              </div>
              {scan.arpAnomalies.map((a, i) => (
                <div key={i} className="text-[8px] py-0.5" style={{ color: '#FF6B2D' }}>{a}</div>
              ))}
            </div>
          )}

          {scan.openPorts?.length === 0 && scan.cveMatches?.length === 0 &&
           scan.credentialFindings?.length === 0 && scan.arpAnomalies?.length === 0 && (
            <div className="text-[10px]" style={{ color: '#00FF9C' }}>No vulnerabilities found</div>
          )}
        </>
      )}
    </div>
  )
}

export default function Inspector({ device, onRemove }) {
  const [flash,       setFlash]       = useState(false)
  const [confirming,  setConfirming]  = useState(false)
  const [removing,    setRemoving]    = useState(false)
  const [editingName, setEditingName] = useState(false)
  const [nameValue,   setNameValue]   = useState('')
  const inputRef = useRef(null)

  useEffect(() => {
    if (!device) return
    setFlash(true)
    setConfirming(false)
    setEditingName(false)
    setNameValue(device.hostname)
    const t = setTimeout(() => setFlash(false), 300)
    return () => clearTimeout(t)
  }, [device?.id])

  useEffect(() => {
    if (editingName) inputRef.current?.focus()
  }, [editingName])

  if (!device) {
    return (
      <div className="flex flex-col items-center justify-center h-32 text-center px-4">
        <div className="text-[24px] mb-2" style={{ color: '#1E4060' }}>⌖</div>
        <div className="text-[11px]" style={{ color: '#1E4060' }}>
          Select a device on the canvas or from the list
        </div>
      </div>
    )
  }

  const color    = nodeColor(device)
  const sColor   = statusColor(device.status)
  const avg      = getAverageLatency(device)
  const min      = getMinLatency(device)
  const max      = getMaxLatency(device)
  const isRouter = device.type === 'ROUTER'
  const statusLabel = { ONLINE: 'Online', OFFLINE: 'Offline', NEW: 'New' }[device.status] || device.status

  const saveName = async () => {
    setEditingName(false)
    const trimmed = nameValue.trim()
    if (!trimmed || trimmed === device.hostname) { setNameValue(device.hostname); return }
    try {
      await fetch(`${import.meta.env.VITE_API_URL}/api/devices/${device.id}/rename`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ hostname: trimmed }),
      })
    } catch { setNameValue(device.hostname) }
  }

  return (
    <motion.div
      animate={{ backgroundColor: flash ? 'rgba(0,217,255,0.04)' : 'transparent' }}
      transition={{ duration: 0.3 }}
      className="flex flex-col gap-0"
    >
      {/* Header */}
      <div className="pb-2 mb-1" style={{ borderBottom: '1px solid #0F2744' }}>
        <div className="flex items-center gap-1.5">
          {editingName ? (
            <input
              ref={inputRef}
              value={nameValue}
              onChange={e => setNameValue(e.target.value)}
              onBlur={saveName}
              onKeyDown={e => {
                if (e.key === 'Enter') saveName()
                if (e.key === 'Escape') { setEditingName(false); setNameValue(device.hostname) }
              }}
              className="text-[13px] font-bold font-mono flex-1 bg-transparent outline-none border-b"
              style={{ color, borderColor: color + '88', minWidth: 0 }}
            />
          ) : (
            <span
              className="text-[13px] font-bold cursor-text"
              style={{ color, textShadow: `0 0 8px ${color}55` }}
              onClick={() => setEditingName(true)}
              title="Click to rename"
            >
              {device.hostname}
            </span>
          )}
          <Pencil
            size={10}
            onClick={() => setEditingName(true)}
            style={{ color: '#1E4060', cursor: 'pointer', flexShrink: 0 }}
          />
          <ThreatBadge level={device.threatLevel} reason={device.threatReason} />
        </div>
        <div className="text-[9px] mt-0.5" style={{ color: '#1E4060' }}>
          {device.type} · {device.vendor}
        </div>
      </div>

      {/* Fields */}
      <div className="px-1">
        <FieldRow label="IP Address"    value={device.ip}  valueColor="#00D9FF" />
        <FieldRow label="MAC"           value={device.mac} />
        <FieldRow label="Vendor"        value={device.vendor} />
        <FieldRow label="Discovered by" value={isRouter ? 'ARP + SNMP' : 'ARP'} />
        <FieldRow label="First seen"    value={device.firstSeen || '—'} />
        <FieldRow label="Status"        value={statusLabel} valueColor={sColor} />
        {device.threatLevel && device.threatLevel !== 'none' && device.threatReason && (
          <FieldRow
            label="Anomaly"
            value={device.threatReason}
            valueColor={device.threatLevel === 'high' ? '#FF2D55' : '#FFB800'}
          />
        )}
      </div>

      {/* Latency section */}
      <div className="mt-2 px-1">
        <div className="text-[9px] font-bold tracking-widest mb-2" style={{ color: '#1E4060' }}>
          LATENCY — LAST 10 PROBES
        </div>
        {device.latencies && device.latencies.length > 0 ? (
          <>
            <LatencyChart latencies={device.latencies} />
            <div className="text-[9px] mt-1 flex gap-2" style={{ color: '#4A7FA5' }}>
              <span>avg <span style={{ color: avg > 20 ? '#FFB800' : '#00D9FF' }}>{avg}ms</span></span>
              <span>min <span style={{ color: '#00FF9C' }}>{min}ms</span></span>
              <span>max <span style={{ color: max > 20 ? '#FFB800' : '#4A7FA5' }}>{max}ms</span></span>
            </div>
          </>
        ) : (
          <div className="text-[10px]" style={{ color: '#1E4060' }}>No data — device offline</div>
        )}
      </div>

      {/* Bandwidth history */}
      <div className="mt-3 px-1">
        <div className="text-[9px] font-bold tracking-widest mb-2" style={{ color: '#1E4060' }}>
          BANDWIDTH — LAST 20 READINGS (Kbps)
        </div>
        <BandwidthChart history={device.bandwidthHistory} />
      </div>

      {/* Security scan */}
      <SecuritySection device={device} />

      {/* Remove device */}
      {onRemove && (
        <div className="mt-4 px-1">
          {!confirming ? (
            <button
              onClick={() => setConfirming(true)}
              className="flex items-center justify-center gap-1 w-full py-1.5 rounded text-[9px] font-bold transition-all"
              style={{
                background: 'rgba(255,45,85,0.06)', border: '1px solid rgba(255,45,85,0.25)',
                color: '#FF2D55', cursor: 'pointer', letterSpacing: '0.08em',
              }}
            >
              <Trash2 size={9} /> REMOVE DEVICE
            </button>
          ) : (
            <div className="flex flex-col gap-1.5">
              <div className="text-[9px] text-center" style={{ color: '#FF2D55' }}>
                Remove <strong>{device.hostname}</strong>? This also deletes its links.
              </div>
              <div className="flex gap-1.5">
                <button onClick={() => setConfirming(false)}
                  className="flex-1 py-1 rounded text-[9px] font-bold"
                  style={{ background: 'transparent', border: '1px solid #0F2744', color: '#4A7FA5', cursor: 'pointer' }}>
                  CANCEL
                </button>
                <button
                  disabled={removing}
                  onClick={async () => {
                    setRemoving(true)
                    try { await onRemove(device.id) } catch { setRemoving(false); setConfirming(false) }
                  }}
                  className="flex-1 py-1 rounded text-[9px] font-bold"
                  style={{ background: 'rgba(255,45,85,0.15)', border: '1px solid rgba(255,45,85,0.4)', color: '#FF2D55', cursor: removing ? 'wait' : 'pointer' }}>
                  {removing ? 'REMOVING…' : 'CONFIRM'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </motion.div>
  )
}
