import { motion } from 'framer-motion'
import { healthColor } from '../utils/healthScore'

const SIZE   = 38
const STROKE = 3
const R      = (SIZE - STROKE * 2) / 2
const CIRC   = 2 * Math.PI * R
const CX     = SIZE / 2
const CY     = SIZE / 2

export default function HealthGauge({ score }) {
  const color  = healthColor(score)
  const filled = CIRC * (1 - score / 100)

  return (
    <div className="relative flex items-center justify-center" style={{ width: SIZE, height: SIZE }}>
      <svg width={SIZE} height={SIZE} style={{ transform: 'rotate(-90deg)' }}>
        {/* track */}
        <circle cx={CX} cy={CY} r={R} fill="none" stroke="#0F2744" strokeWidth={STROKE} />
        {/* animated fill */}
        <motion.circle
          cx={CX} cy={CY} r={R}
          fill="none"
          stroke={color}
          strokeWidth={STROKE}
          strokeLinecap="round"
          strokeDasharray={CIRC}
          animate={{ strokeDashoffset: filled }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
          style={{ filter: `drop-shadow(0 0 3px ${color})` }}
        />
      </svg>
      <span
        className="absolute text-[9px] font-mono font-bold"
        style={{ color, textShadow: `0 0 6px ${color}` }}
      >
        {score}
      </span>
    </div>
  )
}
