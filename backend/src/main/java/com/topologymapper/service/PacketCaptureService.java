package com.topologymapper.service;

import com.topologymapper.model.Device;
import com.topologymapper.store.TopologyStore;
import java.util.ArrayList;
import java.util.List;
import com.topologymapper.websocket.WebSocketBroadcaster;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.pcap4j.packet.IpV4Packet;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures packets on the network interface connected to the hotspot subnet
 * (192.168.43.0/24) and computes per-device bandwidth in Kbps every 5 seconds.
 *
 * Requires Npcap on Windows (https://npcap.com) — degrades gracefully if absent.
 */
@Slf4j
@Service
public class PacketCaptureService implements DisposableBean {

    @Autowired private TopologyStore store;
    @Autowired private WebSocketBroadcaster broadcaster;

    private final ConcurrentHashMap<String, AtomicLong> byteCounters = new ConcurrentHashMap<>();
    private volatile PcapHandle handle;

    @PostConstruct
    public void init() {
        try {
            PcapNetworkInterface nif = findHotspotInterface();
            if (nif == null) {
                log.info("Packet capture: no 192.168.43.x interface found — will retry when a hotspot device is added");
                return;
            }
            openCapture(nif);
        } catch (Exception e) {
            log.warn("Packet capture unavailable (Npcap installed?): {}", e.getMessage());
        }
    }

    private PcapNetworkInterface findHotspotInterface() throws PcapNativeException {
        for (PcapNetworkInterface nif : Pcaps.findAllDevs()) {
            for (PcapAddress addr : nif.getAddresses()) {
                if (addr.getAddress() instanceof Inet4Address ia) {
                    String ip = ia.getHostAddress();
                    if (ip.startsWith("192.168.43.") && !ip.equals("192.168.43.1")) {
                        log.info("Packet capture: using interface {} ({})", nif.getName(), ip);
                        return nif;
                    }
                }
            }
        }
        return null;
    }

    private void openCapture(PcapNetworkInterface nif) throws PcapNativeException {
        handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
        Thread t = new Thread(() -> {
            try {
                handle.loop(-1, (PacketListener) packet -> {
                    IpV4Packet ip4 = packet.get(IpV4Packet.class);
                    if (ip4 == null) return;
                    String src = ip4.getHeader().getSrcAddr().getHostAddress();
                    String dst = ip4.getHeader().getDstAddr().getHostAddress();
                    int len = ip4.length();
                    byteCounters.computeIfAbsent(src, k -> new AtomicLong()).addAndGet(len);
                    byteCounters.computeIfAbsent(dst, k -> new AtomicLong()).addAndGet(len);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("Capture loop ended: {}", e.getMessage());
            }
        }, "pcap-capture");
        t.setDaemon(true);
        t.start();
        log.info("Packet capture started");
    }

    @Scheduled(fixedDelay = 5_000)
    public void flushCounters() {
        if (handle == null || !handle.isOpen()) return;

        boolean changed = false;
        for (Device device : store.allDevices()) {
            AtomicLong counter = byteCounters.remove(device.getIp());
            if (counter == null) continue;
            // bytes over 5 s → Kbps:  bytes * 8 bits / 5 s / 1000
            int kbps = (int) (counter.get() * 8L / 5 / 1000);
            device.setBandwidth(kbps);
            List<Integer> hist = new ArrayList<>(device.getBandwidthHistory());
            hist.add(kbps);
            if (hist.size() > 20) hist.remove(0);
            device.setBandwidthHistory(hist);
            store.putDevice(device);
            changed = true;
        }
        byteCounters.clear(); // discard traffic for unknown IPs
        if (changed) broadcaster.broadcastFullState();
    }

    @Override
    public void destroy() {
        if (handle != null && handle.isOpen()) {
            try { handle.breakLoop(); } catch (Exception ignored) {}
            handle.close();
        }
    }
}
