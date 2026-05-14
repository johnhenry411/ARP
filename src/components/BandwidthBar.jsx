export default function BandwidthBar({ bandwidth = 0, maxBandwidth = 300 }) {
  const bars  = 5
  const ratio = Math.min(1, bandwidth / maxBandwidth)
  const lit   = Math.round(ratio * bars)

  return (
    <div className="flex items-center gap-0.5" title={`${bandwidth} Mbps`}>
      {Array.from({ length: bars }, (_, i) => {
        const active = i < lit
        const color  = i < 2 ? '#00FF9C' : i < 4 ? '#FFB800' : '#FF2D55'
        return (
          <span
            key={i}
            style={{
              width: 3,
              height: 5 + i,
              borderRadius: 1,
              background: active ? color : '#0F2744',
              boxShadow: active ? `0 0 4px ${color}` : 'none',
              display: 'inline-block',
            }}
          />
        )
      })}
    </div>
  )
}
