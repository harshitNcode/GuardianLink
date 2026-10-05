import json
import logging
from typing import Dict, List
from fastapi import WebSocket

logger = logging.getLogger("GuardianLink.WebSocket")


class ConnectionManager:
    def __init__(self):
        # Maps device_id -> List[WebSocket]
        self.active_connections: Dict[str, List[WebSocket]] = {}

    async def connect(self, websocket: WebSocket, device_id: str):
        await websocket.accept()
        if device_id not in self.active_connections:
            self.active_connections[device_id] = []
        self.active_connections[device_id].append(websocket)
        logger.info(f"Client connected to device '{device_id}'. Total listeners: {len(self.active_connections[device_id])}")

    def disconnect(self, websocket: WebSocket, device_id: str):
        if device_id in self.active_connections:
            if websocket in self.active_connections[device_id]:
                self.active_connections[device_id].remove(websocket)
            if not self.active_connections[device_id]:
                del self.active_connections[device_id]
        logger.info(f"Client disconnected from device '{device_id}'.")

    async def broadcast_to_device(self, device_id: str, message: dict):
        """Broadcast live telemetry / device status updates to all listeners for this device."""
        if device_id not in self.active_connections:
            return

        payload = json.dumps(message)
        dead_connections = []
        for connection in self.active_connections[device_id]:
            try:
                await connection.send_text(payload)
            except Exception as e:
                logger.warning(f"Failed to send to client for device {device_id}: {e}")
                dead_connections.append(connection)

        for dead in dead_connections:
            self.disconnect(dead, device_id)


manager = ConnectionManager()
