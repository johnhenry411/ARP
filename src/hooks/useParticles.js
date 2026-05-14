import { useRef } from 'react'

const PARTICLE_COUNT = 80
const MAX_CONNECT_DIST = 90

function createParticles(w, h) {
  return Array.from({ length: PARTICLE_COUNT }, () => ({
    x:  Math.random() * w,
    y:  Math.random() * h,
    vx: (Math.random() - 0.5) * 0.3,
    vy: (Math.random() - 0.5) * 0.3,
  }))
}

export function useParticles() {
  const particlesRef = useRef(null)
  const sizeRef = useRef({ w: 1, h: 1 })

  function init(w, h) {
    sizeRef.current = { w, h }
    particlesRef.current = createParticles(w, h)
  }

  function update() {
    const { w, h } = sizeRef.current
    const pts = particlesRef.current
    if (!pts) return
    for (const p of pts) {
      p.x += p.vx
      p.y += p.vy
      if (p.x < 0)  p.x = w
      if (p.x > w)  p.x = 0
      if (p.y < 0)  p.y = h
      if (p.y > h)  p.y = 0
    }
  }

  function draw(ctx, w, h) {
    const pts = particlesRef.current
    if (!pts) return

    ctx.clearRect(0, 0, w, h)

    for (let i = 0; i < pts.length; i++) {
      for (let j = i + 1; j < pts.length; j++) {
        const dx = pts[i].x - pts[j].x
        const dy = pts[i].y - pts[j].y
        const dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < MAX_CONNECT_DIST) {
          const alpha = (1 - dist / MAX_CONNECT_DIST) * 0.35
          ctx.beginPath()
          ctx.moveTo(pts[i].x, pts[i].y)
          ctx.lineTo(pts[j].x, pts[j].y)
          ctx.strokeStyle = `rgba(13,37,64,${alpha})`
          ctx.lineWidth = 0.6
          ctx.stroke()
        }
      }
    }

    ctx.fillStyle = 'rgba(13,37,64,0.9)'
    for (const p of pts) {
      ctx.beginPath()
      ctx.arc(p.x, p.y, 0.9, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  return { particlesRef, init, update, draw }
}
