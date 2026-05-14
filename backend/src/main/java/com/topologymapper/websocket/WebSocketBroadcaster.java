package com.topologymapper.websocket;

import com.topologymapper.model.Device;
import com.topologymapper.store.TopologyStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WebSocketBroadcaster {

    @Autowired
    private TopologyWebSocketHandler handler;

    @Autowired
    private TopologyStore store;

    public void broadcastFullState() {
        handler.broadcastToAll(Map.of(
                "type", "FULL_STATE",
                "devices", store.allDevices(),
                "links", store.allLinks()
        ));
    }

    public void broadcastScanResult(List<Device> newDevices, List<String> lostIds) {
        handler.broadcastToAll(Map.of(
                "type", "SCAN_RESULT",
                "newDevices", newDevices,
                "lostDevices", lostIds
        ));
    }

    public void broadcastRaw(Object payload) {
        handler.broadcastToAll(payload);
    }
}
