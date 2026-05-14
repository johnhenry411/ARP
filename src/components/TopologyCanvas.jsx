import { useRef, useEffect, useState, useCallback } from 'react'
import { Minimize2 } from 'lucide-react'
import { useCanvasTransform } from '../hooks/useCanvasTransform'
import { useParticles }       from '../hooks/useParticles'
import { COLORS, nodeColor }  from '../utils/colors'
import { computeInitialLayout } from '../utils/layout'
import { setGlow, clearGlow }   from '../utils/canvasGlow'

const ROUTER_R = 22
const NODE_R   = 15
const ICONS    = { ROUTER: '⌖', HOST: '☐', MOBILE: '✆' }

const RISK_COLORS = {
  CRITICAL: '#FF2D55',
  HIGH:     '#FF6B2D',
  MEDIUM:   '#FFB800',
  LOW:      '#C8FF00',
}

function getNodeRadius(device) {
  return device.type === 'ROUTER' ? ROUTER_R : NODE_R
}

function getNodeAtPoint(x, y, positions, devices) {
  for (const device of devices) {
    const pos = positions[device.id]
    if (!pos) continue
    const r  = getNodeRadius(device)
    const dx = pos.x - x
    const dy = pos.y - y
    if (Math.sqrt(dx*dx + dy*dy) <= r + 4) return device
  }
  return null
}

export default function TopologyCanvas({
  devices, links, selectedDeviceId, onSelectDevice,
  scanRunning, secondsUntilNextScan, isFullscreen, onExitFullscreen,
  onAddDnsRule,
}) {
  const containerRef = useRef(null)
  const bgRef        = useRef(null)
  const fgRef        = useRef(null)
  const positionsRef = useRef({})
  const sweepRef     = useRef(0)
  const heartRef     = useRef(0)
  const packetsRef   = useRef([])
  const rafRef       = useRef(null)
  const dragRef      = useRef(null)
  const panRef       = useRef(null)
  const onSelectRef  = useRef(onSelectDevice)
  const devicesRef   = useRef(devices)
  const linksRef     = useRef(links)
  const sizeRef      = useRef({ w: 0, h: 0 })

  const [tooltip,     setTooltip]     = useState({ visible: false, x: 0, y: 0, device: null })
  const [ctxMenu,     setCtxMenu]     = useState({ visible: false, x: 0, y: 0, device: null })
  const { transform, zoom, pan, screenToWorld } = useCanvasTransform()
  const transformRef = useRef(transform)
  const particles    = useParticles()

  // keep refs fresh
  useEffect(() => { onSelectRef.current  = onSelectDevice }, [onSelectDevice])
  useEffect(() => { devicesRef.current   = devices  }, [devices])
  useEffect(() => { linksRef.current     = links    }, [links])
  useEffect(() => { transformRef.current = transform }, [transform])

  // seed initial packets
  useEffect(() => {
    packetsRef.current = links
      .filter(l => {
        const src = devices.find(d => d.id === l.source)
        const tgt = devices.find(d => d.id === l.target)
        return src?.status !== 'OFFLINE' && tgt?.status !== 'OFFLINE'
      })
      .flatMap(l => [
        { linkId: l.id, t: Math.random(), speed: 0.003 + Math.random() * 0.003 },
        { linkId: l.id, t: Math.random(), speed: 0.003 + Math.random() * 0.004 },
      ])
  }, [links])

  // ResizeObserver — init layout + particles
  useEffect(() => {
    const container = containerRef.current
    if (!container) return
    const ro = new ResizeObserver(entries => {
      for (const entry of entries) {
        const { width: w, height: h } = entry.contentRect
        if (w === 0 || h === 0) continue
        sizeRef.current = { w, h }
        if (bgRef.current) { bgRef.current.width = w; bgRef.current.height = h }
        if (fgRef.current) { fgRef.current.width = w; fgRef.current.height = h }

        const hasPrev = Object.keys(positionsRef.current).length > 0
        if (!hasPrev) {
          positionsRef.current = computeInitialLayout(devicesRef.current, w, h)
          particles.init(w, h)
        } else {
          // scale positions to new size
          const prevSize = sizeRef.current
          const scaleX = w / (prevSize.w || w)
          const scaleY = h / (prevSize.h || h)
          const updated = {}
          for (const [id, pos] of Object.entries(positionsRef.current)) {
            updated[id] = { x: pos.x * scaleX, y: pos.y * scaleY }
          }
          positionsRef.current = updated
        }
      }
    })
    ro.observe(container)
    return () => ro.disconnect()
  }, [])

  // add new devices to positions
  useEffect(() => {
    const { w, h } = sizeRef.current
    if (w === 0) return
    for (const d of devices) {
      if (!positionsRef.current[d.id]) {
        const angle = Math.random() * Math.PI * 2
        const r = Math.min(w, h) * 0.3
        positionsRef.current[d.id] = {
          x: w/2 + r * Math.cos(angle),
          y: h/2 + r * Math.sin(angle),
        }
      }
    }
  }, [devices])

  // animation loop
  useEffect(() => {
    let lastT = 0

    function drawParticles() {
      const canvas = bgRef.current
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      particles.update()
      particles.draw(ctx, canvas.width, canvas.height)
    }

    function drawTopology(ts) {
      const canvas = fgRef.current
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      const { w, h } = sizeRef.current
      const dt = Math.min(ts - lastT, 50)
      lastT = ts

      const { scale, offsetX, offsetY } = transformRef.current

      ctx.clearRect(0, 0, w, h)

      ctx.save()
      ctx.translate(offsetX, offsetY)
      ctx.scale(scale, scale)

      const positions = positionsRef.current
      const devs = devicesRef.current
      const lnks = linksRef.current

      // --- edges ---
      for (const link of lnks) {
        const sp = positions[link.source]
        const tp = positions[link.target]
        if (!sp || !tp) continue
        const srcDev = devs.find(d => d.id === link.source)
        const tgtDev = devs.find(d => d.id === link.target)
        const offline = srcDev?.status === 'OFFLINE' || tgtDev?.status === 'OFFLINE'

        if (offline) {
          ctx.setLineDash([6, 5])
          setGlow(ctx, COLORS.RED, 6)
          ctx.strokeStyle = COLORS.EDGE_OFFLINE
          ctx.lineWidth = 0.8
        } else {
          ctx.setLineDash([])
          setGlow(ctx, COLORS.CYAN, 6)
          ctx.strokeStyle = COLORS.EDGE_ACTIVE
          ctx.lineWidth = 1.2
        }
        ctx.beginPath()
        ctx.moveTo(sp.x, sp.y)
        ctx.lineTo(tp.x, tp.y)
        ctx.stroke()
        ctx.setLineDash([])
        clearGlow(ctx)

        // latency label at midpoint
        const mx = (sp.x + tp.x) / 2
        const my = (sp.y + tp.y) / 2
        const lat = tgtDev?.latencies?.slice(-1)[0]
        if (lat != null) {
          ctx.font = '8px monospace'
          ctx.fillStyle = '#2A4A6A'
          ctx.textAlign = 'center'
          ctx.fillText(`${lat}ms`, mx, my - 4)
        }

        // bandwidth bars — log scale: 1 bar ≥ 1 Kbps, 2 ≥ 10, 3 ≥ 100, 4 ≥ 1 Mbps, 5 ≥ 10 Mbps
        if (!offline) {
          const bw = tgtDev?.bandwidth || 0
          const bars = bw <= 0 ? 0 : Math.min(5, Math.floor(Math.log10(bw)) + 1)
          for (let i = 0; i < 5; i++) {
            const barColor = i < bars ? (i < 2 ? COLORS.GREEN : i < 4 ? COLORS.AMBER : COLORS.RED) : '#0F2744'
            ctx.fillStyle = barColor
            ctx.fillRect(mx + (i - 2) * 4, my + 2, 3, 4)
          }
        }
      }

      // --- animated packets ---
      for (const pkt of packetsRef.current) {
        const link = lnks.find(l => l.id === pkt.linkId)
        if (!link) continue
        const sp = positions[link.source]
        const tp = positions[link.target]
        if (!sp || !tp) continue
        pkt.t += pkt.speed * (dt / 16)
        if (pkt.t > 1) pkt.t = 0
        const px = sp.x + (tp.x - sp.x) * pkt.t
        const py = sp.y + (tp.y - sp.y) * pkt.t
        setGlow(ctx, '#00FFFF', 8)
        ctx.beginPath()
        ctx.arc(px, py, 2.5, 0, Math.PI * 2)
        ctx.fillStyle = '#00FFFF'
        ctx.fill()
        clearGlow(ctx)
      }

      // --- nodes ---
      heartRef.current = (heartRef.current + 0.012) % 1

      for (const device of devs) {
        const pos = positions[device.id]
        if (!pos) continue
        const r       = getNodeRadius(device)
        const color   = nodeColor(device)
        const offline = device.status === 'OFFLINE'
        const selected = device.id === selectedDeviceId

        // radial halo
        ctx.globalAlpha = 0.08
        ctx.beginPath()
        ctx.arc(pos.x, pos.y, r + 14, 0, Math.PI * 2)
        ctx.fillStyle = color
        ctx.fill()
        ctx.globalAlpha = 1

        // selection outer ring
        if (selected) {
          const t = heartRef.current
          ctx.globalAlpha = 1 - t
          setGlow(ctx, COLORS.PURPLE, 16)
          ctx.beginPath()
          ctx.arc(pos.x, pos.y, r + 6 + t * 10, 0, Math.PI * 2)
          ctx.strokeStyle = COLORS.PURPLE
          ctx.lineWidth = 2
          ctx.stroke()
          clearGlow(ctx)
          ctx.globalAlpha = 1
        }

        // vulnerability risk ring (outer dashed arc)
        const riskColor = RISK_COLORS[device.securityScan?.riskLevel]
        if (riskColor) {
          ctx.save()
          ctx.setLineDash([4, 3])
          setGlow(ctx, riskColor, 10)
          ctx.beginPath()
          ctx.arc(pos.x, pos.y, r + 9, 0, Math.PI * 2)
          ctx.strokeStyle = riskColor
          ctx.lineWidth = 1.5
          ctx.globalAlpha = 0.8
          ctx.stroke()
          ctx.globalAlpha = 1
          clearGlow(ctx)
          ctx.restore()

          // CVE count badge (top-left corner)
          const cveCount = device.securityScan?.cveMatches?.length || 0
          if (cveCount > 0) {
            const bx = pos.x - r * 0.7
            const by = pos.y - r * 0.7
            ctx.font = 'bold 8px monospace'
            ctx.textAlign = 'center'
            ctx.textBaseline = 'middle'
            setGlow(ctx, riskColor, 6)
            ctx.fillStyle = riskColor
            ctx.fillText(cveCount, bx, by)
            clearGlow(ctx)
          }
        }

        // heartbeat expanding ring
        const hp = heartRef.current
        ctx.globalAlpha = (1 - hp) * 0.4
        ctx.beginPath()
        ctx.arc(pos.x, pos.y, r + hp * 12, 0, Math.PI * 2)
        ctx.strokeStyle = offline ? COLORS.RED : color
        ctx.lineWidth = 1
        ctx.stroke()
        ctx.globalAlpha = 1

        // node fill
        setGlow(ctx, offline ? COLORS.RED : color, offline ? 8 : 12)
        ctx.beginPath()
        ctx.arc(pos.x, pos.y, r, 0, Math.PI * 2)
        ctx.fillStyle = '#0A1628'
        ctx.fill()
        ctx.strokeStyle = offline ? COLORS.RED : color
        ctx.lineWidth = 1.5
        ctx.stroke()
        clearGlow(ctx)

        // icon
        const icon = offline ? '■' : (ICONS[device.type] || '○')
        ctx.font = `bold ${device.type === 'ROUTER' ? 14 : 11}px monospace`
        ctx.textAlign = 'center'
        ctx.textBaseline = 'middle'
        setGlow(ctx, color, 6)
        ctx.fillStyle = offline ? '#4A2A2A' : color
        ctx.fillText(icon, pos.x, pos.y)
        clearGlow(ctx)

        // hostname
        ctx.font = 'bold 9px monospace'
        ctx.textBaseline = 'top'
        setGlow(ctx, color, 4)
        ctx.fillStyle = offline ? '#4A2A2A' : color
        ctx.fillText(device.hostname, pos.x, pos.y + r + 4)
        clearGlow(ctx)

        // IP
        ctx.font = '8px monospace'
        ctx.fillStyle = '#2A4A6A'
        ctx.fillText(device.ip, pos.x, pos.y + r + 14)
        ctx.textBaseline = 'alphabetic'

        // NEW badge
        if (device.status === 'NEW') {
          const bx = pos.x + r
          const by = pos.y - r
          ctx.font = 'bold 7px monospace'
          ctx.textAlign = 'center'
          ctx.textBaseline = 'middle'
          ctx.fillStyle = '#FFB800'
          ctx.fillRect(bx - 8, by - 5, 16, 10)
          ctx.fillStyle = '#050D1A'
          ctx.fillText('NEW', bx, by)
          ctx.textBaseline = 'alphabetic'
        }

        // threat marker
        if (device.threatLevel && device.threatLevel !== 'none') {
          const tx = pos.x + r * 0.7
          const ty = pos.y - r * 0.7
          setGlow(ctx, device.threatLevel === 'high' ? COLORS.RED : COLORS.AMBER, 8)
          ctx.font = 'bold 10px monospace'
          ctx.textAlign = 'center'
          ctx.textBaseline = 'middle'
          ctx.fillStyle = device.threatLevel === 'high' ? COLORS.RED : COLORS.AMBER
          ctx.fillText('!', tx, ty)
          clearGlow(ctx)
        }
      }

      // --- radar sweep ---
      const router = devs.find(d => d.type === 'ROUTER')
      if (router && positions[router.id]) {
        const { x: rx, y: ry } = positions[router.id]
        sweepRef.current = (sweepRef.current + 0.008) % (Math.PI * 2)
        const sweep = sweepRef.current
        const sweepLen = Math.min(w, h) * 0.5
        const grad = ctx.createRadialGradient(rx, ry, 0, rx, ry, sweepLen)
        grad.addColorStop(0, 'rgba(0,217,255,0.12)')
        grad.addColorStop(1, 'rgba(0,217,255,0)')
        ctx.beginPath()
        ctx.moveTo(rx, ry)
        ctx.arc(rx, ry, sweepLen, sweep - 0.5, sweep)
        ctx.closePath()
        ctx.fillStyle = grad
        ctx.fill()
      }

      ctx.restore()

      // scan progress bar at bottom
      const prog = secondsUntilNextScan / 30
      setGlow(ctx, COLORS.CYAN, 4)
      ctx.fillStyle = COLORS.CYAN
      ctx.fillRect(0, h - 2, w * prog, 2)
      clearGlow(ctx)
    }

    function loop(ts) {
      drawParticles()
      drawTopology(ts)
      rafRef.current = requestAnimationFrame(loop)
    }
    rafRef.current = requestAnimationFrame(loop)
    return () => cancelAnimationFrame(rafRef.current)
  }, [selectedDeviceId, secondsUntilNextScan])

  // mouse helpers
  function getCanvasPoint(e) {
    const rect = fgRef.current.getBoundingClientRect()
    return { x: e.clientX - rect.left, y: e.clientY - rect.top }
  }

  function worldPoint(e) {
    const { x, y } = getCanvasPoint(e)
    return screenToWorld(x, y, transformRef.current)
  }

  const onWheel = useCallback((e) => {
    e.preventDefault()
    const { x, y } = getCanvasPoint(e)
    zoom(e.deltaY, x, y)
  }, [zoom])

  useEffect(() => {
    const canvas = fgRef.current
    if (!canvas) return
    canvas.addEventListener('wheel', onWheel, { passive: false })
    return () => canvas.removeEventListener('wheel', onWheel)
  }, [onWheel])

  const onMouseDown = useCallback((e) => {
    const wp = worldPoint(e)
    const hit = getNodeAtPoint(wp.x, wp.y, positionsRef.current, devicesRef.current)
    if (hit) {
      dragRef.current = { id: hit.id, lastX: e.clientX, lastY: e.clientY }
    } else {
      panRef.current = { lastX: e.clientX, lastY: e.clientY }
    }
  }, [])

  const onMouseMove = useCallback((e) => {
    if (dragRef.current) {
      const { id, lastX, lastY } = dragRef.current
      const s = transformRef.current.scale
      const pos = positionsRef.current[id]
      if (pos) {
        positionsRef.current[id] = {
          x: pos.x + (e.clientX - lastX) / s,
          y: pos.y + (e.clientY - lastY) / s,
        }
      }
      dragRef.current = { ...dragRef.current, lastX: e.clientX, lastY: e.clientY }
      fgRef.current.style.cursor = 'grabbing'
      return
    }
    if (panRef.current) {
      pan(e.clientX - panRef.current.lastX, e.clientY - panRef.current.lastY)
      panRef.current = { lastX: e.clientX, lastY: e.clientY }
      fgRef.current.style.cursor = 'grabbing'
      return
    }
    // hover hit test
    const wp  = worldPoint(e)
    const hit = getNodeAtPoint(wp.x, wp.y, positionsRef.current, devicesRef.current)
    fgRef.current.style.cursor = hit ? 'grab' : 'default'
    if (hit) {
      const sp = getCanvasPoint(e)
      setTooltip({ visible: true, x: sp.x + 14, y: sp.y + 14, device: hit })
    } else {
      setTooltip(t => t.visible ? { ...t, visible: false } : t)
    }
  }, [pan])

  const onMouseUp = useCallback((e) => {
    if (ctxMenu.visible) { setCtxMenu(m => ({ ...m, visible: false })); return }
    if (dragRef.current) {
      const movedX = Math.abs(e.clientX - dragRef.current.lastX)
      const movedY = Math.abs(e.clientY - dragRef.current.lastY)
      if (movedX < 3 && movedY < 3) {
        const wp  = worldPoint(e)
        const hit = getNodeAtPoint(wp.x, wp.y, positionsRef.current, devicesRef.current)
        if (hit) onSelectRef.current(hit.id)
      }
    }
    dragRef.current = null
    panRef.current  = null
    if (fgRef.current) fgRef.current.style.cursor = 'default'
  }, [ctxMenu.visible])

  const onContextMenu = useCallback((e) => {
    e.preventDefault()
    const wp  = worldPoint(e)
    const hit = getNodeAtPoint(wp.x, wp.y, positionsRef.current, devicesRef.current)
    if (hit) {
      const sp = getCanvasPoint(e)
      setCtxMenu({ visible: true, x: sp.x, y: sp.y, device: hit })
    }
  }, [])

  return (
    <div ref={containerRef} className="relative flex-1 overflow-hidden min-w-0"
      style={{ background: '#050D1A' }}>
      {/* Layer 1: particle field */}
      <canvas ref={bgRef} className="absolute inset-0" style={{ opacity: 0.6 }} />
      {/* Layer 2: topology */}
      <canvas
        ref={fgRef}
        className="absolute inset-0"
        onMouseDown={onMouseDown}
        onMouseMove={onMouseMove}
        onMouseUp={onMouseUp}
        onContextMenu={onContextMenu}
        onMouseLeave={() => {
          dragRef.current = null
          panRef.current  = null
          setTooltip(t => ({ ...t, visible: false }))
        }}
      />

      {/* Corner label */}
      <div className="absolute top-2 left-3 text-[10px] pointer-events-none select-none"
        style={{ color: '#1E4060' }}>
        Live topology — scroll zoom · drag to pan
      </div>

      {/* Fullscreen exit button */}
      {isFullscreen && (
        <button
          onClick={onExitFullscreen}
          className="absolute top-3 right-3 p-2 rounded"
          style={{ background: '#0A1628', border: '1px solid #0F2744', color: '#4A7FA5' }}
        >
          <Minimize2 size={14} />
        </button>
      )}

      {/* Right-click context menu */}
      {ctxMenu.visible && ctxMenu.device && (
        <div
          className="absolute z-30 rounded py-1 font-mono text-[10px]"
          style={{
            left: ctxMenu.x, top: ctxMenu.y,
            background: '#0A1628',
            border: '1px solid #0F2744',
            boxShadow: '0 0 16px rgba(0,217,255,0.15)',
            minWidth: 180,
          }}
          onMouseLeave={() => setCtxMenu(m => ({ ...m, visible: false }))}
        >
          <div className="px-3 py-1.5 font-bold" style={{ color: RISK_COLORS[ctxMenu.device.securityScan?.riskLevel] || '#00D9FF', borderBottom: '1px solid #0F2744' }}>
            {ctxMenu.device.hostname}
          </div>
          <button
            className="w-full text-left px-3 py-1.5 hover:bg-[#0F2744] transition-colors"
            style={{ color: '#FFB800', cursor: 'pointer', background: 'transparent', border: 'none' }}
            onClick={() => {
              onAddDnsRule?.({ domain: ctxMenu.device.hostname, ip: ctxMenu.device.ip })
              setCtxMenu(m => ({ ...m, visible: false }))
            }}
          >
            ⚡ Spoof DNS for this device
          </button>
          <button
            className="w-full text-left px-3 py-1.5 hover:bg-[#0F2744] transition-colors"
            style={{ color: '#4A7FA5', cursor: 'pointer', background: 'transparent', border: 'none' }}
            onClick={() => {
              onAddDnsRule?.({ domain: '*', ip: ctxMenu.device.ip })
              setCtxMenu(m => ({ ...m, visible: false }))
            }}
          >
            ⚡ Redirect ALL traffic → this IP
          </button>
        </div>
      )}

      {/* Hover tooltip */}
      {tooltip.visible && tooltip.device && (
        <div
          className="absolute pointer-events-none rounded px-2 py-1.5 text-[10px] font-mono"
          style={{
            left: tooltip.x, top: tooltip.y,
            background: '#0A1628',
            border: '1px solid #0F2744',
            boxShadow: '0 0 12px rgba(0,217,255,0.2)',
            color: '#E0F4FF',
            zIndex: 20,
            minWidth: 130,
          }}
        >
          <div className="font-bold" style={{ color: nodeColor(tooltip.device) }}>
            {tooltip.device.hostname}
          </div>
          <div style={{ color: '#4A7FA5' }}>{tooltip.device.ip}</div>
          <div style={{ color: '#4A7FA5' }}>{tooltip.device.vendor}</div>
          <div className="flex items-center gap-1 mt-0.5">
            <span className="rounded-full w-1.5 h-1.5 inline-block"
              style={{ background: tooltip.device.status === 'ONLINE' ? '#00FF9C' : tooltip.device.status === 'NEW' ? '#FFB800' : '#FF2D55' }} />
            <span>{tooltip.device.status}</span>
            {tooltip.device.latencies?.slice(-1)[0] != null && (
              <span className="ml-auto" style={{ color: '#4A7FA5' }}>
                {tooltip.device.latencies.slice(-1)[0]}ms
              </span>
            )}
          </div>
          {tooltip.device.securityScan?.riskLevel && tooltip.device.securityScan.riskLevel !== 'CLEAN' && (
            <div className="flex items-center gap-1 mt-0.5">
              <span className="text-[9px] font-bold"
                style={{ color: RISK_COLORS[tooltip.device.securityScan.riskLevel] || '#4A7FA5' }}>
                ⚠ {tooltip.device.securityScan.riskLevel}
              </span>
              <span className="text-[8px] ml-auto" style={{ color: '#4A7FA5' }}>
                {tooltip.device.securityScan.cveMatches?.length || 0} CVEs ·{' '}
                {tooltip.device.securityScan.openPorts?.length || 0} ports
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
