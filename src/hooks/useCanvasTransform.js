import { useState, useCallback } from 'react'

const MIN_SCALE = 0.3
const MAX_SCALE = 2.5

export function useCanvasTransform() {
  const [transform, setTransform] = useState({ scale: 1, offsetX: 0, offsetY: 0 })

  const screenToWorld = useCallback((sx, sy, t = transform) => ({
    x: (sx - t.offsetX) / t.scale,
    y: (sy - t.offsetY) / t.scale,
  }), [transform])

  const worldToScreen = useCallback((wx, wy, t = transform) => ({
    x: wx * t.scale + t.offsetX,
    y: wy * t.scale + t.offsetY,
  }), [transform])

  const zoom = useCallback((delta, pivotX, pivotY) => {
    setTransform(t => {
      const factor = delta > 0 ? 0.9 : 1.1
      const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, t.scale * factor))
      const ratio = newScale / t.scale
      return {
        scale: newScale,
        offsetX: pivotX - ratio * (pivotX - t.offsetX),
        offsetY: pivotY - ratio * (pivotY - t.offsetY),
      }
    })
  }, [])

  const pan = useCallback((dx, dy) => {
    setTransform(t => ({ ...t, offsetX: t.offsetX + dx, offsetY: t.offsetY + dy }))
  }, [])

  const reset = useCallback(() => {
    setTransform({ scale: 1, offsetX: 0, offsetY: 0 })
  }, [])

  return { transform, zoom, pan, reset, screenToWorld, worldToScreen }
}
