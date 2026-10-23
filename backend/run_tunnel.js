const localtunnel = require('C:/Users/Harshit/AppData/Roaming/npm/node_modules/localtunnel');

let currentTunnel = null;

async function startTunnel() {
  try {
    if (currentTunnel) {
      try { currentTunnel.close(); } catch (e) {}
      currentTunnel = null;
    }

    console.log('[Tunnel] Connecting to loca.lt with subdomain: guardianlink-harshit ...');
    const tunnel = await localtunnel({
      port: 8000,
      subdomain: 'guardianlink-harshit',
      host: 'https://loca.lt'
    });

    currentTunnel = tunnel;
    console.log('[Tunnel] READY! Public URL:', tunnel.url);

    tunnel.on('close', () => {
      console.log('[Tunnel] Connection closed by loca.lt. Auto-reconnecting in 3 seconds...');
      setTimeout(startTunnel, 3000);
    });

    tunnel.on('error', (err) => {
      console.error('[Tunnel] Error:', err.message);
      try { tunnel.close(); } catch (e) {}
    });
  } catch (err) {
    console.error('[Tunnel] Failed to establish tunnel:', err.message, '- Retrying in 5s...');
    setTimeout(startTunnel, 5000);
  }
}

// Keep Node event loop alive indefinitely so process never terminates
setInterval(() => {
  // Heartbeat ping
}, 15000);

process.on('uncaughtException', (err) => {
  console.error('[Tunnel] Uncaught exception:', err.message);
  setTimeout(startTunnel, 3000);
});

startTunnel();
