#!/usr/bin/env python3
import http.server
import socketserver
import json
import os
import subprocess
import time

PORT = 9100

_last_cpu_time = 0.0
_last_idle_time = 0.0

def get_cpu_utilization():
    global _last_cpu_time, _last_idle_time
    try:
        with open("/proc/stat") as f:
            first_line = f.readline()
        parts = first_line.split()[1:]
        values = [float(x) for x in parts]
        idle_time = values[3] + (values[4] if len(values) > 4 else 0.0)
        total_time = sum(values)

        if _last_cpu_time == 0.0 or total_time <= _last_cpu_time:
            time.sleep(0.08)
            with open("/proc/stat") as f:
                second_line = f.readline()
            parts2 = second_line.split()[1:]
            values2 = [float(x) for x in parts2]
            idle_time2 = values2[3] + (values2[4] if len(values2) > 4 else 0.0)
            total_time2 = sum(values2)
            delta_total = total_time2 - total_time
            delta_idle = idle_time2 - idle_time
            _last_cpu_time = total_time2
            _last_idle_time = idle_time2
        else:
            delta_total = total_time - _last_cpu_time
            delta_idle = idle_time - _last_idle_time
            _last_cpu_time = total_time
            _last_idle_time = idle_time

        if delta_total > 0:
            usage = ((delta_total - delta_idle) / delta_total) * 100.0
            return max(0.0, min(100.0, round(usage, 1)))
        return 0.0
    except Exception:
        return 0.0

def get_cpu_info():
    try:
        with open("/proc/loadavg") as f:
            loads = f.read().strip().split()
        cores = os.cpu_count() or 1
        load_1 = float(loads[0])
        load_5 = float(loads[1])
        load_15 = float(loads[2])
        cpu_pct = get_cpu_utilization()
        return {
            "cores": cores,
            "loadAvg": [load_1, load_5, load_15],
            "loadPercent": cpu_pct
        }
    except Exception as e:
        return {"cores": 1, "loadAvg": [0, 0, 0], "loadPercent": 0, "error": str(e)}

def get_mem_info():
    try:
        mem = {}
        with open("/proc/meminfo") as f:
            for line in f:
                parts = line.split(":")
                if len(parts) == 2:
                    key = parts[0].strip()
                    val = int(parts[1].strip().split()[0]) * 1024
                    mem[key] = val
        total = mem.get("MemTotal", 1)
        avail = mem.get("MemAvailable", mem.get("MemFree", 0))
        used = total - avail
        pct = round((used / total) * 100, 1)
        return {
            "totalBytes": total,
            "usedBytes": used,
            "freeBytes": avail,
            "usedPercent": pct
        }
    except Exception as e:
        return {"totalBytes": 0, "usedBytes": 0, "freeBytes": 0, "usedPercent": 0, "error": str(e)}

def get_disk_info():
    try:
        st = os.statvfs('/')
        total = st.f_blocks * st.f_frsize
        free = st.f_bavail * st.f_frsize
        used = total - free
        pct = round((used / total) * 100, 1)
        return {
            "totalBytes": total,
            "usedBytes": used,
            "availBytes": free,
            "usedPercent": pct
        }
    except Exception as e:
        return {"totalBytes": 0, "usedBytes": 0, "availBytes": 0, "usedPercent": 0, "error": str(e)}

def get_uptime():
    try:
        with open("/proc/uptime") as f:
            return int(float(f.read().split()[0]))
    except Exception:
        return 0

def get_services():
    services = ["cockpit.socket", "ssh"]
    res = []
    for s in services:
        try:
            status = subprocess.check_output(["systemctl", "is-active", s], text=True).strip()
        except subprocess.CalledProcessError as e:
            status = e.output.strip() if e.output else "inactive"
        except Exception:
            status = "unknown"
        res.append({"name": s, "status": status})
    return res

class SlaveHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ['/stats', '/health', '/metrics', '/']:
            data = {
                "node": "container-of-souls",
                "role": "Slave (Compute & Workers)",
                "ip": "10.0.0.195",
                "status": "online",
                "os": "Ubuntu 26.04 LTS",
                "uptime": get_uptime(),
                "cpu": get_cpu_info(),
                "memory": get_mem_info(),
                "disk": get_disk_info(),
                "services": get_services(),
                "timestamp": int(time.time())
            }
            body = json.dumps(data).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass

if __name__ == '__main__':
    with socketserver.TCPServer(("0.0.0.0", PORT), SlaveHandler) as httpd:
        httpd.serve_forever()
