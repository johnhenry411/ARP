import { useState, useEffect, useCallback } from 'react'
import { Zap, ZapOff, Plus, Trash2, ToggleLeft, ToggleRight, Download, Shield } from 'lucide-react'

const API = import.meta.env.VITE_API_URL

function Row({ label, children }) {
  return (
    <div className="flex items-center justify-between py-1.5" style={{ borderBottom: '1px solid #0A1628' }}>
      <span className="text-[10px]" style={{ color: '#1E4060' }}>{label}</span>
      <span className="text-[10px] font-mono" style={{ color: '#B0D0E8' }}>{children}</span>
    </div>
  )
}

export default function DnsPanel({ prefilledDomain, prefilledIp, onConsumePreset }) {
  const [status,     setStatus]     = useState(null)
  const [rules,      setRules]      = useState([])
  const [starting,   setStarting]   = useState(false)
  const [showAdd,    setShowAdd]    = useState(false)
  const [newDomain,  setNewDomain]  = useState(prefilledDomain || '')
  const [newIp,      setNewIp]      = useState(prefilledIp || '')
  const [newTitle,   setNewTitle]   = useState('')
  const [newBody,    setNewBody]    = useState('')
  const [selectedIf, setSelectedIf] = useState('')

  const refresh = useCallback(async () => {
    try {
      const [s, r] = await Promise.all([
        fetch(`${API}/api/dns/status`).then(x => x.json()),
        fetch(`${API}/api/dns/rules`).then(x => x.json()),
      ])
      setStatus(s)
      setRules(r)
      if (!selectedIf && s.interfaces?.length) setSelectedIf(s.interfaces[0].ip)
    } catch { /* backend may not be ready */ }
  }, [selectedIf])

  useEffect(() => { refresh() }, [])

  // Accept preset from canvas right-click
  useEffect(() => {
    if (prefilledDomain) { setNewDomain(prefilledDomain); setShowAdd(true) }
    if (prefilledIp)     setNewIp(prefilledIp)
  }, [prefilledDomain, prefilledIp])

  const toggleServer = async () => {
    setStarting(true)
    try {
      const running = status?.dnsRunning
      if (running) {
        await fetch(`${API}/api/dns/stop`, { method: 'POST' })
      } else {
        await fetch(`${API}/api/dns/start`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ip: selectedIf || status?.interfaces?.[0]?.ip }),
        })
      }
      await refresh()
    } finally { setStarting(false) }
  }

  const addRule = async () => {
    if (!newDomain.trim()) return
    const body = {
      domain:    newDomain.trim(),
      targetIp:  newIp.trim() || selectedIf || '',
      pageTitle: newTitle.trim() || `${newDomain} — Redirected`,
      pageBody:  newBody.trim() || `You tried to reach <strong>${newDomain}</strong>, but this request was intercepted.`,
    }
    await fetch(`${API}/api/dns/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    setNewDomain(''); setNewIp(''); setNewTitle(''); setNewBody('')
    setShowAdd(false)
    if (onConsumePreset) onConsumePreset()
    await refresh()
  }

  const deleteRule = async (id) => {
    await fetch(`${API}/api/dns/rules/${id}`, { method: 'DELETE' })
    await refresh()
  }

  const toggleRule = async (id) => {
    await fetch(`${API}/api/dns/rules/${id}/toggle`, { method: 'PATCH' })
    await refresh()
  }

  const downloadCa = () => {
    window.open(`${API}/api/dns/ca.crt`, '_blank')
  }

  const running = status?.dnsRunning

  return (
    <div className="flex flex-col gap-3">

      {/* Server status */}
      <div className="rounded p-2" style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
        <div className="text-[9px] font-bold tracking-widest mb-2" style={{ color: '#1E4060' }}>
          DNS SPOOFER
        </div>
        <div className="flex items-center gap-2 mb-2">
          <span className="rounded-full w-2 h-2 shrink-0"
            style={{ background: running ? '#00FF9C' : '#FF2D55',
                     boxShadow: running ? '0 0 6px #00FF9C' : 'none' }} />
          <span className="text-[10px]" style={{ color: running ? '#00FF9C' : '#FF2D55' }}>
            {running ? `Running on ${status?.boundIp}` : 'Stopped'}
          </span>
        </div>

        {/* Interface selector */}
        {!running && status?.interfaces?.length > 0 && (
          <select
            value={selectedIf}
            onChange={e => setSelectedIf(e.target.value)}
            className="w-full text-[9px] rounded px-2 py-1 mb-2 font-mono"
            style={{ background: '#050D1A', border: '1px solid #0F2744', color: '#B0D0E8' }}
          >
            {status.interfaces.map(iface => (
              <option key={iface.ip} value={iface.ip}>
                {iface.ip} — {iface.name}
              </option>
            ))}
          </select>
        )}

        <button
          onClick={toggleServer}
          disabled={starting}
          className="flex items-center justify-center gap-1 w-full py-1.5 rounded text-[9px] font-bold"
          style={{
            background: running ? 'rgba(255,45,85,0.1)' : 'rgba(0,255,156,0.1)',
            border:     `1px solid ${running ? 'rgba(255,45,85,0.4)' : 'rgba(0,255,156,0.4)'}`,
            color:      running ? '#FF2D55' : '#00FF9C',
            cursor:     starting ? 'wait' : 'pointer',
          }}
        >
          {running ? <ZapOff size={9} /> : <Zap size={9} />}
          {starting ? 'STARTING…' : running ? 'STOP SERVER' : 'START SERVER'}
        </button>

        {/* Admin warning */}
        {!running && (
          <div className="text-[8px] mt-1.5 text-center" style={{ color: '#1E4060' }}>
            Requires Administrator — run terminal as Admin
          </div>
        )}
      </div>

      {/* CA cert download */}
      <div className="rounded p-2" style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
        <div className="text-[9px] font-bold tracking-widest mb-2 flex items-center gap-1"
          style={{ color: '#1E4060' }}>
          <Shield size={9} /> CA CERTIFICATE
        </div>
        <div className="text-[8px] mb-2" style={{ color: '#4A7FA5' }}>
          Install on your phone once — all redirected HTTPS sites will work without warnings.
        </div>
        <button
          onClick={downloadCa}
          className="flex items-center justify-center gap-1 w-full py-1.5 rounded text-[9px] font-bold"
          style={{ background: 'rgba(0,217,255,0.08)', border: '1px dashed #00D9FF44',
                   color: '#00D9FF', cursor: 'pointer' }}
        >
          <Download size={9} /> DOWNLOAD ca.crt
        </button>
        <div className="text-[8px] mt-1.5" style={{ color: '#1E4060' }}>
          Android: Settings → Security → Install from storage<br/>
          iOS: Safari → download → Settings → Profile → Install → Trust
        </div>
      </div>

      {/* Rules */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <div className="text-[9px] font-bold tracking-widest flex items-center gap-1"
            style={{ color: '#1E4060' }}>
            <Zap size={9} /> DNS RULES ({rules.length})
          </div>
          <button
            onClick={() => setShowAdd(v => !v)}
            className="flex items-center gap-0.5 text-[8px] font-bold px-1.5 py-0.5 rounded"
            style={{ background: 'rgba(0,217,255,0.08)', border: '1px dashed #00D9FF44',
                     color: '#00D9FF', cursor: 'pointer' }}
          >
            <Plus size={8} /> ADD RULE
          </button>
        </div>

        {/* Add rule form */}
        {showAdd && (
          <div className="rounded p-2 mb-2" style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
            <div className="text-[9px] font-bold mb-2" style={{ color: '#4A7FA5' }}>NEW RULE</div>
            {[
              { label: 'Domain', value: newDomain, set: setNewDomain, placeholder: 'henry.com  or  *  or  *.henry.com' },
              { label: 'Redirect IP', value: newIp,     set: setNewIp,     placeholder: `${selectedIf || '192.168.x.x'} (leave blank = auto)` },
              { label: 'Page title', value: newTitle,   set: setNewTitle,   placeholder: 'Redirected!' },
              { label: 'Page body',  value: newBody,    set: setNewBody,    placeholder: 'You have been redirected.' },
            ].map(f => (
              <div key={f.label} className="mb-1.5">
                <div className="text-[8px] mb-0.5" style={{ color: '#1E4060' }}>{f.label}</div>
                <input
                  value={f.value}
                  onChange={e => f.set(e.target.value)}
                  placeholder={f.placeholder}
                  className="w-full text-[9px] font-mono rounded px-2 py-1"
                  style={{ background: '#050D1A', border: '1px solid #0F2744', color: '#E0F4FF' }}
                />
              </div>
            ))}
            <div className="flex gap-1.5 mt-2">
              <button onClick={() => setShowAdd(false)}
                className="flex-1 py-1 rounded text-[9px] font-bold"
                style={{ background: 'transparent', border: '1px solid #0F2744',
                         color: '#4A7FA5', cursor: 'pointer' }}>
                CANCEL
              </button>
              <button onClick={addRule}
                className="flex-1 py-1 rounded text-[9px] font-bold"
                style={{ background: 'rgba(0,217,255,0.12)', border: '1px solid #00D9FF44',
                         color: '#00D9FF', cursor: 'pointer' }}>
                ADD
              </button>
            </div>
          </div>
        )}

        {/* Rules list */}
        {rules.length === 0 ? (
          <div className="text-[10px] text-center py-4" style={{ color: '#1E4060' }}>
            No rules — add one above
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            {rules.map(rule => (
              <div key={rule.id} className="rounded p-1.5"
                style={{
                  background: rule.enabled ? 'rgba(0,217,255,0.04)' : 'transparent',
                  border:     `1px solid ${rule.enabled ? '#00D9FF22' : '#0F2744'}`,
                  opacity:    rule.enabled ? 1 : 0.5,
                }}>
                <div className="flex items-center gap-1">
                  <span className="text-[9px] font-bold font-mono flex-1 truncate"
                    style={{ color: rule.catchAll ? '#FFB800' : '#00D9FF' }}>
                    {rule.catchAll ? '* (catch-all)' : rule.domain}
                  </span>
                  <button onClick={() => toggleRule(rule.id)} style={{ cursor: 'pointer' }}>
                    {rule.enabled
                      ? <ToggleRight size={12} style={{ color: '#00FF9C' }} />
                      : <ToggleLeft  size={12} style={{ color: '#1E4060' }} />}
                  </button>
                  <button onClick={() => deleteRule(rule.id)} style={{ cursor: 'pointer' }}>
                    <Trash2 size={9} style={{ color: '#FF2D55' }} />
                  </button>
                </div>
                <div className="text-[8px] font-mono mt-0.5" style={{ color: '#4A7FA5' }}>
                  → {rule.targetIp}
                </div>
                {rule.pageTitle && (
                  <div className="text-[8px] mt-0.5 truncate" style={{ color: '#1E4060' }}>
                    "{rule.pageTitle}"
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Quick presets */}
      <div>
        <div className="text-[9px] font-bold tracking-widest mb-1.5" style={{ color: '#1E4060' }}>
          QUICK PRESETS
        </div>
        {[
          { label: 'Catch-all portal',      domain: '*',       title: 'Network Controlled',   body: 'All traffic on this network is monitored.' },
          { label: 'Block google.com',       domain: 'google.com', title: 'Blocked', body: 'google.com is blocked on this network.' },
          { label: 'Spoof example.com',      domain: 'example.com', title: 'Demo Redirect', body: 'example.com has been intercepted by Topology Mapper.' },
        ].map(preset => (
          <button key={preset.domain}
            onClick={() => {
              setNewDomain(preset.domain)
              setNewTitle(preset.title)
              setNewBody(preset.body)
              setNewIp(selectedIf || '')
              setShowAdd(true)
            }}
            className="w-full text-left text-[8px] px-2 py-1 rounded mb-1"
            style={{ background: 'transparent', border: '1px solid #0F2744',
                     color: '#4A7FA5', cursor: 'pointer' }}
          >
            {preset.label}
          </button>
        ))}
      </div>
    </div>
  )
}
