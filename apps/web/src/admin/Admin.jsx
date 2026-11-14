import React, { useState, useEffect, useCallback } from 'react';
import AdminLayout from './AdminLayout';
import ClusterTab from './tabs/ClusterTab';
import ServicesTab from './tabs/ServicesTab';
import AiStudioTab from './tabs/AiStudioTab';
import ReportsTab from './tabs/ReportsTab';
import MessagesTab from './tabs/MessagesTab';
import PortfolioTab from './tabs/PortfolioTab';
import ProfileTab from './tabs/ProfileTab';
import AnalyticsTab from './tabs/AnalyticsTab';
import SecurityTab from './tabs/SecurityTab';
import SettingsTab from './tabs/SettingsTab';
import ToastNotification from './components/ToastNotification';
import ReportPreviewModal from './components/ReportPreviewModal';

// Exact vector geometry extracted from Resources/Monogram/SVG/1.Complete.svg
export const MONOGRAM_PATHS = {
  wingDown: "M43.82,70.3l64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43,27.69.85-25.84,61.15c-2.63,6.23-11.43,8.64-18.18,8.65l-111.12.13,41.64-20.2Z",
  wingUp: "M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6-28.38-.02L41.22,9.13C43.67,3.32,52.08.55,58.48.55l110.96-.05-39.82,19.21Z",
  centerPoly: "44.55 69.8 51.04 53.07 93.98 33.19 59.67 32.61 64.74 20.95 128.88 20.22 121.68 37.77 78.77 57.7 113 58.08 108.01 69.85 44.55 69.8",
  centerWhite: "M128.13,20.73l-6.84,16.67-40.73,18.92-4,1.86,4.41.05,31.28.34-4.57,10.78-62.4-.05,6.16-15.86,40.77-18.88,3.96-1.83-4.36-.07-31.37-.53,4.64-10.68,63.06-.71M129.63,19.71l-65.22.74-5.49,12.65,32.87.55-41.14,19.05-6.83,17.6,64.52.05,5.42-12.76-32.78-.36,41.09-19.08,7.56-18.43h0Z"
};

export default function Admin({ onExit }) {
  const [activeTab, setActiveTab] = useState('cluster');
  const [refreshing, setRefreshing] = useState(false);
  const [toast, setToast] = useState(null);

  // Modals & Preview
  const [isReportModalOpen, setIsReportModalOpen] = useState(false);
  const [reportHtml, setReportHtml] = useState('');
  const [loadingReportPreview, setLoadingReportPreview] = useState(false);
  const [sendingReport, setSendingReport] = useState(false);

  // Settings & Theme
  const [siteTheme, setSiteTheme] = useState('classic');

  // Cluster State
  const [cluster, setCluster] = useState({
    primary: {
      node: 'Primary Node',
      role: 'Primary Core (Ingress & State)',
      ip: '10.0.0.86',
      status: 'online',
      os: 'Ubuntu 24.04 LTS',
      uptime: 0,
      cpu: { cores: 1, loadPercent: 12, loadAvg: [0.15, 0.18, 0.22] },
      memory: { totalBytes: 997474304, usedBytes: 420000000, freeBytes: 577474304, usedPercent: 42 },
      disk: { totalBytes: 50839412736, usedBytes: 18253611008, availBytes: 32585801728, usedPercent: 36 }
    },
    backup: {
      node: 'Backup Node',
      role: 'Standby & Compute Worker',
      ip: '10.0.0.195',
      status: 'online',
      os: 'Ubuntu 26.04 LTS',
      uptime: 0,
      cpu: { cores: 2, loadPercent: 6, loadAvg: [0.12, 0.09, 0.08] },
      memory: { totalBytes: 997474304, usedBytes: 342765568, freeBytes: 654708736, usedPercent: 34 },
      disk: { totalBytes: 50839412736, usedBytes: 2298728448, availBytes: 48540684288, usedPercent: 5 }
    }
  });

  // Services State
  const [services, setServices] = useState([
    {
      id: 'personal-site-rust',
      name: 'Faizal.uk Web Engine (Rust)',
      node: 'both',
      port: 3002,
      status: 'running',
      type: 'docker',
      serving_state: 'both',
      traffic_route: 'Actively Serving: my-mac (:3002) • Standby: container-of-souls (:3002)',
      cpu_usage: '0.3% avg',
      ram_usage: '24 MB',
      storage_usage: '62 MB'
    },
    {
      id: 'cockpit',
      name: 'Cockpit Multi-Host Bridge',
      node: 'all',
      port: 9090,
      status: 'running',
      type: 'systemctl',
      serving_state: 'cluster',
      traffic_route: 'Tri-Cluster Console: my-mac, container-of-souls & prometheus (:9090)',
      cpu_usage: '0.3% avg',
      ram_usage: '32 MB',
      storage_usage: '85 MB'
    },
    {
      id: 'nginx',
      name: 'Nginx SSL Reverse Proxy',
      node: 'my-mac',
      port: 80,
      status: 'running',
      type: 'systemctl',
      serving_state: 'active',
      traffic_route: 'Public Edge Ingress (Ports 80 & 443 SSL) on my-mac',
      cpu_usage: '0.4% avg',
      ram_usage: '18 MB',
      storage_usage: '45 MB'
    },
    {
      id: 'macos-server-blue',
      name: 'MacOS Server Web Desktop (Blue)',
      node: 'my-mac',
      port: 3001,
      status: 'running',
      type: 'docker',
      serving_state: 'active',
      traffic_route: 'Actively Serving: mac.faizal.uk -> my-mac (:3001 Active Slot)',
      cpu_usage: '2.4% avg',
      ram_usage: '180 MB',
      storage_usage: '520 MB'
    },
    {
      id: 'stalwart-mail',
      name: 'Stalwart Mail & JMAP Server',
      node: 'my-mac',
      port: 25,
      status: 'running',
      type: 'docker',
      serving_state: 'active',
      traffic_route: 'Actively Serving: Primary Mail Host (MX & Webmail :8080)',
      cpu_usage: '1.2% avg',
      ram_usage: '95 MB',
      storage_usage: '280 MB'
    },
    {
      id: 'cs-panel',
      name: 'Counter-Strike 1.6 Panel',
      node: 'my-mac',
      port: 3005,
      status: 'stopped',
      type: 'systemctl',
      serving_state: 'idle',
      traffic_route: 'cs.faizal.uk (Port 3005) - On-Demand Game Engine',
      cpu_usage: '0.0%',
      ram_usage: '0 MB',
      storage_usage: '1.1 GB'
    },
    {
      id: 'nexus-backend',
      name: 'Nexus Backend API',
      node: 'my-mac',
      port: 3010,
      status: 'stopped',
      type: 'systemctl',
      serving_state: 'idle',
      traffic_route: 'Internal App API (Port 3010) on my-mac',
      cpu_usage: '0.0%',
      ram_usage: '0 MB',
      storage_usage: '110 MB'
    },
    {
      id: 'bloomberg-terminal',
      name: 'Bloomberg Market Terminal',
      node: 'container-of-souls',
      port: 443,
      status: 'stopped',
      type: 'docker',
      serving_state: 'idle',
      traffic_route: 'bloom.faizal.uk on container-of-souls',
      cpu_usage: '0.0%',
      ram_usage: '0 MB',
      storage_usage: '340 MB'
    },
    {
      id: 'macos-server-green',
      name: 'MacOS Server Web Desktop (Green Standby)',
      node: 'container-of-souls',
      port: 3004,
      status: 'running',
      type: 'docker',
      serving_state: 'standby',
      traffic_route: 'Hot Standby Slot: container-of-souls (:3004 Failover Target)',
      cpu_usage: '0.5% avg',
      ram_usage: '140 MB',
      storage_usage: '520 MB'
    },
    {
      id: 'stalwart-mail-standby',
      name: 'Stalwart Mail (Standby Replica)',
      node: 'container-of-souls',
      port: 8080,
      status: 'running',
      type: 'docker',
      serving_state: 'standby',
      traffic_route: 'Hot Standby Slot: container-of-souls (Mail Failover & VCN Sync)',
      cpu_usage: '0.4% avg',
      ram_usage: '65 MB',
      storage_usage: '280 MB'
    },
    {
      id: 'mc-manager',
      name: 'Minecraft Bedrock Manager',
      node: 'container-of-souls',
      port: 5000,
      status: 'stopped',
      type: 'docker',
      serving_state: 'idle',
      traffic_route: 'mine.faizal.uk (Port 5000 / 19132) on container-of-souls',
      cpu_usage: '0.0%',
      ram_usage: '0 MB',
      storage_usage: '1.8 GB'
    }
  ]);

  // Messages, Projects, Profile, Security
  const [messages, setMessages] = useState([]);
  const [projects, setProjects] = useState([]);
  const [profileHistory, setProfileHistory] = useState([]);
  const [accessLogs, setAccessLogs] = useState([]);
  const [bannedIps, setBannedIps] = useState([]);
  const [reportConfig, setReportConfig] = useState({
    recipient: 'me@faizal.uk, fazztronic@gmail.com',
    dispatchTime: '08:00 UTC'
  });
  const [reportErrors, setReportErrors] = useState({
    total_error_count: 0,
    ssh_auth_failures: 0,
    systemd: [],
    nginx: []
  });
  const [analytics, setAnalytics] = useState({ totalViews: 0, uniqueVisitors: 0, recentVisitors: [] });

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => setToast(null), 3500);
  };

  const getAuthToken = () => {
    return localStorage.getItem('nexus_auth_token') || localStorage.getItem('faizal_admin_pin') || '2004';
  };

  const fetchWithAuth = useCallback(async (url, options = {}) => {
    const token = getAuthToken();
    const headers = {
      'Authorization': (token.startsWith('eyJ') || token.startsWith('nex_live_')) ? `Bearer ${token}` : token,
      ...(options.headers || {})
    };
    return fetch(url, { ...options, headers });
  }, []);

  // Fetch Telemetry & Database
  const fetchAllData = useCallback(async () => {
    try {
      setRefreshing(true);

      // System Stats
      try {
        const res = await fetchWithAuth('/api/system-stats');
        if (res.ok) {
          const json = await res.json();
          if (json.success && json.stats) {
            if (json.stats.analytics) setAnalytics(json.stats.analytics);
            if (json.stats.cluster) {
              setCluster(json.stats.cluster);
            } else if (json.stats.os) {
              const os = json.stats.os;
              setCluster((prev) => ({
                ...prev,
                master: {
                  ...prev.master,
                  uptime: os.uptime || 0,
                  cpu: { cores: os.cpus || 1, loadPercent: os.cpuLoad || 0, loadAvg: os.loadAvg || [0, 0, 0] },
                  memory: {
                    totalBytes: os.totalMem || 997474304,
                    usedBytes: os.usedMem || 400000000,
                    freeBytes: os.freeMem || 500000000,
                    usedPercent: Math.round(((os.usedMem || 400) / (os.totalMem || 997)) * 100)
                  },
                  disk: {
                    totalBytes: os.diskTotal || 50839412736,
                    usedBytes: os.diskUsed || 18253611008,
                    availBytes: os.diskAvail || 32585801728,
                    usedPercent: os.diskUsedPct || 36
                  }
                }
              }));
            }
          }
        }
      } catch (e) {}

      // Services
      try {
        const sRes = await fetchWithAuth('/api/services');
        if (sRes.ok) {
          const sJson = await sRes.json();
          if (sJson.services) {
            const normalized = sJson.services.map((s) => ({
              ...s,
              type: s.type || s.service_type || (s.control_type?.includes('docker') ? 'docker' : s.control_type) || 'systemctl'
            }));
            setServices(normalized);
          }
        }
      } catch (e) {}

      // Messages
      try {
        const mRes = await fetchWithAuth('/api/messages');
        if (mRes.ok) {
          const mJson = await mRes.json();
          if (Array.isArray(mJson)) setMessages(mJson);
        }
      } catch (e) {}

      // Projects
      try {
        const pRes = await fetch('/api/projects');
        if (pRes.ok) {
          const pJson = await pRes.json();
          if (Array.isArray(pJson)) setProjects(pJson);
        }
      } catch (e) {}

      // Profile History
      try {
        const profRes = await fetchWithAuth('/api/profile-history');
        if (profRes.ok) {
          const profJson = await profRes.json();
          if (Array.isArray(profJson)) setProfileHistory(profJson);
        }
      } catch (e) {}

      // Theme Config
      try {
        const tRes = await fetch('/api/theme-config');
        if (tRes.ok) {
          const tJson = await tRes.json();
          if (tJson.theme) setSiteTheme(tJson.theme);
        }
      } catch (e) {}

      // Security Logs & Bans
      try {
        const aRes = await fetchWithAuth('/api/access-logs');
        if (aRes.ok) {
          const aJson = await aRes.json();
          if (Array.isArray(aJson)) setAccessLogs(aJson);
        }
        const bRes = await fetchWithAuth('/api/banned-ips');
        if (bRes.ok) {
          const bJson = await bRes.json();
          if (Array.isArray(bJson)) setBannedIps(bJson);
        }
      } catch (e) {}

      // Health Errors
      try {
        const errRes = await fetchWithAuth('/api/health-report/errors');
        if (errRes.ok) {
          const errJson = await errRes.json();
          if (errJson) setReportErrors(errJson);
        }
      } catch (e) {}

    } catch (err) {
      console.error('Data refresh error:', err);
    } finally {
      setRefreshing(false);
    }
  }, [fetchWithAuth]);

  useEffect(() => {
    fetchAllData();
    const interval = setInterval(fetchAllData, 12000);
    return () => clearInterval(interval);
  }, [fetchAllData]);

  // Service Control Action
  const handleServiceAction = async (serviceId, action) => {
    try {
      showToast(`Executing ${action.toUpperCase()} on ${serviceId}...`, 'info');
      const res = await fetchWithAuth(`/api/services/${serviceId}/action`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action })
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
        showToast(data.message || `Service ${serviceId} ${action} command confirmed`);
        setTimeout(fetchAllData, 1500);
      } else {
        showToast(data.error || `Failed to ${action} ${serviceId}`, 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Open Health Report Live Preview
  const handleOpenReportPreview = async () => {
    setIsReportModalOpen(true);
    setLoadingReportPreview(true);
    try {
      const res = await fetchWithAuth('/api/health-report/preview');
      if (res.ok) {
        const html = await res.text();
        setReportHtml(html);
      } else {
        setReportHtml('<div style="color:#ff3366;padding:40px;font-family:monospace;text-align:center;">Failed to generate report preview. Check server daemon.</div>');
      }
    } catch (err) {
      setReportHtml(`<div style="color:#ff3366;padding:40px;font-family:monospace;text-align:center;">Network error: ${err.message}</div>`);
    } finally {
      setLoadingReportPreview(false);
    }
  };

  // Dispatch Daily Health Report Email Now
  const handleDispatchReport = async () => {
    setSendingReport(true);
    try {
      const res = await fetchWithAuth('/api/health-report/send', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ recipient: reportConfig.recipient })
      });
      if (res.ok) {
        showToast('Daily System Report dispatched via Brevo Relay (Port 587)!', 'success');
      } else {
        showToast('Failed to dispatch report. Verify Brevo credentials.', 'error');
      }
    } catch (err) {
      showToast(`Dispatch failed: ${err.message}`, 'error');
    } finally {
      setSendingReport(false);
    }
  };

  // Update Report Configuration
  const handleUpdateReportConfig = async (newConfig) => {
    try {
      const res = await fetchWithAuth('/api/health-report/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newConfig)
      });
      if (res.ok) {
        setReportConfig(newConfig);
        showToast('Supervision schedule updated');
      } else {
        showToast('Failed to save configuration', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Delete Contact Message
  const handleDeleteMessage = async (id) => {
    try {
      const res = await fetchWithAuth(`/api/messages/${id}`, { method: 'DELETE' });
      if (res.ok) {
        showToast('Message deleted');
        setMessages((prev) => prev.filter((m) => m.id !== id));
      } else {
        showToast('Failed to delete message', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Add Project
  const handleAddProject = async (projectData) => {
    try {
      const res = await fetchWithAuth('/api/projects', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(projectData)
      });
      if (res.ok) {
        showToast('Project published to portfolio');
        fetchAllData();
      } else {
        showToast('Failed to create project', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Delete Project
  const handleDeleteProject = async (id) => {
    try {
      const res = await fetchWithAuth(`/api/projects/${id}`, { method: 'DELETE' });
      if (res.ok) {
        showToast('Project removed');
        setProjects((prev) => prev.filter((p) => p.id !== id));
      } else {
        showToast('Failed to delete project', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Profile Upload & Select
  const handleUploadPic = async (formData) => {
    try {
      const res = await fetchWithAuth('/api/upload-picture', {
        method: 'POST',
        body: formData
      });
      if (res.ok) {
        showToast('Avatar portrait uploaded and set active');
        fetchAllData();
      } else {
        showToast('Failed to upload image', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  const handleSelectPic = async (id) => {
    try {
      const res = await fetchWithAuth(`/api/select-profile-pic/${id}`, { method: 'POST' });
      if (res.ok) {
        showToast('Active avatar updated');
        fetchAllData();
      } else {
        showToast('Failed to activate picture', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  const handleDeletePic = async (id) => {
    try {
      const res = await fetchWithAuth(`/api/profile-history/${id}`, { method: 'DELETE' });
      if (res.ok) {
        showToast('Photo removed from vault');
        setProfileHistory((prev) => prev.filter((p) => p.id !== id));
      } else {
        showToast('Failed to delete photo', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Ban & Unban IP
  const handleBanIp = async (banData) => {
    try {
      const res = await fetchWithAuth('/api/ban-ip', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(banData)
      });
      if (res.ok) {
        showToast(`IP ${banData.ip} blacklisted`);
        fetchAllData();
      } else {
        showToast('Failed to ban IP', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  const handleUnbanIp = async (ip) => {
    try {
      const res = await fetchWithAuth(`/api/ban-ip/${ip}`, { method: 'DELETE' });
      if (res.ok) {
        showToast(`IP ${ip} unbanned`);
        fetchAllData();
      } else {
        showToast('Failed to unban IP', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  // Switch Theme
  const handleThemeChange = async (theme) => {
    setSiteTheme(theme);
    try {
      await fetchWithAuth('/api/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key: 'theme', value: theme })
      });
      showToast(`Site theme configured to ${theme.toUpperCase()}`);
    } catch (err) {
      showToast('Failed to persist theme setting', 'error');
    }
  };

  // Send Test Relay Email
  const handleSendTestEmail = async (toEmail) => {
    try {
      const res = await fetchWithAuth('/api/test-email', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ to: toEmail })
      });
      if (res.ok) {
        showToast(`Test probe dispatched to ${toEmail}`);
      } else {
        showToast('Failed to send test email. Check relay credentials.', 'error');
      }
    } catch (err) {
      showToast(`Error: ${err.message}`, 'error');
    }
  };

  return (
    <AdminLayout
      activeTab={activeTab}
      onTabChange={setActiveTab}
      onExit={onExit}
      onRefresh={fetchAllData}
      refreshing={refreshing}
      onOpenReportPreview={handleOpenReportPreview}
      inboxCount={messages.length}
      projectCount={projects.length}
    >
      <ToastNotification toast={toast} onClose={() => setToast(null)} />

      {/* Render Active Tab */}
      {activeTab === 'cluster' && (
        <ClusterTab
          cluster={cluster}
          onOpenReportPreview={handleOpenReportPreview}
        />
      )}

      {activeTab === 'services' && (
        <ServicesTab
          services={services}
          cluster={cluster}
          onServiceAction={handleServiceAction}
          onRefresh={fetchAllData}
          refreshing={refreshing}
          fetchWithAuth={fetchWithAuth}
          showToast={showToast}
        />
      )}

      {activeTab === 'ai-studio' && (
        <AiStudioTab onToast={showToast} />
      )}

      {activeTab === 'reports' && (
        <ReportsTab
          reportConfig={reportConfig}
          onUpdateConfig={handleUpdateReportConfig}
          onOpenPreview={handleOpenReportPreview}
          onDispatchReport={handleDispatchReport}
          sendingReport={sendingReport}
          errors={reportErrors}
        />
      )}

      {activeTab === 'messages' && (
        <MessagesTab
          messages={messages}
          onDeleteMessage={handleDeleteMessage}
          onRefresh={fetchAllData}
          refreshing={refreshing}
        />
      )}

      {activeTab === 'portfolio' && (
        <PortfolioTab
          projects={projects}
          onAddProject={handleAddProject}
          onDeleteProject={handleDeleteProject}
        />
      )}

      {activeTab === 'profile' && (
        <ProfileTab
          profileHistory={profileHistory}
          onUploadPic={handleUploadPic}
          onSelectPic={handleSelectPic}
          onDeletePic={handleDeletePic}
          refreshing={refreshing}
        />
      )}

      {activeTab === 'analytics' && (
        <AnalyticsTab analytics={analytics} />
      )}

      {activeTab === 'security' && (
        <SecurityTab
          accessLogs={accessLogs}
          bannedIps={bannedIps}
          onBanIp={handleBanIp}
          onUnbanIp={handleUnbanIp}
          fetchWithAuth={fetchWithAuth}
          showToast={showToast}
        />
      )}

      {activeTab === 'settings' && (
        <SettingsTab
          siteTheme={siteTheme}
          onThemeChange={handleThemeChange}
          onSendTestEmail={handleSendTestEmail}
          fetchWithAuth={fetchWithAuth}
          showToast={showToast}
        />
      )}

      {/* Live Email Report Modal */}
      <ReportPreviewModal
        isOpen={isReportModalOpen}
        onClose={() => setIsReportModalOpen(false)}
        html={reportHtml}
        loading={loadingReportPreview}
        onRefresh={handleOpenReportPreview}
        onSend={handleDispatchReport}
        sending={sendingReport}
        recipient={reportConfig.recipient}
      />
    </AdminLayout>
  );
}
