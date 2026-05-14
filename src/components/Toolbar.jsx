import { motion } from 'framer-motion'
import { Square, Play, Download, Sun, Moon, Maximize2, Minimize2, RotateCcw } from 'lucide-react'
import HealthGauge from './HealthGauge'

export default function Toolbar({
  scanRunning, secondsUntilNextScan, subnet, onSubnetChange,
  deviceCount, healthScore, onToggleScan, onExport,
  isDarkMode, onToggleMode, isFullscreen, onToggleFullscreen,
  connectionStatus,
}) {
  const progress = secondsUntilNextScan / 30

  return (
    <div
      className="relative flex items-center gap-3 px-4 shrink-0 font-mono select-none"
      style={{
        height: 48,
        background: '#060E1C',
        borderBottom: '1px solid #0F2744',
        boxShadow: '0 0 20px rgba(0,217,255,0.06)',
      }}
    >
      {/* Brand */}
      <span className="text-[13px] font-bold tracking-widest text-glow-cyan" style={{ color: '#00D9FF' }}>
        NETMAPPER
      </span>
      <span className="text-[9px] text-cyber-dim border border-cyber-border rounded px-1">v0.1</span>

      {/* Status badge */}
      <div className="flex items-center gap-1.5">
        {scanRunning && (
          <span className="relative flex h-2 w-2">
            <span className="animate-pulse-ring absolute inline-flex h-full w-full rounded-full opacity-75"
              style={{ background: '#00D9FF' }} />
            <span className="relative inline-flex rounded-full h-2 w-2" style={{ background: '#00D9FF' }} />
          </span>
        )}
        <motion.span
          animate={scanRunning ? { scale: [1, 1.08, 1] } : {}}
          transition={{ repeat: Infinity, duration: 1.2 }}
          className="text-[10px] font-bold px-2 py-0.5 rounded-full"
          style={{
            background: scanRunning ? 'rgba(0,217,255,0.12)' : 'rgba(255,184,0,0.12)',
            color:      scanRunning ? '#00D9FF' : '#FFB800',
            border:     `1px solid ${scanRunning ? '#00D9FF44' : '#FFB80044'}`,
            textShadow: `0 0 6px ${scanRunning ? '#00D9FF' : '#FFB800'}`,
          }}
        >
          {scanRunning ? 'SCANNING' : 'PAUSED'}
        </motion.span>
      </div>

      {/* Subnet input */}
      <div className="flex items-center gap-1 px-2 py-0.5 rounded"
        style={{ background: '#0A1628', border: '1px solid #0F2744' }}>
        <span className="text-[10px]" style={{ color: '#1E4060' }}>›</span>
        <input
          value={subnet}
          onChange={e => onSubnetChange(e.target.value)}
          className="bg-transparent text-[11px] font-mono w-[120px]"
          style={{ color: '#00D9FF', caretColor: '#00D9FF' }}
        />
      </div>

      {/* Device count */}
      <span className="text-[11px]" style={{ color: '#4A7FA5' }}>
        {deviceCount} devices
      </span>

      {/* Countdown */}
      <span className="text-[10px]" style={{ color: '#1E4060' }}>
        {scanRunning ? `Next in ${secondsUntilNextScan}s` : 'paused'}
      </span>

      {/* Health gauge — centered */}
      <div className="flex-1 flex items-center justify-center gap-2">
        <HealthGauge score={healthScore} />
        <span className="text-[10px]" style={{ color: '#4A7FA5' }}>NET HEALTH</span>
      </div>

      {/* Connection status (live mode only) */}
      {connectionStatus !== 'mock' && (
        <span className="text-[10px] px-1.5 py-0.5 rounded"
          style={{
            color: connectionStatus === 'open' ? '#00FF9C' : '#FF2D55',
            background: connectionStatus === 'open' ? 'rgba(0,255,156,0.1)' : 'rgba(255,45,85,0.1)',
          }}>
          {connectionStatus === 'open' ? 'LIVE' : connectionStatus === 'connecting' ? 'CONNECTING…' : 'DISCONNECTED'}
        </span>
      )}

      {/* Icon buttons */}
      <div className="flex items-center gap-1">
        <ToolbarBtn icon={isDarkMode ? <Sun size={13}/> : <Moon size={13}/>} onClick={onToggleMode} title="Toggle theme" />
        <ToolbarBtn icon={isFullscreen ? <Minimize2 size={13}/> : <Maximize2 size={13}/>} onClick={onToggleFullscreen} title="Fullscreen" />
        <ToolbarBtn icon={<Download size={13}/>} onClick={onExport} title="Export JSON" />
        <motion.button
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
          onClick={onToggleScan}
          className="flex items-center gap-1 px-2 py-1 rounded text-[11px] font-bold"
          style={{
            background: scanRunning ? 'rgba(255,45,85,0.12)' : 'rgba(0,255,156,0.12)',
            border:     `1px solid ${scanRunning ? '#FF2D5544' : '#00FF9C44'}`,
            color:      scanRunning ? '#FF2D55' : '#00FF9C',
          }}
        >
          {scanRunning ? <><Square size={10}/> STOP</> : <><Play size={10}/> START</>}
        </motion.button>
      </div>

      {/* Progress bar at bottom */}
      <div className="absolute bottom-0 left-0 right-0 h-[2px]" style={{ background: '#0F2744' }}>
        <motion.div
          className="h-full"
          animate={{ width: `${progress * 100}%` }}
          transition={{ duration: 0.5 }}
          style={{ background: '#00D9FF', boxShadow: '0 0 6px #00D9FF' }}
        />
      </div>
    </div>
  )
}

function ToolbarBtn({ icon, onClick, title }) {
  return (
    <motion.button
      whileHover={{ scale: 1.1 }}
      whileTap={{ scale: 0.9 }}
      onClick={onClick}
      title={title}
      className="p-1.5 rounded"
      style={{ color: '#4A7FA5', background: 'transparent', border: '1px solid transparent' }}
      onMouseEnter={e => { e.currentTarget.style.borderColor = '#0F2744'; e.currentTarget.style.color = '#00D9FF' }}
      onMouseLeave={e => { e.currentTarget.style.borderColor = 'transparent'; e.currentTarget.style.color = '#4A7FA5' }}
    >
      {icon}
    </motion.button>
  )
}
