import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Bot,
  Cpu,
  Sparkles,
  Zap,
  Play,
  Square,
  Trash2,
  RefreshCw,
  Sliders,
  Send,
  Copy,
  Check,
  HardDrive,
  Info,
  ChevronDown,
  ChevronUp,
  Server,
  Layers,
  Flame,
  ArrowRight,
  MessageSquare,
  Hash,
  AtSign,
  AlertTriangle,
  Download,
  Plus,
  Clock,
  Radio
} from 'lucide-react';

export default function AiStudioTab({ onToast }) {
  // Top Mode Switcher: 'ai' (Distributed AI Studio) vs 'chat' (Fleet Chat)
  const [activeTabMode, setActiveTabMode] = useState('ai');

  // Fleet Nodes State
  const [nodes, setNodes] = useState([]);
  const [selectedNodeId, setSelectedNodeId] = useState('');
  const [selectedModel, setSelectedModel] = useState('');
  const [loadingNodes, setLoadingNodes] = useState(true);

  // VRAM & Process Management State
  const [vramProcesses, setVramProcesses] = useState([]);
  const [loadingVram, setLoadingVram] = useState(false);
  const [keepAliveDuration, setKeepAliveDuration] = useState('15m');
  const [actionInProgress, setActionInProgress] = useState(false);
  const [showVramInfo, setShowVramInfo] = useState(false);
  const [pullingModel, setPullingModel] = useState(false);

  // AI Conversations & History State (Persisted in SQLite)
  const [conversations, setConversations] = useState([]);
  const [activeConversationId, setActiveConversationId] = useState(null);
  const [showHistoryDrawer, setShowHistoryDrawer] = useState(false);
  const [messages, setMessages] = useState([
    {
      id: 'welcome',
      role: 'assistant',
      content: "👋 Welcome to **Nexus Distributed AI Studio**.\n\nConnected to your fleet mesh compute nodes. All prompts and conversation sessions are orchestrated and synchronized across your fleet CLI and central SQLite database on faizal.uk.",
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  ]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [streamingTokenSpeed, setStreamingTokenSpeed] = useState(0);
  const [streamStats, setStreamStats] = useState({ tokens: 0, seconds: 0 });

  // System Tuning Drawer
  const [showSettingsDrawer, setShowSettingsDrawer] = useState(false);
  const [systemPrompt, setSystemPrompt] = useState('You are an expert pair-programming and distributed systems engineer. Give concise, highly technical responses with clean code blocks.');
  const [temperature, setTemperature] = useState(0.7);

  // Fleet Chat State (Mode 2)
  const [chatChannel, setChatChannel] = useState('global');
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [sendingChat, setSendingChat] = useState(false);

  // Copy State
  const [copiedId, setCopiedId] = useState(null);

  const messagesEndRef = useRef(null);
  const chatEndRef = useRef(null);
  const abortControllerRef = useRef(null);

  const getAdminPin = () => {
    return localStorage.getItem('faizal_admin_pin') || '';
  };

  const getAuthHeaders = () => {
    const pin = getAdminPin();
    return pin ? { 'Authorization': `Bearer ${pin}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
  };

  // 1. Fetch Fleet Nodes
  const fetchNodes = async () => {
    try {
      setLoadingNodes(true);
      const res = await fetch('/api/fleet/nodes', {
        headers: getAuthHeaders()
      });
      if (res.ok) {
        const data = await res.json();
        setNodes(data);
        if (data.length > 0 && !selectedNodeId) {
          const onlineWithModels = data.find(n => n.status === 'online' && (n.models?.length > 0 || n.telemetry?.ai_engine?.models?.length > 0));
          const target = onlineWithModels || data[0];
          setSelectedNodeId(target.node_id);
          const availableModels = target.models || target.telemetry?.ai_engine?.models || [];
          if (availableModels.length > 0) {
            setSelectedModel(availableModels[0]);
          }
        }
      }
    } catch (e) {
      console.error('Failed to load fleet nodes:', e);
    } finally {
      setLoadingNodes(false);
    }
  };

  // 2. Fetch SQLite AI Conversations List
  const fetchConversations = async () => {
    try {
      const res = await fetch('/api/fleet/ai/conversations', {
        headers: getAuthHeaders()
      });
      if (res.ok) {
        const list = await res.json();
        setConversations(list);
      }
    } catch (e) {
      console.error('Failed to load conversations:', e);
    }
  };

  // 3. Load Conversation Messages
  const handleSelectConversation = async (convId) => {
    try {
      setActiveConversationId(convId);
      const res = await fetch(`/api/fleet/ai/conversations/${convId}/messages`, {
        headers: getAuthHeaders()
      });
      if (res.ok) {
        const msgList = await res.json();
        if (msgList.length > 0) {
          setMessages(msgList.map(m => ({
            id: m.id,
            role: m.role,
            content: m.content,
            timestamp: m.created_at ? m.created_at.substring(11, 19) : ''
          })));
        } else {
          setMessages([
            {
              id: 'empty',
              role: 'assistant',
              content: 'Conversation resumed. Type your prompt below.',
              timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            }
          ]);
        }
        if (onToast) onToast('Conversation loaded from SQLite', 'info');
      }
    } catch (e) {
      console.error('Failed to load conversation messages:', e);
    }
  };

  // 4. Delete Conversation
  const handleDeleteConversation = async (e, convId) => {
    e.stopPropagation();
    try {
      const res = await fetch(`/api/fleet/ai/conversations/${convId}`, {
        method: 'DELETE',
        headers: getAuthHeaders()
      });
      if (res.ok) {
        setConversations(prev => prev.filter(c => c.id !== convId));
        if (activeConversationId === convId) {
          setActiveConversationId(null);
          handleClearChat();
        }
        if (onToast) onToast('Conversation deleted', 'info');
      }
    } catch (e) {
      console.error('Delete error:', e);
    }
  };

  // 5. Fetch Fleet Chat Messages
  const fetchChatMessages = async (channel) => {
    const targetChannel = channel || chatChannel;
    try {
      const res = await fetch(`/api/fleet/chat/messages?chat_id=${encodeURIComponent(targetChannel)}&limit=50`, {
        headers: getAuthHeaders()
      });
      if (res.ok) {
        const msgs = await res.json();
        setChatMessages(msgs);
      }
    } catch (e) {
      console.error('Failed to fetch chat messages:', e);
    }
  };

  // 6. Send Fleet Chat Message
  const handleSendChatMessage = async () => {
    const text = chatInput.trim();
    if (!text || sendingChat) return;
    setSendingChat(true);

    const isDm = chatChannel !== 'global';
    const payload = {
      chat_id: chatChannel,
      sender_id: 'web-operator',
      sender_name: 'Faizal (Web Console)',
      recipient_id: isDm ? chatChannel : null,
      content: text,
      message_type: 'text'
    };

    try {
      const res = await fetch('/api/fleet/chat/messages', {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify(payload)
      });
      if (res.ok) {
        setChatInput('');
        await fetchChatMessages(chatChannel);
      } else {
        if (onToast) onToast('Failed to send chat message', 'error');
      }
    } catch (e) {
      if (onToast) onToast(`Send error: ${e.message}`, 'error');
    } finally {
      setSendingChat(false);
    }
  };

  useEffect(() => {
    fetchNodes();
    fetchConversations();
    const interval = setInterval(fetchNodes, 15000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (activeTabMode === 'chat') {
      fetchChatMessages(chatChannel);
      const chatInterval = setInterval(() => fetchChatMessages(chatChannel), 4000);
      return () => clearInterval(chatInterval);
    }
  }, [activeTabMode, chatChannel]);

  const activeNode = nodes.find(n => n.node_id === selectedNodeId) || null;
  const availableModels = activeNode?.models || activeNode?.telemetry?.ai_engine?.models || [];

  // Memory Metrics & Edge Case calculations
  const memTotalMb = activeNode?.telemetry?.memory?.total_mb || 0;
  const memUsedMb = activeNode?.telemetry?.memory?.used_mb || 0;
  const memFreeGb = memTotalMb > 0 ? ((memTotalMb - memUsedMb) / 1024).toFixed(1) : null;
  const memUsagePct = memTotalMb > 0 ? Math.round((memUsedMb / memTotalMb) * 100) : 0;
  const isLowMemory = memFreeGb !== null && (parseFloat(memFreeGb) < 2.0 || memUsagePct > 85);
  const hasNoModels = activeNode?.status === 'online' && availableModels.length === 0;
  const isOffline = activeNode?.status === 'offline';

  // Update selected model when activeNode changes
  useEffect(() => {
    if (availableModels.length > 0 && (!selectedModel || !availableModels.includes(selectedModel))) {
      setSelectedModel(availableModels[0]);
    } else if (availableModels.length === 0) {
      setSelectedModel('');
    }
  }, [selectedNodeId, availableModels]);

  // Fetch VRAM State via /api/fleet/nodes/:id/ai/ps
  const fetchVramProcesses = async () => {
    if (!selectedNodeId) return;
    try {
      setLoadingVram(true);
      const res = await fetch(`/api/fleet/nodes/${selectedNodeId}/ai/ps`, {
        headers: getAuthHeaders()
      });
      if (res.ok) {
        const cmdResult = await res.json();
        if (cmdResult.stdout) {
          try {
            const parsed = JSON.parse(cmdResult.stdout);
            setVramProcesses(Array.isArray(parsed) ? parsed : []);
          } catch (_) {
            setVramProcesses([]);
          }
        } else {
          setVramProcesses([]);
        }
      }
    } catch (e) {
      console.error('Failed to fetch VRAM state:', e);
    } finally {
      setLoadingVram(false);
    }
  };

  useEffect(() => {
    if (selectedNodeId) {
      fetchVramProcesses();
    }
  }, [selectedNodeId]);

  // Remote Pull Model
  const handleRemotePull = async (modelToPull = 'qwen2.5-coder:1.5b') => {
    if (!selectedNodeId) return;
    setPullingModel(true);
    try {
      const res = await fetch(`/api/fleet/nodes/${selectedNodeId}/pull`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({ model: modelToPull })
      });
      if (res.ok) {
        if (onToast) onToast(`Remote pull for '${modelToPull}' dispatched to ${activeNode?.hostname}!`, 'success');
        setTimeout(fetchNodes, 5000);
      } else {
        if (onToast) onToast('Failed to dispatch model pull', 'error');
      }
    } catch (e) {
      if (onToast) onToast(`Error: ${e.message}`, 'error');
    } finally {
      setPullingModel(false);
    }
  };

  // Pre-load Model into VRAM
  const handlePreloadModel = async () => {
    if (!selectedNodeId || !selectedModel) return;
    setActionInProgress(true);
    try {
      const res = await fetch(`/api/fleet/nodes/${selectedNodeId}/ai/load`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          model: selectedModel,
          keep_alive: keepAliveDuration
        })
      });
      if (res.ok) {
        if (onToast) onToast(`Pre-loaded '${selectedModel}' into VRAM (keep_alive: ${keepAliveDuration})`, 'success');
        await fetchVramProcesses();
      } else {
        if (onToast) onToast('Failed to pre-load model into VRAM', 'error');
      }
    } catch (e) {
      if (onToast) onToast(`Error: ${e.message}`, 'error');
    } finally {
      setActionInProgress(false);
    }
  };

  // Evict / Unload Model from VRAM
  const handleUnloadModel = async (modelToUnload) => {
    const target = modelToUnload || selectedModel;
    if (!selectedNodeId || !target) return;
    setActionInProgress(true);
    try {
      const res = await fetch(`/api/fleet/nodes/${selectedNodeId}/ai/unload`, {
        method: 'POST',
        headers: getAuthHeaders(),
        body: JSON.stringify({
          model: target
        })
      });
      if (res.ok) {
        if (onToast) onToast(`Evicted '${target}' from VRAM. Memory released.`, 'success');
        await fetchVramProcesses();
      } else {
        if (onToast) onToast('Failed to unload model from VRAM', 'error');
      }
    } catch (e) {
      if (onToast) onToast(`Error: ${e.message}`, 'error');
    } finally {
      setActionInProgress(false);
    }
  };

  // Send Prompt & Handle Real-Time SSE Stream with SQLite Persistence
  const handleSendMessage = async (customPrompt) => {
    const promptToSend = (customPrompt || inputPrompt).trim();
    if (!promptToSend || !selectedNodeId || isStreaming) return;

    if (isOffline) {
      if (onToast) onToast('Cannot prompt an offline node. Switch to an online node.', 'error');
      return;
    }

    if (hasNoModels || !selectedModel) {
      if (onToast) onToast('Target node has no models installed. Please pull a model first.', 'warning');
      return;
    }

    setInputPrompt('');

    const userMsgId = 'user-' + Date.now();
    const assistantMsgId = 'asst-' + Date.now();
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    setMessages(prev => [
      ...prev,
      { id: userMsgId, role: 'user', content: promptToSend, timestamp },
      { id: assistantMsgId, role: 'assistant', content: '', timestamp, model: selectedModel, isStreaming: true }
    ]);

    setIsStreaming(true);
    setStreamingTokenSpeed(0);
    const startTime = Date.now();
    let tokenCount = 0;

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      // 1. Ensure conversation exists in SQLite
      let currentConvId = activeConversationId;
      if (!currentConvId) {
        const title = promptToSend.length > 32 ? promptToSend.substring(0, 32) + '...' : promptToSend;
        const createRes = await fetch('/api/fleet/ai/conversations', {
          method: 'POST',
          headers: getAuthHeaders(),
          body: JSON.stringify({
            title,
            target_node_id: selectedNodeId,
            model: selectedModel,
            system_prompt: systemPrompt || undefined,
            temperature: parseFloat(temperature)
          })
        });
        if (createRes.ok) {
          const convData = await createRes.json();
          currentConvId = convData.id;
          setActiveConversationId(convData.id);
          fetchConversations();
        }
      }

      // 2. Stream prompt via SQLite-backed endpoint
      const streamEndpoint = currentConvId
        ? `/api/fleet/ai/conversations/${currentConvId}/messages`
        : `/api/fleet/nodes/${selectedNodeId}/ai/stream`;

      const response = await fetch(streamEndpoint, {
        method: 'POST',
        headers: getAuthHeaders(),
        signal: controller.signal,
        body: JSON.stringify({
          model: selectedModel,
          content: promptToSend,
          prompt: promptToSend,
          system_prompt: systemPrompt || undefined,
          temperature: parseFloat(temperature)
        })
      });

      if (!response.ok) {
        throw new Error(`Server returned status ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop();

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const chunk = line.slice(5).trim();
            if (chunk) {
              tokenCount++;
              const elapsedSec = (Date.now() - startTime) / 1000;
              if (elapsedSec > 0) {
                setStreamingTokenSpeed(Math.round((tokenCount / elapsedSec) * 10) / 10);
                setStreamStats({ tokens: tokenCount, seconds: Math.round(elapsedSec * 10) / 10 });
              }

              setMessages(prev =>
                prev.map(m =>
                  m.id === assistantMsgId ? { ...m, content: m.content + chunk } : m
                )
              );
            }
          }
        }
      }

      setMessages(prev =>
        prev.map(m =>
          m.id === assistantMsgId ? { ...m, isStreaming: false } : m
        )
      );

      // Refresh conversations count
      fetchConversations();
    } catch (e) {
      if (e.name !== 'AbortError') {
        setMessages(prev =>
          prev.map(m =>
            m.id === assistantMsgId
              ? { ...m, content: m.content + `\n\n*(Error: ${e.message})*`, isStreaming: false }
              : m
          )
        );
      }
    } finally {
      setIsStreaming(false);
      abortControllerRef.current = null;
    }
  };

  const handleStopStream = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      setIsStreaming(false);
    }
  };

  const handleClearChat = () => {
    setActiveConversationId(null);
    setMessages([
      {
        id: 'welcome',
        role: 'assistant',
        content: "New conversation started. Select a model and start prompting your fleet.",
        timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      }
    ]);
  };

  const handleCopyCode = (text, id) => {
    navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages]);

  // GPU & VRAM Metrics from Node Telemetry
  const gpuTelemetry = activeNode?.telemetry?.gpu;
  const isGpuAvailable = !!gpuTelemetry;
  const vramTotalMb = gpuTelemetry?.vram_total_mb || 0;
  const vramUsedMb = gpuTelemetry?.vram_used_mb || 0;
  const vramPct = vramTotalMb > 0 ? Math.round((vramUsedMb / vramTotalMb) * 100) : 0;

  return (
    <div className="space-y-6">
      {/* ─── DUAL MODE NAVIGATION BAR (AI vs FLEET CHAT) ─── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[#0b0c10] border border-white/10 rounded-2xl p-2.5 backdrop-blur-xl shadow-xl">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTabMode('ai')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all ${
              activeTabMode === 'ai'
                ? 'bg-gradient-to-r from-[#00f0ff]/20 to-blue-500/20 text-[#00f0ff] border border-[#00f0ff]/40 shadow-lg shadow-[#00f0ff]/10'
                : 'text-white/60 hover:text-white hover:bg-white/5 border border-transparent'
            }`}
          >
            <Bot className="w-4 h-4" />
            <span>🤖 Nexus Distributed AI Studio</span>
          </button>

          <button
            onClick={() => setActiveTabMode('chat')}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all ${
              activeTabMode === 'chat'
                ? 'bg-gradient-to-r from-[#89ff00]/20 to-emerald-500/20 text-[#89ff00] border border-[#89ff00]/40 shadow-lg shadow-[#89ff00]/10'
                : 'text-white/60 hover:text-white hover:bg-white/5 border border-transparent'
            }`}
          >
            <MessageSquare className="w-4 h-4" />
            <span>💬 Fleet Chat (Global & Device-to-Device)</span>
          </button>
        </div>

        <div className="flex items-center gap-2 px-3 text-xs text-white/50">
          <Radio className="w-3.5 h-3.5 text-[#89ff00] animate-pulse" />
          <span>Central Hub: <strong>faizal.uk</strong> (SQLite Mesh)</span>
        </div>
      </div>

      {activeTabMode === 'ai' ? (
        <>
          {/* ─── TOP CONTROL BAR ─── */}
          <div className="bg-[#0b0c10] border border-white/10 rounded-2xl p-4 sm:p-5 shadow-2xl backdrop-blur-xl">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
              
              {/* Node & Model Selector Group */}
              <div className="flex flex-wrap items-center gap-3">
                
                {/* Fleet Node Dropdown */}
                <div className="flex items-center gap-2 bg-[#14151b] border border-white/10 px-3 py-2 rounded-xl">
                  <Server className="w-4 h-4 text-[#89ff00]" />
                  <label className="text-xs text-white/50 font-medium">Node:</label>
                  <select
                    value={selectedNodeId}
                    onChange={(e) => setSelectedNodeId(e.target.value)}
                    className="bg-transparent text-sm font-semibold text-white focus:outline-none cursor-pointer"
                  >
                    {nodes.map(n => (
                      <option key={n.node_id} value={n.node_id} className="bg-[#14151b] text-white">
                        {n.hostname} ({n.os}) {n.status === 'online' ? '● Online' : '○ Offline'}
                      </option>
                    ))}
                  </select>
                </div>

                {/* AI Model Dropdown */}
                <div className="flex items-center gap-2 bg-[#14151b] border border-white/10 px-3 py-2 rounded-xl">
                  <Bot className="w-4 h-4 text-[#00f0ff]" />
                  <label className="text-xs text-white/50 font-medium">Model:</label>
                  <select
                    value={selectedModel}
                    onChange={(e) => setSelectedModel(e.target.value)}
                    className="bg-transparent text-sm font-semibold text-white focus:outline-none cursor-pointer max-w-[200px] truncate"
                  >
                    {availableModels.length > 0 ? (
                      availableModels.map(m => (
                        <option key={m} value={m} className="bg-[#14151b] text-white">
                          {m}
                        </option>
                      ))
                    ) : (
                      <option value="" className="bg-[#14151b] text-white/50">No models found</option>
                    )}
                  </select>
                </div>

                {/* Quick Engine Status Pill */}
                <div className="flex items-center gap-2 px-3 py-2 bg-white/5 rounded-xl border border-white/5 text-xs">
                  <span className={`w-2 h-2 rounded-full ${activeNode?.status === 'online' ? 'bg-[#89ff00] animate-pulse' : 'bg-red-500'}`} />
                  <span className="text-white/70">Ollama Remote Port 11434</span>
                </div>
              </div>

              {/* Action Tools */}
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  onClick={() => setShowHistoryDrawer(!showHistoryDrawer)}
                  className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-xl border transition-all ${
                    showHistoryDrawer ? 'bg-[#00f0ff]/10 border-[#00f0ff] text-[#00f0ff]' : 'bg-[#14151b] border-white/10 text-white/80 hover:border-white/20'
                  }`}
                >
                  <Clock className="w-3.5 h-3.5" />
                  <span>History ({conversations.length})</span>
                </button>

                <button
                  onClick={() => setShowSettingsDrawer(!showSettingsDrawer)}
                  className={`flex items-center gap-1.5 px-3 py-2 text-xs font-semibold rounded-xl border transition-all ${
                    showSettingsDrawer ? 'bg-[#89ff00]/10 border-[#89ff00] text-[#89ff00]' : 'bg-[#14151b] border-white/10 text-white/80 hover:border-white/20'
                  }`}
                >
                  <Sliders className="w-3.5 h-3.5" />
                  <span>Tuning</span>
                </button>

                <button
                  onClick={fetchVramProcesses}
                  disabled={loadingVram}
                  className="flex items-center gap-1.5 px-3 py-2 bg-[#14151b] hover:bg-white/5 border border-white/10 text-xs font-semibold text-white/80 rounded-xl transition-all"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${loadingVram ? 'animate-spin text-[#00f0ff]' : ''}`} />
                  <span>VRAM</span>
                </button>

                <button
                  onClick={handleClearChat}
                  className="flex items-center gap-1.5 px-3 py-2 bg-[#14151b] hover:bg-emerald-500/10 hover:border-emerald-500/30 border border-white/10 text-xs font-semibold text-emerald-400 rounded-xl transition-all"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>New Chat</span>
                </button>
              </div>
            </div>

            {/* ─── CONVERSATION HISTORY DRAWER ─── */}
            <AnimatePresence>
              {showHistoryDrawer && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  className="overflow-hidden border-t border-white/10 mt-4 pt-4 space-y-3"
                >
                  <div className="flex items-center justify-between text-xs font-bold text-white/70">
                    <span>SQLite Persistent AI Conversations</span>
                    <span className="text-[10px] text-white/40">Stored centrally on faizal.uk</span>
                  </div>

                  {conversations.length === 0 ? (
                    <div className="text-xs text-white/40 italic py-2">No stored conversations yet. Send your first prompt to save automatically.</div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2 max-h-48 overflow-y-auto pr-1">
                      {conversations.map(c => (
                        <div
                          key={c.id}
                          onClick={() => handleSelectConversation(c.id)}
                          className={`p-2.5 rounded-xl border text-xs cursor-pointer transition-all flex items-center justify-between gap-2 ${
                            activeConversationId === c.id
                              ? 'bg-[#00f0ff]/10 border-[#00f0ff]/40 text-white'
                              : 'bg-[#14151b] border-white/5 text-white/70 hover:border-white/20'
                          }`}
                        >
                          <div className="min-w-0">
                            <div className="font-semibold truncate">{c.title || 'Untitled Conversation'}</div>
                            <div className="text-[10px] text-white/40 flex items-center gap-2">
                              <span>{c.model}</span>
                              <span>• {c.message_count || 0} msgs</span>
                            </div>
                          </div>
                          <button
                            onClick={(e) => handleDeleteConversation(e, c.id)}
                            className="p-1 text-white/30 hover:text-red-400 hover:bg-red-500/10 rounded"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>

            {/* ─── TUNING DRAWER ─── */}
            <AnimatePresence>
              {showSettingsDrawer && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  className="overflow-hidden border-t border-white/10 mt-4 pt-4 space-y-4"
                >
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="md:col-span-2 space-y-1.5">
                      <label className="text-xs font-semibold text-white/70 flex items-center justify-between">
                        <span>System Prompt Persona</span>
                        <span className="text-[10px] text-white/40">Injected before every prompt</span>
                      </label>
                      <textarea
                        rows={2}
                        value={systemPrompt}
                        onChange={(e) => setSystemPrompt(e.target.value)}
                        placeholder="Enter system prompt..."
                        className="w-full bg-[#14151b] border border-white/10 rounded-xl p-2.5 text-xs text-white/90 focus:outline-none focus:border-[#89ff00]/50 resize-none font-mono"
                      />
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <label className="text-xs font-semibold text-white/70">Temperature</label>
                        <span className="text-xs font-mono text-[#89ff00]">{temperature}</span>
                      </div>
                      <input
                        type="range"
                        min="0.0"
                        max="1.0"
                        step="0.05"
                        value={temperature}
                        onChange={(e) => setTemperature(parseFloat(e.target.value))}
                        className="w-full accent-[#89ff00] cursor-pointer"
                      />
                      <div className="flex justify-between text-[10px] text-white/40">
                        <span>0.0 (Deterministic)</span>
                        <span>1.0 (Creative)</span>
                      </div>
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* ─── EDGE CASE NOTIFICATION BANNERS ─── */}
          {hasNoModels && (
            <div className="bg-amber-500/10 border border-amber-500/30 rounded-2xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-amber-300">
              <div className="flex items-center gap-3">
                <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" />
                <div>
                  <strong className="font-bold text-white">Notice: No Models Installed on '{activeNode?.hostname}'</strong>
                  <p className="text-amber-300/70">This node is online in the fleet, but does not have any Ollama model weights downloaded.</p>
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button
                  onClick={() => handleRemotePull('qwen2.5-coder:1.5b')}
                  disabled={pullingModel}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-amber-400 text-black font-bold rounded-xl hover:bg-amber-300 transition-all text-xs"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>{pullingModel ? 'Pulling...' : 'Pull Lightweight Model (1.5B)'}</span>
                </button>
              </div>
            </div>
          )}

          {isLowMemory && (
            <div className="bg-orange-500/10 border border-orange-500/30 rounded-2xl p-4 flex items-center gap-3 text-xs text-orange-300">
              <AlertTriangle className="w-5 h-5 text-orange-400 shrink-0" />
              <div>
                <strong className="font-bold text-white">Resource Safety Warning: Tight Memory on '{activeNode?.hostname}'</strong>
                <p className="text-orange-300/70">Only <strong>{memFreeGb} GB free RAM</strong> ({memUsagePct}% load). Loading a heavy 7B model (~5 GB) may trigger severe swap thrashing or crash system daemons. Prefer 1.5B or 3B models.</p>
              </div>
            </div>
          )}

          {isOffline && (
            <div className="bg-red-500/10 border border-red-500/30 rounded-2xl p-4 flex items-center gap-3 text-xs text-red-300">
              <AlertTriangle className="w-5 h-5 text-red-400 shrink-0" />
              <div>
                <strong className="font-bold text-white">Node '{activeNode?.hostname}' is Offline</strong>
                <p className="text-red-300/70">Last known models: {activeNode?.models?.join(', ') || 'none'}. Remote prompts cannot be executed until this machine connects to the mesh.</p>
              </div>
            </div>
          )}

          {/* ─── VRAM & MEMORY CONTROLS CARD ─── */}
          <div className="bg-[#0b0c10] border border-white/10 rounded-2xl p-5 shadow-2xl backdrop-blur-xl space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="flex items-center gap-2.5">
                <div className="p-2 bg-gradient-to-br from-amber-500/20 to-orange-500/20 rounded-xl border border-amber-500/30">
                  <Cpu className="w-5 h-5 text-amber-400" />
                </div>
                <div>
                  <h2 className="text-sm font-bold text-white flex items-center gap-2">
                    <span>VRAM Allocation & Model Lifecycle Control</span>
                    <span className="text-[10px] uppercase font-mono px-2 py-0.5 bg-amber-400/10 text-amber-300 rounded border border-amber-400/20">
                      Ollama /api/ps
                    </span>
                  </h2>
                  <p className="text-xs text-white/50">Manage model residency in GPU VRAM and evict inactive weights</p>
                </div>
              </div>

              {/* Pre-load Duration Picker + Action Buttons */}
              <div className="flex items-center gap-2 flex-wrap">
                <select
                  value={keepAliveDuration}
                  onChange={(e) => setKeepAliveDuration(e.target.value)}
                  className="bg-[#14151b] border border-white/10 text-xs text-white rounded-xl px-2.5 py-1.5 focus:outline-none"
                >
                  <option value="5m">Keep Warm: 5 mins</option>
                  <option value="15m">Keep Warm: 15 mins</option>
                  <option value="1h">Keep Warm: 1 hour</option>
                  <option value="-1">Keep Warm: Indefinite (-1)</option>
                </select>

                <button
                  onClick={handlePreloadModel}
                  disabled={actionInProgress || !selectedModel || isOffline}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-[#89ff00]/10 hover:bg-[#89ff00]/20 border border-[#89ff00]/30 text-xs font-semibold text-[#89ff00] rounded-xl transition-all shadow-lg hover:shadow-[#89ff00]/10 disabled:opacity-40"
                >
                  <Flame className="w-3.5 h-3.5" />
                  <span>Pre-load into VRAM</span>
                </button>

                <button
                  onClick={() => handleUnloadModel(selectedModel)}
                  disabled={actionInProgress || !selectedModel || isOffline}
                  className="flex items-center gap-1.5 px-3 py-1.5 bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-xs font-semibold text-red-400 rounded-xl transition-all disabled:opacity-40"
                >
                  <Square className="w-3.5 h-3.5" />
                  <span>Evict Model</span>
                </button>
              </div>
            </div>

            {/* VRAM Bar + Active Models In Memory Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
              
              {/* Hardware GPU / VRAM Meter */}
              <div className="bg-[#14151b] border border-white/5 p-4 rounded-xl space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-white/70 flex items-center gap-1.5">
                    <HardDrive className="w-3.5 h-3.5 text-[#00f0ff]" />
                    {isGpuAvailable ? gpuTelemetry.name : `${activeNode?.hostname || 'Host'} Hardware`}
                  </span>
                  <span className="font-mono text-white/90">
                    {isGpuAvailable ? `${(vramUsedMb / 1024).toFixed(1)} / ${(vramTotalMb / 1024).toFixed(1)} GB (${vramPct}%)` : `RAM: ${(memUsedMb/1024).toFixed(1)}/${(memTotalMb/1024).toFixed(1)} GB`}
                  </span>
                </div>
                <div className="w-full bg-white/5 h-2 rounded-full overflow-hidden">
                  <div
                    className={`h-full transition-all duration-500 rounded-full ${
                      vramPct > 85 ? 'bg-red-500' : vramPct > 60 ? 'bg-amber-400' : 'bg-[#00f0ff]'
                    }`}
                    style={{ width: `${Math.max(vramPct, 3)}%` }}
                  />
                </div>
                <div className="flex justify-between text-[11px] text-white/40">
                  <span>{isGpuAvailable ? `${gpuTelemetry.vendor} Driver` : 'CPU Offloading Active'}</span>
                  <span>{isGpuAvailable && gpuTelemetry.temperature_c ? `${gpuTelemetry.temperature_c}°C` : 'Optimal'}</span>
                </div>
              </div>

              {/* Live In-Memory Models List */}
              <div className="bg-[#14151b] border border-white/5 p-4 rounded-xl space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-white/70 flex items-center gap-1.5">
                    <Layers className="w-3.5 h-3.5 text-[#89ff00]" />
                    Active in VRAM ({vramProcesses.length})
                  </span>
                  <button
                    onClick={() => setShowVramInfo(!showVramInfo)}
                    className="text-[11px] text-white/40 hover:text-white/80 flex items-center gap-1"
                  >
                    <Info className="w-3 h-3" />
                    <span>How memory works</span>
                  </button>
                </div>

                {vramProcesses.length === 0 ? (
                  <div className="text-xs text-white/40 py-2 italic flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-white/20" />
                    <span>No models currently resident in VRAM. (Cold start required)</span>
                  </div>
                ) : (
                  <div className="space-y-1.5 max-h-24 overflow-y-auto pr-1">
                    {vramProcesses.map((p, idx) => {
                      const vramMb = p.size_vram ? (p.size_vram / 1048576).toFixed(0) : '0';
                      return (
                        <div key={idx} className="flex items-center justify-between bg-black/40 px-2.5 py-1.5 rounded-lg text-xs">
                          <div className="flex items-center gap-2 min-w-0">
                            <span className="w-1.5 h-1.5 rounded-full bg-[#89ff00] animate-pulse" />
                            <span className="font-mono text-white font-medium truncate">{p.name}</span>
                            <span className="text-[10px] text-amber-300 font-mono">({vramMb} MB VRAM)</span>
                          </div>
                          <button
                            onClick={() => handleUnloadModel(p.name)}
                            className="text-[10px] text-red-400 hover:text-red-300 px-2 py-0.5 rounded bg-red-500/10 border border-red-500/20"
                          >
                            Evict
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>

            {/* Technical Explainer Tooltip Card */}
            <AnimatePresence>
              {showVramInfo && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="bg-black/60 border border-white/10 p-4 rounded-xl text-xs space-y-2 text-white/70"
                >
                  <div className="font-bold text-white flex items-center gap-1.5">
                    <Info className="w-4 h-4 text-[#00f0ff]" />
                    Technical Lifecycle: How Ollama Loads & Frees Memory
                  </div>
                  <ul className="list-disc pl-5 space-y-1 text-white/60">
                    <li><strong className="text-white">Automatic 5-Minute Inactivity Eviction:</strong> Ollama sets a default <code className="text-[#89ff00]">keep_alive: "5m"</code> timer. If no prompts arrive within 5 minutes, weights are dumped from VRAM to release system memory.</li>
                    <li><strong className="text-white">Pre-warming:</strong> Clicking "Pre-load into VRAM" sends <code className="text-[#89ff00]">POST /api/generate</code> with your chosen duration. This eliminates the initial cold-start latency.</li>
                    <li><strong className="text-white">Immediate Eviction:</strong> Passing <code className="text-[#89ff00]">keep_alive: 0</code> purges the model instantly from memory.</li>
                  </ul>
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* ─── INTERACTIVE CHAT INTERFACE ─── */}
          <div className="bg-[#0b0c10] border border-white/10 rounded-2xl shadow-2xl flex flex-col h-[650px] overflow-hidden">
            
            {/* Chat Messages Scrollable Area */}
            <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-5">
              {messages.map((msg) => (
                <div
                  key={msg.id}
                  className={`flex gap-3.5 max-w-3xl ${msg.role === 'user' ? 'ml-auto flex-row-reverse' : ''}`}
                >
                  {/* Avatar Icon */}
                  <div className={`w-8 h-8 rounded-xl flex items-center justify-center shrink-0 ${
                    msg.role === 'user'
                      ? 'bg-gradient-to-br from-[#89ff00] to-emerald-600 text-black font-bold text-xs'
                      : 'bg-gradient-to-br from-[#00f0ff]/20 to-blue-600/20 border border-[#00f0ff]/40 text-[#00f0ff]'
                  }`}>
                    {msg.role === 'user' ? 'U' : <Bot className="w-4 h-4" />}
                  </div>

                  {/* Message Bubble */}
                  <div className="space-y-1 min-w-0">
                    <div className={`p-4 rounded-2xl text-xs sm:text-sm leading-relaxed backdrop-blur-md ${
                      msg.role === 'user'
                        ? 'bg-[#89ff00]/10 border border-[#89ff00]/30 text-white rounded-tr-none'
                        : 'bg-[#14151b] border border-white/10 text-white/90 rounded-tl-none shadow-xl'
                    }`}>
                      <div className="prose prose-invert prose-xs max-w-none whitespace-pre-wrap font-sans">
                        {msg.content}
                      </div>

                      {msg.isStreaming && (
                        <span className="inline-block w-2 h-4 bg-[#00f0ff] animate-pulse ml-1 align-middle" />
                      )}
                    </div>

                    <div className={`flex items-center gap-2 text-[10px] text-white/40 px-1 ${msg.role === 'user' ? 'justify-end' : ''}`}>
                      <span>{msg.timestamp}</span>
                      {msg.model && <span>• {msg.model}</span>}
                      {msg.role === 'assistant' && msg.content && (
                        <button
                          onClick={() => handleCopyCode(msg.content, msg.id)}
                          className="hover:text-white flex items-center gap-0.5 ml-1"
                        >
                          {copiedId === msg.id ? <Check className="w-3 h-3 text-[#89ff00]" /> : <Copy className="w-3 h-3" />}
                          <span>{copiedId === msg.id ? 'Copied' : 'Copy'}</span>
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>

            {/* Live Token Streaming Speed Bar */}
            {isStreaming && (
              <div className="bg-[#14151b] border-t border-white/5 px-4 py-1.5 flex items-center justify-between text-xs text-white/60">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#00f0ff] animate-ping" />
                  <span>Generating via <strong className="text-white">{selectedModel}</strong> on {activeNode?.hostname}</span>
                </div>
                <div className="flex items-center gap-4 font-mono text-[11px]">
                  <span className="text-[#89ff00] font-bold">{streamingTokenSpeed} tok/s</span>
                  <span>{streamStats.tokens} tokens</span>
                  <span>{streamStats.seconds}s</span>
                  <button
                    onClick={handleStopStream}
                    className="flex items-center gap-1 text-red-400 hover:text-red-300 font-sans"
                  >
                    <Square className="w-3 h-3" />
                    <span>Stop</span>
                  </button>
                </div>
              </div>
            )}

            {/* Prompt Input Container */}
            <div className="p-4 bg-[#070709] border-t border-white/10 space-y-2">
              <div className="flex items-center gap-2 overflow-x-auto pb-1 text-[11px]">
                <span className="text-white/40 shrink-0">Quick starters:</span>
                {[
                  "Explain how Ollama VRAM keep_alive works",
                  "Audit GPU memory and model layers",
                  "Write a Rust Axum SSE stream endpoint",
                  "Draft a Docker compose for Ollama"
                ].map((chip, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleSendMessage(chip)}
                    className="shrink-0 bg-white/5 hover:bg-white/10 border border-white/5 hover:border-white/20 text-white/70 px-2.5 py-1 rounded-lg transition-all"
                  >
                    {chip}
                  </button>
                ))}
              </div>

              <div className="flex items-end gap-2">
                <textarea
                  rows={2}
                  value={inputPrompt}
                  onChange={(e) => setInputPrompt(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                      e.preventDefault();
                      handleSendMessage();
                    }
                  }}
                  placeholder={`Message ${selectedModel || 'model'} on ${activeNode?.hostname || 'fleet'}... (Shift+Enter for newline)`}
                  className="flex-1 bg-[#14151b] border border-white/10 rounded-xl p-3 text-xs sm:text-sm text-white focus:outline-none focus:border-[#89ff00]/50 resize-none font-sans"
                />

                <button
                  onClick={() => handleSendMessage()}
                  disabled={isStreaming || !inputPrompt.trim() || isOffline || hasNoModels}
                  className={`p-3 rounded-xl transition-all font-semibold ${
                    isStreaming || !inputPrompt.trim() || isOffline || hasNoModels
                      ? 'bg-white/5 text-white/30 cursor-not-allowed border border-white/5'
                      : 'bg-[#89ff00] text-black hover:bg-[#89ff00]/90 shadow-lg shadow-[#89ff00]/20'
                  }`}
                >
                  <Send className="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        </>
      ) : (
        /* ─── FLEET CHAT INTERFACE (MODE 2) ─── */
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Channel Selector Sidebar */}
          <div className="bg-[#0b0c10] border border-white/10 rounded-2xl p-4 space-y-4">
            <div>
              <h3 className="text-xs font-bold text-white/50 tracking-wider uppercase mb-2">Fleet Channels</h3>
              <button
                onClick={() => setChatChannel('global')}
                className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                  chatChannel === 'global'
                    ? 'bg-[#89ff00]/15 text-[#89ff00] border border-[#89ff00]/30'
                    : 'text-white/70 hover:bg-white/5'
                }`}
              >
                <div className="flex items-center gap-2">
                  <Hash className="w-4 h-4 text-[#89ff00]" />
                  <span>global</span>
                </div>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-white/10 text-white/60">Broadcast</span>
              </button>
            </div>

            <div>
              <h3 className="text-xs font-bold text-white/50 tracking-wider uppercase mb-2">Direct Device Channels</h3>
              <div className="space-y-1">
                {nodes.map(n => (
                  <button
                    key={n.node_id}
                    onClick={() => setChatChannel(n.node_id)}
                    className={`w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs transition-all ${
                      chatChannel === n.node_id
                        ? 'bg-[#00f0ff]/15 text-[#00f0ff] border border-[#00f0ff]/30 font-semibold'
                        : 'text-white/70 hover:bg-white/5'
                    }`}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <AtSign className="w-3.5 h-3.5 text-white/40" />
                      <span className="truncate">{n.hostname}</span>
                    </div>
                    <span className={`w-2 h-2 rounded-full ${n.status === 'online' ? 'bg-[#89ff00]' : 'bg-white/20'}`} />
                  </button>
                ))}
              </div>
            </div>
          </div>

          {/* Chat Messages & Input Area */}
          <div className="lg:col-span-3 bg-[#0b0c10] border border-white/10 rounded-2xl shadow-2xl flex flex-col h-[650px] overflow-hidden">
            {/* Header */}
            <div className="p-4 bg-[#14151b] border-b border-white/10 flex items-center justify-between">
              <div className="flex items-center gap-2 text-sm font-bold text-white">
                {chatChannel === 'global' ? (
                  <>
                    <Hash className="w-4 h-4 text-[#89ff00]" />
                    <span>#global (Broadcast to all fleet nodes)</span>
                  </>
                ) : (
                  <>
                    <AtSign className="w-4 h-4 text-[#00f0ff]" />
                    <span>Direct Message with {nodes.find(n => n.node_id === chatChannel)?.hostname || chatChannel}</span>
                  </>
                )}
              </div>
              <button
                onClick={() => fetchChatMessages(chatChannel)}
                className="p-1.5 rounded-lg bg-white/5 hover:bg-white/10 text-white/60 hover:text-white text-xs flex items-center gap-1"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Refresh</span>
              </button>
            </div>

            {/* Chat Stream */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
              {chatMessages.length === 0 ? (
                <div className="text-center py-16 text-xs text-white/40 italic">
                  No messages in channel '{chatChannel}'. Start the conversation!
                </div>
              ) : (
                chatMessages.map(msg => (
                  <div key={msg.id} className="bg-[#14151b]/80 border border-white/5 rounded-xl p-3 space-y-1">
                    <div className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-[#89ff00]">{msg.sender_name}</span>
                        <span className="text-[10px] text-white/40">({msg.sender_id})</span>
                      </div>
                      <span className="text-[10px] text-white/30 font-mono">
                        {msg.created_at ? msg.created_at.substring(11, 19) : ''}
                      </span>
                    </div>
                    <div className="text-xs text-white/90 font-sans whitespace-pre-wrap">{msg.content}</div>
                  </div>
                ))
              )}
              <div ref={chatEndRef} />
            </div>

            {/* Chat Input */}
            <div className="p-4 bg-[#070709] border-t border-white/10 flex items-center gap-2">
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    handleSendChatMessage();
                  }
                }}
                placeholder={`Message in ${chatChannel === 'global' ? '#global' : '@' + chatChannel}...`}
                className="flex-1 bg-[#14151b] border border-white/10 rounded-xl px-4 py-2.5 text-xs text-white focus:outline-none focus:border-[#89ff00]/50"
              />
              <button
                onClick={handleSendChatMessage}
                disabled={sendingChat || !chatInput.trim()}
                className="px-4 py-2.5 bg-[#89ff00] text-black font-bold text-xs rounded-xl hover:bg-[#89ff00]/90 transition-all disabled:opacity-40 flex items-center gap-1.5"
              >
                <Send className="w-3.5 h-3.5" />
                <span>Send</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
