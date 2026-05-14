import { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X, Plus } from 'lucide-react'

const FIELD_STYLE = {
  background: '#0A1628',
  border: '1px solid #0F2744',
  color: '#E0F4FF',
  borderRadius: 4,
  padding: '5px 8px',
  fontSize: 11,
  fontFamily: 'monospace',
  width: '100%',
  outline: 'none',
}
const SELECT_STYLE = { ...FIELD_STYLE, cursor: 'pointer' }
const LABEL_STYLE  = { fontSize: 9, fontWeight: 'bold', letterSpacing: '0.08em', color: '#1E4060', marginBottom: 3, display: 'block' }

export default function AddDeviceModal({ devices, onAdd, onClose }) {
  const [form, setForm] = useState({
    hostname: '', ip: '', mac: '', vendor: '',
    type: 'HOST', isHotspot: false,
    gatewayId: '', bandwidth: 0,
    subnet: '', snmpCommunity: '',
  })
  const [error,   setError]   = useState('')
  const [loading, setLoading] = useState(false)

  const gateways   = devices.filter(d => d.type === 'ROUTER' || d.isGateway || d.gateway)
  const isGateway  = form.type === 'ROUTER' || (form.type === 'MOBILE' && form.isHotspot)

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.hostname.trim() || !form.ip.trim() || !form.mac.trim()) {
      setError('Hostname, IP, and MAC are required.')
      return
    }
    setError('')
    setLoading(true)
    try {
      await onAdd({
        hostname:      form.hostname.trim(),
        ip:            form.ip.trim(),
        mac:           form.mac.trim(),
        vendor:        form.vendor.trim() || 'Unknown',
        type:          form.type,
        isGateway,
        gatewayId:     form.gatewayId  || null,
        bandwidth:     Number(form.bandwidth) || 0,
        subnet:        form.subnet.trim()        || null,
        snmpCommunity: form.snmpCommunity.trim() || null,
      })
    } catch (err) {
      setError(err.message || 'Failed to add device.')
      setLoading(false)
    }
  }

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
        style={{ position: 'fixed', inset: 0, zIndex: 100, background: 'rgba(5,13,26,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
        onClick={onClose}
      >
        <motion.div
          initial={{ opacity: 0, y: 24 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: 24 }}
          transition={{ type: 'spring', stiffness: 340, damping: 28 }}
          style={{ background: '#060E1C', border: '1px solid #0F2744', borderRadius: 8, padding: 20, width: 320, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 0 40px rgba(0,217,255,0.08)' }}
          onClick={e => e.stopPropagation()}
        >
          {/* Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <span style={{ fontSize: 11, fontWeight: 'bold', color: '#00D9FF', letterSpacing: '0.1em' }}>+ ADD DEVICE</span>
            <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#1E4060', cursor: 'pointer', padding: 2 }}><X size={14} /></button>
          </div>

          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>

            {/* Basic fields */}
            <Field label="HOSTNAME *">
              <input style={FIELD_STYLE} value={form.hostname} onChange={e => set('hostname', e.target.value)} placeholder="e.g. Galaxy-S24" />
            </Field>

            <Field label="IP ADDRESS *">
              <input style={FIELD_STYLE} value={form.ip} onChange={e => set('ip', e.target.value)} placeholder="192.168.43.1" />
            </Field>

            <Field label="MAC ADDRESS *">
              <input style={FIELD_STYLE} value={form.mac} onChange={e => set('mac', e.target.value)} placeholder="AA:BB:CC:DD:EE:FF" />
            </Field>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              <Field label="TYPE">
                <select style={SELECT_STYLE} value={form.type} onChange={e => set('type', e.target.value)}>
                  <option value="HOST">HOST</option>
                  <option value="ROUTER">ROUTER</option>
                  <option value="MOBILE">MOBILE</option>
                </select>
              </Field>
              <Field label="BANDWIDTH (Mbps)">
                <input style={FIELD_STYLE} type="number" min={0} value={form.bandwidth} onChange={e => set('bandwidth', e.target.value)} placeholder="auto" />
              </Field>
            </div>

            <Field label="VENDOR">
              <input style={FIELD_STYLE} value={form.vendor} onChange={e => set('vendor', e.target.value)} placeholder="e.g. Samsung" />
            </Field>

            {/* Hotspot toggle — only shown for MOBILE */}
            {form.type === 'MOBILE' && (
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', padding: '6px 8px', borderRadius: 4, background: form.isHotspot ? 'rgba(0,217,255,0.08)' : 'transparent', border: `1px solid ${form.isHotspot ? '#00D9FF44' : '#0F2744'}` }}>
                <input
                  type="checkbox"
                  checked={form.isHotspot}
                  onChange={e => set('isHotspot', e.target.checked)}
                  style={{ accentColor: '#00D9FF' }}
                />
                <span style={{ fontSize: 10, color: form.isHotspot ? '#00D9FF' : '#4A7FA5', fontWeight: 'bold', letterSpacing: '0.06em' }}>
                  This phone is sharing a hotspot
                </span>
              </label>
            )}

            {/* Gateway fields — shown when this device IS a gateway */}
            {isGateway && (
              <>
                <div style={{ borderTop: '1px solid #0F2744', paddingTop: 8, marginTop: 2 }}>
                  <div style={{ fontSize: 9, fontWeight: 'bold', color: '#00D9FF', letterSpacing: '0.1em', marginBottom: 8 }}>
                    GATEWAY / DISCOVERY SETTINGS
                  </div>

                  <div style={{ fontSize: 9, color: '#4A7FA5', marginBottom: 8, lineHeight: 1.5 }}>
                    The backend will scan this subnet to auto-discover connected devices.
                    Your laptop must be connected to this same network for scanning to work.
                  </div>

                  <Field label="SUBNET (optional — auto-inferred from IP if blank)">
                    <input style={FIELD_STYLE} value={form.subnet} onChange={e => set('subnet', e.target.value)}
                      placeholder={form.ip ? inferSubnet(form.ip) : '192.168.43.0/24'} />
                  </Field>

                  <div style={{ marginTop: 8 }}>
                    <Field label="SNMP COMMUNITY (optional — enables real bandwidth data)">
                      <input style={FIELD_STYLE} value={form.snmpCommunity} onChange={e => set('snmpCommunity', e.target.value)}
                        placeholder='e.g. "public" (check router admin panel)' />
                    </Field>
                  </div>
                </div>
              </>
            )}

            {/* Connect to another gateway */}
            <Field label="CONNECT TO GATEWAY">
              <select style={SELECT_STYLE} value={form.gatewayId} onChange={e => set('gatewayId', e.target.value)}>
                <option value="">— None (standalone) —</option>
                {gateways.map(d => (
                  <option key={d.id} value={d.id}>{d.hostname} ({d.ip})</option>
                ))}
              </select>
            </Field>

            {error && (
              <div style={{ fontSize: 10, color: '#FF2D55', padding: '4px 8px', background: 'rgba(255,45,85,0.1)', borderRadius: 4, border: '1px solid rgba(255,45,85,0.3)' }}>
                {error}
              </div>
            )}

            <div style={{ display: 'flex', gap: 8, marginTop: 4 }}>
              <button type="button" onClick={onClose}
                style={{ flex: 1, padding: '6px 0', fontSize: 10, fontWeight: 'bold', fontFamily: 'monospace', cursor: 'pointer', background: 'transparent', border: '1px solid #0F2744', color: '#4A7FA5', borderRadius: 4 }}>
                CANCEL
              </button>
              <button type="submit" disabled={loading}
                style={{ flex: 1, padding: '6px 0', fontSize: 10, fontWeight: 'bold', fontFamily: 'monospace', cursor: loading ? 'wait' : 'pointer', background: loading ? 'rgba(0,217,255,0.05)' : 'rgba(0,217,255,0.12)', border: '1px solid #00D9FF55', color: '#00D9FF', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
                <Plus size={10} />{loading ? 'ADDING...' : 'ADD'}
              </button>
            </div>
          </form>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  )
}

function Field({ label, children }) {
  return (
    <div>
      <label style={LABEL_STYLE}>{label}</label>
      {children}
    </div>
  )
}

function inferSubnet(ip) {
  const parts = ip.split('.')
  if (parts.length < 3) return '192.168.43.0/24'
  return `${parts[0]}.${parts[1]}.${parts[2]}.0/24`
}
