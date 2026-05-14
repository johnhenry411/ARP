export function computeInitialLayout(devices, w, h) {
  const positions = {}
  const router = devices.find(d => d.type === 'ROUTER')
  const others  = devices.filter(d => d.type !== 'ROUTER')

  const cx = w / 2
  const cy = h / 2
  const radius = Math.min(w, h) * 0.35

  if (router) {
    positions[router.id] = { x: cx, y: cy }
  }

  others.forEach((device, i) => {
    const angle = (i / others.length) * Math.PI * 2 - Math.PI / 2
    positions[device.id] = {
      x: cx + radius * Math.cos(angle),
      y: cy + radius * Math.sin(angle),
    }
  })

  return positions
}
