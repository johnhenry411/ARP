export const DEVICES = [
  {
    id: 'gw-01', ip: '192.168.1.1', mac: 'A4:2B:8C:11:FF:02',
    hostname: 'tplinkrouter', vendor: 'TP-Link Technologies',
    type: 'ROUTER', status: 'ONLINE',
    latencies: [4,3,5,2,6,4,7,4,3,4],
    bandwidth: 300, threatLevel: 'none', firstSeen: '09:12:00',
  },
  {
    id: 'host-01', ip: '192.168.1.12', mac: 'B8:27:EB:44:A1:09',
    hostname: 'macbook-henry', vendor: 'Apple Inc.',
    type: 'HOST', status: 'ONLINE',
    latencies: [2,3,2,1,2,3,2,2,1,2],
    bandwidth: 150, threatLevel: 'none', firstSeen: '09:12:10',
  },
  {
    id: 'mob-01', ip: '192.168.1.47', mac: 'E8:4E:06:A2:BB:31',
    hostname: 'Galaxy-S23', vendor: 'Samsung Electronics',
    type: 'MOBILE', status: 'NEW',
    latencies: [11,9,12,10,11,13,10,11,9,11],
    bandwidth: 54, threatLevel: 'low', firstSeen: '09:41:22',
  },
  {
    id: 'host-02', ip: '192.168.1.22', mac: 'CC:2D:E0:88:12:7F',
    hostname: 'samsung-tv', vendor: 'Samsung Electronics',
    type: 'HOST', status: 'ONLINE',
    latencies: [6,5,87,7,6,6,5,7,6,5],
    bandwidth: 100, threatLevel: 'low', firstSeen: '09:12:44',
  },
  {
    id: 'host-03', ip: '192.168.1.55', mac: 'B8:27:EB:CC:99:01',
    hostname: 'raspberrypi', vendor: 'Raspberry Pi Foundation',
    type: 'HOST', status: 'ONLINE',
    latencies: [3,3,4,3,3,3,4,3,3,3],
    bandwidth: 100, threatLevel: 'none', firstSeen: '09:13:00',
  },
  {
    id: 'host-04', ip: '192.168.1.8', mac: '00:1A:2B:3C:4D:5E',
    hostname: 'DESKTOP-PC', vendor: 'Dell Inc.',
    type: 'HOST', status: 'OFFLINE',
    latencies: [],
    bandwidth: 0, threatLevel: 'none', firstSeen: '08:50:00',
  },
  {
    id: 'host-05', ip: '192.168.1.33', mac: 'F0:18:98:A3:D2:44',
    hostname: 'HP-Printer', vendor: 'HP Inc.',
    type: 'HOST', status: 'ONLINE',
    latencies: [4,4,5,4,4,4,4,5,4,4],
    bandwidth: 10, threatLevel: 'none', firstSeen: '09:12:55',
  },
  {
    id: 'host-06', ip: '192.168.1.71', mac: '3C:22:FB:01:88:9A',
    hostname: 'iPad-Air', vendor: 'Apple Inc.',
    type: 'MOBILE', status: 'ONLINE',
    latencies: [5,6,5,4,5,5,6,5,5,5],
    bandwidth: 54, threatLevel: 'none', firstSeen: '09:15:30',
  },
]

export const LINKS = [
  { id: 'l1', source: 'gw-01', target: 'host-01', discoveredBy: 'ARP' },
  { id: 'l2', source: 'gw-01', target: 'mob-01',  discoveredBy: 'SNMP' },
  { id: 'l3', source: 'gw-01', target: 'host-02', discoveredBy: 'ARP' },
  { id: 'l4', source: 'gw-01', target: 'host-03', discoveredBy: 'ARP' },
  { id: 'l5', source: 'gw-01', target: 'host-04', discoveredBy: 'ARP' },
  { id: 'l6', source: 'gw-01', target: 'host-05', discoveredBy: 'ARP' },
  { id: 'l7', source: 'gw-01', target: 'host-06', discoveredBy: 'ARP' },
]

export const ALERTS = [
  { id: 'a1', type: 'DEVICE_JOINED', deviceId: 'mob-01',  message: 'Galaxy-S23 joined',      timestamp: '09:41:22' },
  { id: 'a2', type: 'HIGH_LATENCY',  deviceId: 'host-02', message: 'Smart TV 87ms peak',      timestamp: '09:38:05' },
  { id: 'a3', type: 'DEVICE_LOST',   deviceId: 'host-04', message: 'DESKTOP-PC offline',      timestamp: '09:25:10' },
  { id: 'a4', type: 'SCAN_STARTED',  deviceId: null,       message: 'Scan 192.168.1.0/24',     timestamp: '09:12:00' },
]

export const SCAN_CONFIG = {
  subnet: '192.168.1.0/24',
  intervalSeconds: 30,
  lastScanAgo: 3,
}
