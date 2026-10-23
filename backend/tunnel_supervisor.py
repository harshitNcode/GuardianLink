import subprocess, time, sys, urllib.request, os

SUBDOMAIN = "guardianlink-harshit"
LT_PATH = r"C:\Users\Harshit\AppData\Roaming\npm\node_modules\localtunnel\bin\lt.js"
LOG_FILE = r"C:\Users\Harshit\AndroidStudioProjects\GuardianLink\backend\lt_live.log"

def kill_node_processes():
    try:
        subprocess.run('powershell -Command "Get-Process node -ErrorAction SilentlyContinue | Stop-Process -Force"', shell=True, capture_output=True)
    except Exception:
        pass

def check_tunnel_healthy():
    try:
        req = urllib.request.Request(
            f"https://{SUBDOMAIN}.loca.lt/health",
            headers={"Bypass-Tunnel-Reminder": "true", "User-Agent": "ESP32"}
        )
        with urllib.request.urlopen(req, timeout=8) as resp:
            return resp.status == 200
    except Exception:
        return False

print(f"[Tunnel Supervisor] Starting robust watchdog for '{SUBDOMAIN}'...", flush=True)

while True:
    try:
        kill_node_processes()
        time.sleep(1)

        # Clear log file
        with open(LOG_FILE, "w") as f:
            f.write("")

        # Launch localtunnel directing stdout and stderr to the log file (never blocks Python!)
        with open(LOG_FILE, "w") as out:
            proc = subprocess.Popen(
                ["node", LT_PATH, "--port", "8000", "--subdomain", SUBDOMAIN, "--host", "https://loca.lt"],
                stdout=out,
                stderr=out
            )

        got_domain = False
        start_time = time.time()
        while time.time() - start_time < 18:
            time.sleep(1)
            if os.path.exists(LOG_FILE):
                try:
                    with open(LOG_FILE, "r") as f:
                        content = f.read()
                    if "your url is:" in content:
                        if SUBDOMAIN in content:
                            got_domain = True
                            print(f"[Tunnel Supervisor] Dedicated domain '{SUBDOMAIN}.loca.lt' is CONNECTED!", flush=True)
                            break
                        else:
                            print(f"[Tunnel Supervisor] Got random subdomain. Retrying...", flush=True)
                            break
                except Exception:
                    pass

        if not got_domain:
            try:
                proc.kill()
            except Exception:
                pass
            kill_node_processes()
            time.sleep(2)
            continue

        # Keep watchdog active
        consecutive_failures = 0
        while proc.poll() is None:
            time.sleep(10)
            if check_tunnel_healthy():
                consecutive_failures = 0
            else:
                consecutive_failures += 1
                print(f"[Tunnel Supervisor] Health check failure ({consecutive_failures}/4)...", flush=True)
                if consecutive_failures >= 4:
                    print("[Tunnel Supervisor] Tunnel drop detected. Auto-restarting...", flush=True)
                    try:
                        proc.kill()
                    except Exception:
                        pass
                    break

        kill_node_processes()
        time.sleep(2)
    except Exception as e:
        print(f"[Tunnel Supervisor] Error: {e}. Retrying in 2s...", flush=True)
        kill_node_processes()
        time.sleep(2)
