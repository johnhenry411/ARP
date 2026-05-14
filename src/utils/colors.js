export const COLORS = {
  BG_CANVAS:      '#050D1A',
  BG_SIDEBAR:     '#060E1C',
  BG_TOOLBAR:     '#060E1C',
  BG_CARD:        '#0A1628',
  BG_CARD_HOVER:  '#0E1E38',
  BG_SELECTED:    '#0B1F3A',

  CYAN:           '#00D9FF',
  PURPLE:         '#7B2FFF',
  RED:            '#FF2D55',
  AMBER:          '#FFB800',
  GREEN:          '#00FF9C',

  STATUS_ONLINE:  '#00FF9C',
  STATUS_OFFLINE: '#FF2D55',
  STATUS_NEW:     '#FFB800',

  NODE_ROUTER:    '#00D9FF',
  NODE_HOST:      '#00FF9C',
  NODE_MOBILE:    '#7B2FFF',

  EDGE_ACTIVE:    '#00D9FF',
  EDGE_OFFLINE:   '#FF2D55',
  EDGE_PACKET:    '#00FFFF',

  PARTICLE:       '#0D2540',
  PARTICLE_LINE:  '#0D2540',

  ALERT_JOINED:   '#00FF9C',
  ALERT_LOST:     '#FF2D55',
  ALERT_LATENCY:  '#FFB800',
  ALERT_SCAN:     '#334466',

  BORDER:         '#0F2744',
  BORDER_ACTIVE:  '#00D9FF',
  TEXT_PRIMARY:   '#E0F4FF',
  TEXT_SECONDARY: '#4A7FA5',
  TEXT_DIM:       '#1E4060',
}

export function nodeColor(device) {
  if (!device) return COLORS.TEXT_DIM
  if (device.status === 'OFFLINE') return COLORS.STATUS_OFFLINE
  if (device.type === 'ROUTER')   return COLORS.NODE_ROUTER
  if (device.type === 'MOBILE')   return COLORS.NODE_MOBILE
  return COLORS.NODE_HOST
}

export function statusColor(status) {
  if (status === 'ONLINE')  return COLORS.STATUS_ONLINE
  if (status === 'OFFLINE') return COLORS.STATUS_OFFLINE
  if (status === 'NEW')     return COLORS.STATUS_NEW
  return COLORS.TEXT_DIM
}

export function alertColor(type) {
  if (type === 'DEVICE_JOINED') return COLORS.ALERT_JOINED
  if (type === 'DEVICE_LOST')   return COLORS.ALERT_LOST
  if (type === 'HIGH_LATENCY')  return COLORS.ALERT_LATENCY
  return COLORS.ALERT_SCAN
}
