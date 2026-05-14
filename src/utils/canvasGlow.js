export function setGlow(ctx, color, blur = 10) {
  ctx.shadowColor = color
  ctx.shadowBlur = blur
}

export function clearGlow(ctx) {
  ctx.shadowColor = 'transparent'
  ctx.shadowBlur = 0
}

export function drawGlowCircle(ctx, x, y, r, fillColor, strokeColor, glowBlur = 10) {
  setGlow(ctx, strokeColor, glowBlur)
  ctx.beginPath()
  ctx.arc(x, y, r, 0, Math.PI * 2)
  ctx.fillStyle = fillColor
  ctx.fill()
  ctx.strokeStyle = strokeColor
  ctx.lineWidth = 1.5
  ctx.stroke()
  clearGlow(ctx)
}

export function drawGlowLine(ctx, x1, y1, x2, y2, color, lineWidth = 1.2, glowBlur = 8) {
  setGlow(ctx, color, glowBlur)
  ctx.beginPath()
  ctx.moveTo(x1, y1)
  ctx.lineTo(x2, y2)
  ctx.strokeStyle = color
  ctx.lineWidth = lineWidth
  ctx.stroke()
  clearGlow(ctx)
}

export function drawGlowText(ctx, text, x, y, color, font, glowBlur = 6) {
  setGlow(ctx, color, glowBlur)
  ctx.fillStyle = color
  ctx.font = font
  ctx.fillText(text, x, y)
  clearGlow(ctx)
}
