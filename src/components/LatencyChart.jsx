import { BarChart, Bar, Cell } from 'recharts'

export default function LatencyChart({ latencies }) {
  if (!latencies || latencies.length === 0) return null

  const data = latencies.map((value, index) => ({ index, value }))

  return (
    <div style={{ filter: 'drop-shadow(0 0 3px rgba(0,217,255,0.3))' }}>
      <BarChart width={200} height={44} data={data} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
        <Bar dataKey="value" radius={[2, 2, 0, 0]} barSize={14} isAnimationActive={false}>
          {data.map((entry, i) => (
            <Cell key={i} fill={entry.value > 20 ? '#FFB800' : '#00D9FF'} />
          ))}
        </Bar>
      </BarChart>
    </div>
  )
}
