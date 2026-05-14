export default function ThreatBadge({ level, reason }) {
  if (!level || level === 'none') return null
  const isHigh = level === 'high'
  const color  = isHigh ? '#FF2D55' : '#FFB800'
  const label  = isHigh ? '!!' : '!'
  const tip    = reason || (isHigh ? 'High threat detected' : 'Anomaly detected')

  return (
    <span
      title={tip}
      className="relative inline-flex items-center justify-center text-[9px] font-bold rounded-full ml-1"
      style={{
        width: 14, height: 14,
        background: `${color}22`,
        border: `1px solid ${color}`,
        color,
        boxShadow: `0 0 6px ${color}66`,
        cursor: 'help',
      }}
    >
      <span
        className="absolute inset-0 rounded-full animate-pulse-ring"
        style={{ background: color, opacity: 0.3 }}
      />
      {label}
    </span>
  )
}
