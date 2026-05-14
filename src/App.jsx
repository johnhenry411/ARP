import { useState, useCallback, useMemo } from 'react'
import { useTopology }       from './hooks/useTopology'
import { useScanner }        from './hooks/useScanner'
import { computeHealthScore } from './utils/healthScore'
import { SCAN_CONFIG }       from './data/mockData'
import Toolbar               from './components/Toolbar'
import LeftSidebar           from './components/LeftSidebar'
import TopologyCanvas        from './components/TopologyCanvas'
import RightPanel            from './components/RightPanel'

export default function App() {
  const { devices, links, alerts, triggerScan, addDevice, removeDevice, clearDevices, connectionStatus } = useTopology()
  const { scanRunning, secondsUntilNextScan, toggleScan } = useScanner(triggerScan)

  const [selectedDeviceId, setSelectedDeviceId] = useState('gw-01')
  const [activeRightTab,   setActiveRightTab]   = useState('inspector')
  const [leftCollapsed,    setLeftCollapsed]    = useState(false)
  const [rightCollapsed,   setRightCollapsed]   = useState(false)
  const [isDarkMode,       setIsDarkMode]       = useState(true)
  const [isFullscreen,     setIsFullscreen]     = useState(false)
  const [subnet,           setSubnet]           = useState(SCAN_CONFIG.subnet)
  const [dnsPreset,        setDnsPreset]        = useState(null)

  // Filter state — lifted here so canvas and list stay in sync
  const [search,       setSearch]       = useState('')
  const [typeFilter,   setTypeFilter]   = useState('ALL')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const healthScore = computeHealthScore(devices)

  const filteredDevices = useMemo(() => devices.filter(d => {
    const matchType   = typeFilter   === 'ALL' || d.type   === typeFilter
    const matchStatus = statusFilter === 'ALL' || d.status === statusFilter
    const matchSearch = !search
      || d.hostname.toLowerCase().includes(search.toLowerCase())
      || d.ip.includes(search)
      || d.vendor.toLowerCase().includes(search.toLowerCase())
    return matchType && matchStatus && matchSearch
  }), [devices, typeFilter, statusFilter, search])

  const filteredIds = useMemo(() => new Set(filteredDevices.map(d => d.id)), [filteredDevices])

  const filteredLinks = useMemo(
    () => links.filter(l => filteredIds.has(l.source) && filteredIds.has(l.target)),
    [links, filteredIds]
  )

  const handleSelectDevice = useCallback((id) => {
    setSelectedDeviceId(id)
    setActiveRightTab('inspector')
    if (rightCollapsed) setRightCollapsed(false)
  }, [rightCollapsed])

  const handleExport = () => {
    console.log(JSON.stringify({ devices, links }, null, 2))
  }

  return (
    <div
      className="flex flex-col h-screen font-mono overflow-hidden"
      style={{ background: '#050D1A', color: '#E0F4FF' }}
    >
      <div className="scanline-overlay" />

      {!isFullscreen && (
        <Toolbar
          scanRunning={scanRunning}
          secondsUntilNextScan={secondsUntilNextScan}
          subnet={subnet}
          onSubnetChange={setSubnet}
          deviceCount={devices.length}
          healthScore={healthScore}
          onToggleScan={toggleScan}
          onExport={handleExport}
          isDarkMode={isDarkMode}
          onToggleMode={() => setIsDarkMode(m => !m)}
          isFullscreen={isFullscreen}
          onToggleFullscreen={() => setIsFullscreen(f => !f)}
          connectionStatus={connectionStatus || 'mock'}
        />
      )}

      <div className="flex flex-1 overflow-hidden min-h-0">
        {!isFullscreen && (
          <LeftSidebar
            devices={devices}
            filteredDevices={filteredDevices}
            collapsed={leftCollapsed}
            onToggle={() => setLeftCollapsed(c => !c)}
            selectedDeviceId={selectedDeviceId}
            onSelectDevice={handleSelectDevice}
            addDevice={addDevice}
            clearDevices={clearDevices}
            search={search}         onSearchChange={setSearch}
            typeFilter={typeFilter} onTypeFilterChange={setTypeFilter}
            statusFilter={statusFilter} onStatusFilterChange={setStatusFilter}
          />
        )}

        <TopologyCanvas
          devices={filteredDevices}
          links={filteredLinks}
          selectedDeviceId={selectedDeviceId}
          onSelectDevice={handleSelectDevice}
          scanRunning={scanRunning}
          secondsUntilNextScan={secondsUntilNextScan}
          isFullscreen={isFullscreen}
          onExitFullscreen={() => setIsFullscreen(false)}
          onAddDnsRule={(preset) => {
            setDnsPreset(preset)
            setActiveRightTab('dns')
            if (rightCollapsed) setRightCollapsed(false)
          }}
        />

        {!isFullscreen && (
          <RightPanel
            devices={devices}
            alerts={alerts}
            selectedDeviceId={selectedDeviceId}
            activeTab={activeRightTab}
            onTabChange={setActiveRightTab}
            onSelectDevice={handleSelectDevice}
            onRemoveDevice={async (id) => { await removeDevice(id); setSelectedDeviceId(null) }}
            collapsed={rightCollapsed}
            onToggle={() => setRightCollapsed(c => !c)}
            dnsPreset={dnsPreset}
            onClearDnsPreset={() => setDnsPreset(null)}
          />
        )}
      </div>
    </div>
  )
}
