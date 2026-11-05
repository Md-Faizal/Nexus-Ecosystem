use std::fs;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};

fn get_uptime() -> u64 {
    fs::read_to_string("/proc/uptime")
        .ok()
        .and_then(|s| s.split_whitespace().next().and_then(|x| x.parse::<f64>().ok()))
        .map(|f| f as u64)
        .unwrap_or(0)
}

use std::sync::atomic::{AtomicU64, Ordering};

static LAST_TOTAL_JIFFIES: AtomicU64 = AtomicU64::new(0);
static LAST_IDLE_JIFFIES: AtomicU64 = AtomicU64::new(0);

fn read_proc_stat_jiffies() -> Option<(u64, u64)> {
    let stat = fs::read_to_string("/proc/stat").ok()?;
    let first_line = stat.lines().next()?;
    if !first_line.starts_with("cpu ") {
        return None;
    }
    let parts: Vec<u64> = first_line
        .split_whitespace()
        .skip(1)
        .filter_map(|s| s.parse::<u64>().ok())
        .collect();
    if parts.len() < 5 {
        return None;
    }
    let idle = parts[3] + parts.get(4).copied().unwrap_or(0);
    let total: u64 = parts.iter().sum();
    Some((total, idle))
}

fn get_cpu_utilization() -> f64 {
    if let Some((total, idle)) = read_proc_stat_jiffies() {
        let prev_total = LAST_TOTAL_JIFFIES.swap(total, Ordering::Relaxed);
        let prev_idle = LAST_IDLE_JIFFIES.swap(idle, Ordering::Relaxed);

        if prev_total > 0 && total > prev_total {
            let total_diff = total - prev_total;
            let idle_diff = idle.saturating_sub(prev_idle);
            if total_diff > 0 {
                let active = total_diff.saturating_sub(idle_diff);
                return ((active as f64 / total_diff as f64) * 100.0).clamp(0.0, 100.0);
            }
        } else {
            std::thread::sleep(std::time::Duration::from_millis(80));
            if let Some((total2, idle2)) = read_proc_stat_jiffies() {
                LAST_TOTAL_JIFFIES.store(total2, Ordering::Relaxed);
                LAST_IDLE_JIFFIES.store(idle2, Ordering::Relaxed);
                let total_diff = total2.saturating_sub(total);
                let idle_diff = idle2.saturating_sub(idle);
                if total_diff > 0 {
                    let active = total_diff.saturating_sub(idle_diff);
                    return ((active as f64 / total_diff as f64) * 100.0).clamp(0.0, 100.0);
                }
            }
        }
    }
    0.0
}

fn get_cpu_info() -> (usize, f64, f64, f64, f64) {
    let cores = fs::read_to_string("/proc/cpuinfo")
        .map(|s| s.lines().filter(|l| l.starts_with("processor")).count())
        .unwrap_or(1)
        .max(1);

    let (l1, l5, l15) = fs::read_to_string("/proc/loadavg")
        .ok()
        .map(|s| {
            let mut parts = s.split_whitespace();
            let p1 = parts.next().and_then(|x| x.parse::<f64>().ok()).unwrap_or(0.0);
            let p2 = parts.next().and_then(|x| x.parse::<f64>().ok()).unwrap_or(0.0);
            let p3 = parts.next().and_then(|x| x.parse::<f64>().ok()).unwrap_or(0.0);
            (p1, p2, p3)
        })
        .unwrap_or((0.0, 0.0, 0.0));

    let pct = get_cpu_utilization();
    (cores, l1, l5, l15, pct)
}

fn get_mem_info() -> (u64, u64, u64, f64) {
    let content = fs::read_to_string("/proc/meminfo").unwrap_or_default();
    let mut total: u64 = 0;
    let mut free: u64 = 0;
    let mut avail: u64 = 0;

    for line in content.lines() {
        if line.starts_with("MemTotal:") {
            total = parse_mem_kb(line);
        } else if line.starts_with("MemFree:") {
            free = parse_mem_kb(line);
        } else if line.starts_with("MemAvailable:") {
            avail = parse_mem_kb(line);
        }
    }

    let effective_free = if avail > 0 { avail } else { free };
    let used = total.saturating_sub(effective_free);
    let pct = if total > 0 {
        ((used as f64 / total as f64) * 100.0).clamp(0.0, 100.0)
    } else {
        0.0
    };

    (total, used, effective_free, pct)
}

fn parse_mem_kb(line: &str) -> u64 {
    line.split_whitespace()
        .nth(1)
        .and_then(|s| s.parse::<u64>().ok())
        .map(|kb| kb * 1024)
        .unwrap_or(0)
}

fn get_disk_info() -> (u64, u64, u64, f64) {
    if let Ok(output) = Command::new("df").args(["-B1", "/"]).output() {
        if let Ok(text) = String::from_utf8(output.stdout) {
            if let Some(line) = text.lines().nth(1) {
                let parts: Vec<&str> = line.split_whitespace().collect();
                if parts.len() >= 4 {
                    let total = parts[1].parse::<u64>().unwrap_or(0);
                    let used = parts[2].parse::<u64>().unwrap_or(0);
                    let avail = parts[3].parse::<u64>().unwrap_or(0);
                    let pct = if total > 0 {
                        ((used as f64 / total as f64) * 100.0).clamp(0.0, 100.0)
                    } else {
                        0.0
                    };
                    return (total, used, avail, pct);
                }
            }
        }
    }
    (0, 0, 0, 0.0)
}

fn get_service_status(name: &str) -> String {
    Command::new("systemctl")
        .args(["is-active", name])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_else(|_| "unknown".into())
}

fn handle_client(mut stream: TcpStream) {
    let mut buffer = [0; 1024];
    let _ = stream.read(&mut buffer);

    let (cores, l1, l5, l15, cpu_pct) = get_cpu_info();
    let (mem_total, mem_used, mem_free, mem_pct) = get_mem_info();
    let (disk_total, disk_used, disk_avail, disk_pct) = get_disk_info();
    let uptime = get_uptime();
    let now = SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);

    let cockpit_status = get_service_status("cockpit.socket");
    let ssh_status = get_service_status("ssh");

    let body = format!(
        r#"{{"node":"container-of-souls","role":"Slave (Compute & Workers)","ip":"10.0.0.195","status":"online","os":"Ubuntu 26.04 LTS","uptime":{},"cpu":{{"cores":{},"loadAvg":[{:.2},{:.2},{:.2}],"loadPercent":{:.1}}},"memory":{{"totalBytes":{},"usedBytes":{},"freeBytes":{},"usedPercent":{:.1}}},"disk":{{"totalBytes":{},"usedBytes":{},"availBytes":{},"usedPercent":{:.1}}},"services":[{{"name":"cockpit.socket","status":"{}"}},{{"name":"ssh","status":"{}"}}],"timestamp":{}}}"#,
        uptime, cores, l1, l5, l15, cpu_pct,
        mem_total, mem_used, mem_free, mem_pct,
        disk_total, disk_used, disk_avail, disk_pct,
        cockpit_status, ssh_status, now
    );

    let response = format!(
        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
        body.len(),
        body
    );

    let _ = stream.write_all(response.as_bytes());
}

fn main() {
    let listener = TcpListener::bind("0.0.0.0:9100").expect("Failed to bind to 0.0.0.0:9100");
    println!("Rust Cluster Slave Agent running on port 9100");

    for stream in listener.incoming() {
        if let Ok(stream) = stream {
            handle_client(stream);
        }
    }
}
