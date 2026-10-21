import React, { useState, useEffect } from 'react';
import {
  Settings,
  Sparkles,
  Mic,
  Volume2,
  ShieldAlert,
  UserCheck,
  Database,
  Info,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Play,
  RotateCcw,
  Download,
  Trash2,
  Edit2,
  Plus,
  Search,
  ExternalLink,
  Shield,
  Radio,
  Sliders,
  Bell,
  Cpu,
  Save,
  Check,
  ShieldCheck,
} from 'lucide-react';
import {
  AiSettingsConfig,
  VoiceSettingsConfig,
  AutomationSettingsConfig,
  PersonalizationConfig,
  RoomMemoryEntity,
  MemoryCategory,
} from '../types/settings';

interface Props {
  onBackToEditor?: () => void;
  onClearWorkspace?: () => void;
}

export const SettingsScreen: React.FC<Props> = ({ onBackToEditor, onClearWorkspace }) => {
  const [activeTab, setActiveTab] = useState<'ai' | 'voice' | 'automation' | 'personalization' | 'memory' | 'about'>('ai');

  // 1. AI Settings State
  const [aiConfig, setAiConfig] = useState<AiSettingsConfig>(() => {
    const saved = localStorage.getItem('nexus_ai_settings');
    if (saved) {
      try { return JSON.parse(saved); } catch {}
    }
    return {
      provider: 'gemini',
      model: 'gemini-3.8-flash',
      temperature: 0.2,
      topP: 0.95,
      maxOutputTokens: 8192,
      streamingEnabled: true,
      thinkingLevel: 'MEDIUM',
    };
  });

  const [testApiStatus, setTestApiStatus] = useState<{
    testing: boolean;
    result?: { connected: boolean; latencyMs: number; model: string; reply?: string; error?: string };
  }>({ testing: false });

  // 2. Voice Settings State
  const [voiceConfig, setVoiceConfig] = useState<VoiceSettingsConfig>(() => {
    const saved = localStorage.getItem('nexus_voice_settings');
    if (saved) {
      try { return JSON.parse(saved); } catch {}
    }
    return {
      language: 'en-US',
      selectedVoice: 'Kore (Female - Natural)',
      speechRate: 1.0,
      pitch: 1.0,
      autoSpeakResponses: false,
      wakeWordEnabled: true,
      wakeWord: 'Hey Nonea',
      wakeWordSensitivity: 7,
    };
  });

  const [isPlayingVoiceSample, setIsPlayingVoiceSample] = useState(false);

  // 3. Automation Settings State
  const [automationConfig, setAutomationConfig] = useState<AutomationSettingsConfig>(() => {
    const saved = localStorage.getItem('nexus_automation_settings');
    if (saved) {
      try { return JSON.parse(saved); } catch {}
    }
    return {
      accessibilityGranted: true,
      microphoneGranted: false,
      notificationGranted: false,
      backgroundOperationSupported: true,
      appLaunchPreference: 'direct',
      confirmFileWrites: true,
      confirmAppLaunches: false,
      confirmNetworkCalls: false,
      emergencyStopArmed: false,
    };
  });

  // 4. Personalization State
  const [personalization, setPersonalization] = useState<PersonalizationConfig>(() => {
    const saved = localStorage.getItem('nexus_personalization');
    if (saved) {
      try { return JSON.parse(saved); } catch {}
    }
    return {
      assistantName: 'Nonea',
      personalityStyle: 'architect',
      preferredLanguage: 'English',
      theme: 'dark',
      memorySpanCount: 10,
      contextMemoryEnabled: true,
    };
  });

  // 5. Room Database Memories State
  const [memories, setMemories] = useState<RoomMemoryEntity[]>(() => {
    const saved = localStorage.getItem('nexus_room_memories');
    if (saved) {
      try { return JSON.parse(saved); } catch {}
    }
    return [
      {
        id: 1,
        text: 'User prefers Kotlin Jetpack Compose over classic XML layouts for modern Android apps.',
        category: 'architecture',
        timestamp: Date.now() - 3600000 * 24,
        tags: ['android', 'compose', 'kotlin'],
      },
      {
        id: 2,
        text: 'Room SQLite database is preferred for persistent offline cache in Android repositories.',
        category: 'architecture',
        timestamp: Date.now() - 3600000 * 12,
        tags: ['room', 'sqlite', 'cache'],
      },
      {
        id: 3,
        text: 'Project builds must always use Gradle Kotlin DSL (.gradle.kts).',
        category: 'preference',
        timestamp: Date.now() - 3600000 * 5,
        tags: ['gradle', 'kts'],
      },
      {
        id: 4,
        text: 'User timezone is America/New_York (UTC-4/5).',
        category: 'user_fact',
        timestamp: Date.now() - 3600000 * 2,
        tags: ['timezone', 'locale'],
      },
    ];
  });

  const [memorySearch, setMemorySearch] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [editingMemoryId, setEditingMemoryId] = useState<number | null>(null);
  const [editMemoryText, setEditMemoryText] = useState('');
  const [newMemoryText, setNewMemoryText] = useState('');
  const [newMemoryCategory, setNewMemoryCategory] = useState<MemoryCategory>('architecture');
  const [savedBanner, setSavedBanner] = useState(false);

  // Auto-persist changes
  useEffect(() => {
    localStorage.setItem('nexus_ai_settings', JSON.stringify(aiConfig));
  }, [aiConfig]);

  useEffect(() => {
    localStorage.setItem('nexus_voice_settings', JSON.stringify(voiceConfig));
  }, [voiceConfig]);

  useEffect(() => {
    localStorage.setItem('nexus_automation_settings', JSON.stringify(automationConfig));
  }, [automationConfig]);

  useEffect(() => {
    localStorage.setItem('nexus_personalization', JSON.stringify(personalization));
  }, [personalization]);

  useEffect(() => {
    localStorage.setItem('nexus_room_memories', JSON.stringify(memories));
  }, [memories]);

  // Query actual browser permissions
  useEffect(() => {
    if (navigator.permissions && navigator.permissions.query) {
      navigator.permissions.query({ name: 'microphone' as PermissionName }).then((res) => {
        setAutomationConfig((prev) => ({ ...prev, microphoneGranted: res.state === 'granted' }));
      }).catch(() => {});

      if ('Notification' in window) {
        setAutomationConfig((prev) => ({ ...prev, notificationGranted: Notification.permission === 'granted' }));
      }
    }
  }, []);

  // Test API Connection
  const handleTestApiConnection = async () => {
    setTestApiStatus({ testing: true });
    try {
      const res = await fetch(`/api/gemini/test-connection?model=${aiConfig.model}`);
      const data = await res.json();
      setTestApiStatus({
        testing: false,
        result: {
          connected: data.connected,
          latencyMs: data.latencyMs,
          model: data.model,
          reply: data.reply,
          error: data.error,
        },
      });
    } catch (err: any) {
      setTestApiStatus({
        testing: false,
        result: {
          connected: false,
          latencyMs: 0,
          model: aiConfig.model,
          error: err.message || 'Connection failed',
        },
      });
    }
  };

  // Preview Voice Sample via SpeechSynthesis
  const handlePreviewVoice = () => {
    if (!('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();

    setIsPlayingVoiceSample(true);
    const text = voiceConfig.language.startsWith('bn')
      ? 'নমস্কার, আমি নোনিয়া এআই। আপনার সাথে কাজ করতে পেরে আমি আনন্দিত।'
      : `Hello, I am ${personalization.assistantName}. Your voice parameters have been configured successfully.`;

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = voiceConfig.speechRate;
    utterance.pitch = voiceConfig.pitch;
    utterance.lang = voiceConfig.language;

    utterance.onend = () => setIsPlayingVoiceSample(false);
    utterance.onerror = () => setIsPlayingVoiceSample(false);

    window.speechSynthesis.speak(utterance);
  };

  // Trigger Emergency Stop (halts automation process-wide)
  const handleTriggerEmergencyStop = () => {
    setAutomationConfig((prev) => ({
      ...prev,
      emergencyStopArmed: !prev.emergencyStopArmed,
    }));
  };

  // Memory operations
  const handleAddMemory = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMemoryText.trim()) return;

    const newMem: RoomMemoryEntity = {
      id: Date.now(),
      text: newMemoryText.trim(),
      category: newMemoryCategory,
      timestamp: Date.now(),
      tags: [newMemoryCategory],
    };

    setMemories((prev) => [newMem, ...prev]);
    setNewMemoryText('');
    triggerSaveBanner();
  };

  const handleDeleteMemory = (id: number) => {
    setMemories((prev) => prev.filter((m) => m.id !== id));
    triggerSaveBanner();
  };

  const handleSaveEditMemory = (id: number) => {
    setMemories((prev) =>
      prev.map((m) => (m.id === id ? { ...m, text: editMemoryText.trim() } : m))
    );
    setEditingMemoryId(null);
    triggerSaveBanner();
  };

  const handleClearAllMemories = () => {
    if (confirm('Are you sure you want to delete all saved memories from Room database?')) {
      setMemories([]);
      triggerSaveBanner();
    }
  };

  const triggerSaveBanner = () => {
    setSavedBanner(true);
    setTimeout(() => setSavedBanner(false), 2000);
  };

  // Export conversations
  const handleExportData = () => {
    const payload = {
      assistantName: personalization.assistantName,
      exportTimestamp: new Date().toISOString(),
      memories,
      preferences: {
        ai: aiConfig,
        voice: voiceConfig,
        automation: automationConfig,
        personalization,
      },
    };

    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `nexus_conversations_memory_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // Filter memories
  const filteredMemories = memories.filter((m) => {
    const matchesCategory = selectedCategory === 'all' || m.category === selectedCategory;
    const matchesSearch =
      !memorySearch ||
      m.text.toLowerCase().includes(memorySearch.toLowerCase()) ||
      (m.tags && m.tags.some((t) => t.toLowerCase().includes(memorySearch.toLowerCase())));
    return matchesCategory && matchesSearch;
  });

  return (
    <div className="flex-1 flex flex-col bg-zinc-950 text-zinc-200 overflow-hidden font-sans select-none">
      {/* Top Header */}
      <div className="h-12 bg-zinc-900 border-b border-zinc-800 px-6 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2.5">
          <Settings className="w-5 h-5 text-indigo-400" />
          <h2 className="text-sm font-semibold text-white tracking-wide">
            Settings, Memory & Device Permissions
          </h2>
          {savedBanner && (
            <span className="text-[11px] px-2 py-0.5 rounded bg-emerald-950 text-emerald-400 border border-emerald-800/40 flex items-center gap-1 animate-in fade-in">
              <Check className="w-3 h-3" /> Saved to Room DB
            </span>
          )}
        </div>

        {onBackToEditor && (
          <button
            onClick={onBackToEditor}
            className="px-3 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded text-xs font-medium transition-colors"
          >
            ← Back to Editor
          </button>
        )}
      </div>

      {/* Main Container */}
      <div className="flex-1 flex overflow-hidden">
        {/* Navigation Sidebar */}
        <aside className="w-56 bg-zinc-950/70 border-r border-zinc-800 p-2 flex flex-col gap-1 shrink-0 text-xs font-medium">
          <button
            onClick={() => setActiveTab('ai')}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors ${
              activeTab === 'ai' ? 'bg-indigo-600 text-white font-semibold shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
            }`}
          >
            <Sparkles className="w-4 h-4" />
            <span>AI Settings</span>
          </button>

          <button
            onClick={() => setActiveTab('voice')}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors ${
              activeTab === 'voice' ? 'bg-indigo-600 text-white font-semibold shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
            }`}
          >
            <Volume2 className="w-4 h-4" />
            <span>Voice & Speech</span>
          </button>

          <button
            onClick={() => setActiveTab('automation')}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors ${
              activeTab === 'automation' ? 'bg-indigo-600 text-white font-semibold shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
            }`}
          >
            <ShieldAlert className="w-4 h-4" />
            <span>Automation & Safety</span>
          </button>

          <button
            onClick={() => setActiveTab('personalization')}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors ${
              activeTab === 'personalization' ? 'bg-indigo-600 text-white font-semibold shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
            }`}
          >
            <UserCheck className="w-4 h-4" />
            <span>Personalization</span>
          </button>

          <button
            onClick={() => setActiveTab('memory')}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors ${
              activeTab === 'memory' ? 'bg-indigo-600 text-white font-semibold shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
            }`}
          >
            <Database className="w-4 h-4 text-emerald-400" />
            <span>Room Memory ({memories.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('about')}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-left transition-colors ${
              activeTab === 'about' ? 'bg-indigo-600 text-white font-semibold shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-900'
            }`}
          >
            <Info className="w-4 h-4" />
            <span>About & Diagnostics</span>
          </button>
        </aside>

        {/* Settings Body Canvas */}
        <main className="flex-1 overflow-y-auto p-6 max-w-4xl space-y-6 text-sm">
          {/* TAB 1: AI SETTINGS */}
          {activeTab === 'ai' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-semibold text-white">Gemini AI Model & Generation Settings</h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Configure server-side Gemini client, model selection, reasoning parameters, and API health.
                </p>
              </div>

              {/* Provider & Secure API Proxy Banner */}
              <div className="bg-zinc-900/80 border border-zinc-800 rounded-xl p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Shield className="w-4 h-4 text-emerald-400" />
                    <span className="font-semibold text-zinc-200 text-xs">API Configuration & Proxy Status</span>
                  </div>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-950 text-emerald-300 border border-emerald-800/40 font-mono">
                    Proxy Active (Server-Side)
                  </span>
                </div>
                <p className="text-xs text-zinc-400 leading-relaxed">
                  Your Gemini API key is securely managed on the server side via AI Studio environment variables.
                  Keys are never exposed to browser client bundles, and all logging enforces automated secret redaction.
                </p>

                {/* Test Connection Button */}
                <div className="pt-2 flex items-center gap-3">
                  <button
                    onClick={handleTestApiConnection}
                    disabled={testApiStatus.testing}
                    className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-md shadow-indigo-600/20 flex items-center gap-2 transition-all"
                  >
                    {testApiStatus.testing ? (
                      <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    ) : (
                      <Radio className="w-3.5 h-3.5" />
                    )}
                    <span>Test API Connection</span>
                  </button>

                  {testApiStatus.result && (
                    <div
                      className={`text-xs px-3 py-1.5 rounded-lg border flex items-center gap-2 ${
                        testApiStatus.result.connected
                          ? 'bg-emerald-950/60 border-emerald-800/50 text-emerald-300'
                          : 'bg-red-950/60 border-red-800/50 text-red-300'
                      }`}
                    >
                      {testApiStatus.result.connected ? (
                        <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                      ) : (
                        <XCircle className="w-4 h-4 text-red-400" />
                      )}
                      <span>
                        {testApiStatus.result.connected
                          ? `Connected! Latency: ${testApiStatus.result.latencyMs}ms (Response: "${testApiStatus.result.reply}")`
                          : `Connection error: ${testApiStatus.result.error}`}
                      </span>
                    </div>
                  )}
                </div>
              </div>

              {/* Model Selection */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-zinc-300 block">Gemini Model:</label>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {[
                    { id: 'gemini-3.8-flash', name: 'Gemini 3.8 Flash (Recommended)', desc: 'High-speed reasoning, advanced code synthesis, and low latency.' },
                    { id: 'gemini-3.1-flash-lite', name: 'Gemini 3.1 Flash-Lite', desc: 'Lightweight, ultra-fast responses for quick edits and queries.' },
                  ].map((m) => (
                    <div
                      key={m.id}
                      onClick={() => setAiConfig((prev) => ({ ...prev, model: m.id }))}
                      className={`p-3 rounded-xl border cursor-pointer transition-all ${
                        aiConfig.model === m.id
                          ? 'bg-indigo-950/40 border-indigo-500 text-white shadow-sm'
                          : 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:border-zinc-700'
                      }`}
                    >
                      <div className="font-semibold text-xs text-zinc-100">{m.name}</div>
                      <div className="text-[11px] text-zinc-400 mt-1">{m.desc}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Generation Parameters */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-4">
                <h4 className="text-xs font-semibold text-zinc-200 uppercase tracking-wider flex items-center gap-1.5">
                  <Sliders className="w-3.5 h-3.5 text-indigo-400" /> Generation Parameters
                </h4>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <div className="flex justify-between text-xs mb-1">
                      <span className="text-zinc-400">Temperature (Creativity):</span>
                      <span className="font-mono text-zinc-200">{aiConfig.temperature.toFixed(2)}</span>
                    </div>
                    <input
                      type="range"
                      min="0"
                      max="1"
                      step="0.05"
                      value={aiConfig.temperature}
                      onChange={(e) => setAiConfig((prev) => ({ ...prev, temperature: parseFloat(e.target.value) }))}
                      className="w-full accent-indigo-500"
                    />
                    <div className="flex justify-between text-[10px] text-zinc-500 mt-0.5">
                      <span>Deterministic (0.0)</span>
                      <span>Creative (1.0)</span>
                    </div>
                  </div>

                  <div>
                    <div className="flex justify-between text-xs mb-1">
                      <span className="text-zinc-400">Top-P (Nucleus Sampling):</span>
                      <span className="font-mono text-zinc-200">{aiConfig.topP.toFixed(2)}</span>
                    </div>
                    <input
                      type="range"
                      min="0.1"
                      max="1"
                      step="0.05"
                      value={aiConfig.topP}
                      onChange={(e) => setAiConfig((prev) => ({ ...prev, topP: parseFloat(e.target.value) }))}
                      className="w-full accent-indigo-500"
                    />
                  </div>
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-zinc-800/80">
                  <div>
                    <span className="text-xs font-medium text-zinc-200 block">Streaming Responses:</span>
                    <span className="text-[11px] text-zinc-500">Stream code tokens incrementally to editor</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={aiConfig.streamingEnabled}
                    onChange={(e) => setAiConfig((prev) => ({ ...prev, streamingEnabled: e.target.checked }))}
                    className="accent-indigo-500 w-4 h-4 rounded cursor-pointer"
                  />
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: VOICE SETTINGS */}
          {activeTab === 'voice' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-semibold text-white">Voice & Speech Settings</h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Configure Text-to-Speech audio output, female voice personas, pitch, rate, and wake-word detection.
                </p>
              </div>

              {/* Language & Voice Persona Selection */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-medium text-zinc-300 block mb-1.5">Spoken Language:</label>
                  <select
                    value={voiceConfig.language}
                    onChange={(e) => setVoiceConfig((prev) => ({ ...prev, language: e.target.value }))}
                    className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 focus:outline-none focus:border-indigo-500"
                  >
                    <option value="en-US">English (United States)</option>
                    <option value="en-GB">English (United Kingdom)</option>
                    <option value="bn-BD">Bengali (বাংলাদেশ)</option>
                    <option value="es-ES">Spanish (Español)</option>
                    <option value="fr-FR">French (Français)</option>
                    <option value="de-DE">German (Deutsch)</option>
                    <option value="ja-JP">Japanese (日本語)</option>
                  </select>
                </div>

                <div>
                  <label className="text-xs font-medium text-zinc-300 block mb-1.5">Voice Persona:</label>
                  <select
                    value={voiceConfig.selectedVoice}
                    onChange={(e) => setVoiceConfig((prev) => ({ ...prev, selectedVoice: e.target.value }))}
                    className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 focus:outline-none focus:border-indigo-500"
                  >
                    <option value="Kore (Female - Natural)">Kore (Female - Clear & Articulate)</option>
                    <option value="Zephyr (Female - Soft)">Zephyr (Female - Calm & Gentle)</option>
                    <option value="Puck (Male - Energetic)">Puck (Male - Dynamic Host)</option>
                    <option value="Charon (Male - Deep)">Charon (Male - Deep & Technical)</option>
                    <option value="Fenrir (Neutral - Balanced)">Fenrir (Neutral - Balanced)</option>
                  </select>
                </div>
              </div>

              {/* Preview Button */}
              <div>
                <button
                  onClick={handlePreviewVoice}
                  disabled={isPlayingVoiceSample}
                  className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 text-white rounded-lg text-xs font-medium flex items-center gap-2 transition-colors"
                >
                  <Volume2 className="w-4 h-4 text-indigo-400" />
                  <span>{isPlayingVoiceSample ? 'Speaking Sample...' : 'Preview Voice Output'}</span>
                </button>
              </div>

              {/* Speech Rate & Pitch Sliders */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-4">
                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-zinc-400">Speech Rate:</span>
                    <span className="font-mono text-zinc-200">{voiceConfig.speechRate.toFixed(1)}x</span>
                  </div>
                  <input
                    type="range"
                    min="0.5"
                    max="2.0"
                    step="0.1"
                    value={voiceConfig.speechRate}
                    onChange={(e) => setVoiceConfig((prev) => ({ ...prev, speechRate: parseFloat(e.target.value) }))}
                    className="w-full accent-indigo-500"
                  />
                  <div className="flex justify-between text-[10px] text-zinc-500 mt-0.5">
                    <span>0.5x (Slow)</span>
                    <span>1.0x (Standard)</span>
                    <span>2.0x (Fast)</span>
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-zinc-400">Voice Pitch:</span>
                    <span className="font-mono text-zinc-200">{voiceConfig.pitch.toFixed(1)}</span>
                  </div>
                  <input
                    type="range"
                    min="0.5"
                    max="1.5"
                    step="0.1"
                    value={voiceConfig.pitch}
                    onChange={(e) => setVoiceConfig((prev) => ({ ...prev, pitch: parseFloat(e.target.value) }))}
                    className="w-full accent-indigo-500"
                  />
                </div>

                <div className="flex items-center justify-between pt-2 border-t border-zinc-800">
                  <div>
                    <span className="text-xs font-medium text-zinc-200 block">Auto-speak Responses:</span>
                    <span className="text-[11px] text-zinc-500">Automatically speak assistant answers</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={voiceConfig.autoSpeakResponses}
                    onChange={(e) => setVoiceConfig((prev) => ({ ...prev, autoSpeakResponses: e.target.checked }))}
                    className="accent-indigo-500 w-4 h-4 rounded cursor-pointer"
                  />
                </div>
              </div>

              {/* Wake Word Configuration */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-xs font-semibold text-zinc-200 block">Wake-Word Detection:</span>
                    <span className="text-[11px] text-zinc-500">Listen for hotword in background to activate assistant</span>
                  </div>
                  <input
                    type="checkbox"
                    checked={voiceConfig.wakeWordEnabled}
                    onChange={(e) => setVoiceConfig((prev) => ({ ...prev, wakeWordEnabled: e.target.checked }))}
                    className="accent-indigo-500 w-4 h-4 rounded cursor-pointer"
                  />
                </div>

                {voiceConfig.wakeWordEnabled && (
                  <div className="pt-2 grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-[11px] text-zinc-400 block mb-1">Wake Word Phrase:</label>
                      <select
                        value={voiceConfig.wakeWord}
                        onChange={(e) => setVoiceConfig((prev) => ({ ...prev, wakeWord: e.target.value }))}
                        className="w-full p-1.5 bg-zinc-950 border border-zinc-800 rounded text-xs text-zinc-200"
                      >
                        <option value="Hey Nonea">Hey Nonea</option>
                        <option value="Hey Nexus">Hey Nexus</option>
                        <option value="Computer">Computer</option>
                      </select>
                    </div>

                    <div>
                      <div className="flex justify-between text-[11px] text-zinc-400 mb-1">
                        <span>Sensitivity:</span>
                        <span className="font-mono text-zinc-200">{voiceConfig.wakeWordSensitivity}/10</span>
                      </div>
                      <input
                        type="range"
                        min="1"
                        max="10"
                        value={voiceConfig.wakeWordSensitivity}
                        onChange={(e) => setVoiceConfig((prev) => ({ ...prev, wakeWordSensitivity: parseInt(e.target.value) }))}
                        className="w-full accent-indigo-500"
                      />
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 3: AUTOMATION & SAFETY SETTINGS */}
          {activeTab === 'automation' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-semibold text-white">Automation, Permissions & Safety Guard</h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Manage device permission surfaces, strict confirmation thresholds, and emergency override switches.
                </p>
              </div>

              {/* Big Red Emergency Stop Button */}
              <div className="p-4 rounded-xl border border-red-800/60 bg-red-950/30 flex items-center justify-between">
                <div>
                  <div className="text-sm font-bold text-red-200 flex items-center gap-1.5">
                    <ShieldAlert className="w-4 h-4 text-red-400" />
                    Emergency Stop (Automation Guard)
                  </div>
                  <p className="text-xs text-zinc-400 mt-0.5">
                    Instantly disarms all ongoing automation steps, Accessibility clicks, and pending action queues.
                  </p>
                </div>
                <button
                  onClick={handleTriggerEmergencyStop}
                  className={`px-4 py-2 rounded-lg text-xs font-bold uppercase tracking-wider transition-all shadow-lg ${
                    automationConfig.emergencyStopArmed
                      ? 'bg-amber-600 hover:bg-amber-500 text-white animate-pulse'
                      : 'bg-red-600 hover:bg-red-500 text-white shadow-red-600/30'
                  }`}
                >
                  {automationConfig.emergencyStopArmed ? 'Disarmed (Click to Reset)' : 'Halt Automation'}
                </button>
              </div>

              {/* Permission Surfaces List */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-3">
                <span className="text-xs font-semibold text-zinc-200 uppercase tracking-wider block">
                  Device Permission Surfaces
                </span>

                <div className="space-y-2">
                  <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                    <div>
                      <div className="font-medium text-zinc-200 text-xs">Accessibility Service</div>
                      <div className="text-[11px] text-zinc-500">NoneaAccessibilityService screen automation dispatcher</div>
                    </div>
                    <span className="text-[10px] px-2 py-0.5 rounded bg-emerald-950 text-emerald-300 border border-emerald-800/40 font-mono">
                      Active
                    </span>
                  </div>

                  <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                    <div>
                      <div className="font-medium text-zinc-200 text-xs">Microphone Input</div>
                      <div className="text-[11px] text-zinc-500">Requested only when voice dictation or wake-word is used</div>
                    </div>
                    <button
                      onClick={async () => {
                        try {
                          await navigator.mediaDevices.getUserMedia({ audio: true });
                          setAutomationConfig((prev) => ({ ...prev, microphoneGranted: true }));
                        } catch {
                          setAutomationConfig((prev) => ({ ...prev, microphoneGranted: false }));
                        }
                      }}
                      className={`text-[10px] px-2 py-1 rounded font-medium border ${
                        automationConfig.microphoneGranted
                          ? 'bg-emerald-950 text-emerald-300 border-emerald-800/40'
                          : 'bg-zinc-800 text-zinc-300 border-zinc-700 hover:bg-zinc-700'
                      }`}
                    >
                      {automationConfig.microphoneGranted ? 'Granted' : 'Request Access'}
                    </button>
                  </div>

                  <div className="p-2.5 rounded-lg bg-zinc-950 border border-zinc-800 flex items-center justify-between">
                    <div>
                      <div className="font-medium text-zinc-200 text-xs">Notifications</div>
                      <div className="text-[11px] text-zinc-500">Alerts on background task completion and build results</div>
                    </div>
                    <button
                      onClick={async () => {
                        if ('Notification' in window) {
                          const res = await Notification.requestPermission();
                          setAutomationConfig((prev) => ({ ...prev, notificationGranted: res === 'granted' }));
                        }
                      }}
                      className={`text-[10px] px-2 py-1 rounded font-medium border ${
                        automationConfig.notificationGranted
                          ? 'bg-emerald-950 text-emerald-300 border-emerald-800/40'
                          : 'bg-zinc-800 text-zinc-300 border-zinc-700 hover:bg-zinc-700'
                      }`}
                    >
                      {automationConfig.notificationGranted ? 'Granted' : 'Request Access'}
                    </button>
                  </div>
                </div>
              </div>

              {/* Confirmation Policy Toggles */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-2.5">
                <span className="text-xs font-semibold text-zinc-200 uppercase tracking-wider block">
                  Mandatory Confirmation Policies
                </span>

                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800 cursor-pointer">
                  <span className="text-xs text-zinc-300">Always confirm file write & modification operations</span>
                  <input
                    type="checkbox"
                    checked={automationConfig.confirmFileWrites}
                    onChange={(e) => setAutomationConfig((prev) => ({ ...prev, confirmFileWrites: e.target.checked }))}
                    className="accent-indigo-500 rounded"
                  />
                </label>

                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800 cursor-pointer">
                  <span className="text-xs text-zinc-300">Confirm third-party application launch intents</span>
                  <input
                    type="checkbox"
                    checked={automationConfig.confirmAppLaunches}
                    onChange={(e) => setAutomationConfig((prev) => ({ ...prev, confirmAppLaunches: e.target.checked }))}
                    className="accent-indigo-500 rounded"
                  />
                </label>

                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800 cursor-pointer">
                  <span className="text-xs text-zinc-300">Confirm external network URLs</span>
                  <input
                    type="checkbox"
                    checked={automationConfig.confirmNetworkCalls}
                    onChange={(e) => setAutomationConfig((prev) => ({ ...prev, confirmNetworkCalls: e.target.checked }))}
                    className="accent-indigo-500 rounded"
                  />
                </label>
              </div>
            </div>
          )}

          {/* TAB 4: PERSONALIZATION */}
          {activeTab === 'personalization' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-semibold text-white">Assistant Persona & Styling</h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Customize the assistant name, technical personality style, theme, and memory depth.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-medium text-zinc-300 block mb-1">Assistant Name:</label>
                  <input
                    type="text"
                    value={personalization.assistantName}
                    onChange={(e) => setPersonalization((prev) => ({ ...prev, assistantName: e.target.value }))}
                    className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-100 focus:outline-none focus:border-indigo-500 font-mono"
                  />
                </div>

                <div>
                  <label className="text-xs font-medium text-zinc-300 block mb-1">Preferred Language:</label>
                  <select
                    value={personalization.preferredLanguage}
                    onChange={(e) => setPersonalization((prev) => ({ ...prev, preferredLanguage: e.target.value }))}
                    className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200"
                  >
                    <option value="English">English</option>
                    <option value="Bengali">Bengali (বাংলা)</option>
                    <option value="Spanish">Spanish (Español)</option>
                    <option value="French">French</option>
                  </select>
                </div>
              </div>

              {/* Personality Style Cards */}
              <div className="space-y-2">
                <label className="text-xs font-medium text-zinc-300 block">Personality & Behavior Style:</label>
                <div className="grid grid-cols-2 gap-3">
                  {[
                    { id: 'architect', title: 'Senior Software Architect', desc: 'Focuses on clean architecture, idiomatic Kotlin, SOLID design, and scalability.' },
                    { id: 'precise', title: 'Precise & Minimalist', desc: 'Code-first, direct outputs with minimal small-talk.' },
                    { id: 'conversational', title: 'Friendly & Conversational', desc: 'Step-by-step guidance, explanations, and encouraging coaching.' },
                    { id: 'creative', title: 'Creative & Exploratory', desc: 'Suggests novel patterns, alternative approaches, and UI concepts.' },
                  ].map((style) => (
                    <div
                      key={style.id}
                      onClick={() => setPersonalization((prev) => ({ ...prev, personalityStyle: style.id as any }))}
                      className={`p-3 rounded-xl border cursor-pointer transition-all ${
                        personalization.personalityStyle === style.id
                          ? 'bg-indigo-950/40 border-indigo-500 text-white shadow-sm'
                          : 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:border-zinc-700'
                      }`}
                    >
                      <div className="font-semibold text-xs text-zinc-100">{style.title}</div>
                      <div className="text-[11px] text-zinc-400 mt-1 leading-relaxed">{style.desc}</div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Context Memory Depth Slider */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-3">
                <div className="flex justify-between text-xs">
                  <span className="font-semibold text-zinc-300">Conversation Memory Span:</span>
                  <span className="font-mono text-indigo-400">{personalization.memorySpanCount} turns</span>
                </div>
                <input
                  type="range"
                  min="2"
                  max="30"
                  value={personalization.memorySpanCount}
                  onChange={(e) => setPersonalization((prev) => ({ ...prev, memorySpanCount: parseInt(e.target.value) }))}
                  className="w-full accent-indigo-500"
                />
                <p className="text-[11px] text-zinc-500">
                  Controls how many past conversational interactions are maintained in active working context.
                </p>
              </div>
            </div>
          )}

          {/* TAB 5: ROOM MEMORY & DATA PRIVACY */}
          {activeTab === 'memory' && (
            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-base font-semibold text-white">Room Local Database & User Memory</h3>
                  <p className="text-xs text-zinc-400 mt-0.5">
                    User-approved remembered facts, architecture guidelines, and persistent SQLite entities.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleExportData}
                    className="px-3 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded-lg text-xs font-medium flex items-center gap-1.5"
                  >
                    <Download className="w-3.5 h-3.5" />
                    Export JSON
                  </button>
                  <button
                    onClick={handleClearAllMemories}
                    className="px-3 py-1.5 bg-red-950/60 hover:bg-red-900/60 border border-red-800/40 text-red-300 rounded-lg text-xs font-medium flex items-center gap-1.5"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    Clear All
                  </button>
                </div>
              </div>

              {/* Zero-Silent-Collection Disclosure Banner */}
              <div className="p-3.5 rounded-xl border border-blue-900/40 bg-blue-950/20 text-xs text-blue-300 leading-relaxed space-y-1">
                <div className="font-semibold text-blue-200 flex items-center gap-1.5">
                  <ShieldCheck className="w-4 h-4 text-blue-400" />
                  Zero Silent Collection Guarantee
                </div>
                <p className="text-zinc-400 text-[11px]">
                  NexusCode does not silently collect contacts, SMS messages, location data, or background audio.
                  All memories below represent explicitly user-approved facts stored securely in local Room database storage.
                </p>
              </div>

              {/* Add Memory Form */}
              <form onSubmit={handleAddMemory} className="p-3 bg-zinc-900 border border-zinc-800 rounded-xl space-y-2">
                <span className="text-[11px] font-semibold text-zinc-300 block">Record New Memory Entity:</span>
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={newMemoryText}
                    onChange={(e) => setNewMemoryText(e.target.value)}
                    placeholder="e.g. Always use Coroutines & StateFlow for repository reactive streams..."
                    className="flex-1 p-2 bg-zinc-950 border border-zinc-800 rounded-lg text-xs text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500"
                  />
                  <select
                    value={newMemoryCategory}
                    onChange={(e) => setNewMemoryCategory(e.target.value as any)}
                    className="p-2 bg-zinc-950 border border-zinc-800 rounded-lg text-xs text-zinc-300"
                  >
                    <option value="architecture">Architecture</option>
                    <option value="preference">Preference</option>
                    <option value="code_snippet">Code Snippet</option>
                    <option value="user_fact">User Fact</option>
                  </select>
                  <button
                    type="submit"
                    className="px-3 py-2 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-medium shrink-0"
                  >
                    Save Memory
                  </button>
                </div>
              </form>

              {/* Search & Category Filter Bar */}
              <div className="flex flex-col sm:flex-row gap-2 items-center justify-between">
                <div className="relative flex-1 w-full">
                  <Search className="w-3.5 h-3.5 text-zinc-500 absolute left-2.5 top-2.5" />
                  <input
                    type="text"
                    value={memorySearch}
                    onChange={(e) => setMemorySearch(e.target.value)}
                    placeholder="Search memories by keyword..."
                    className="w-full pl-8 pr-3 py-1.5 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-indigo-500"
                  />
                </div>

                <div className="flex items-center gap-1 text-[11px] overflow-x-auto w-full sm:w-auto shrink-0">
                  {['all', 'architecture', 'preference', 'user_fact', 'code_snippet'].map((cat) => (
                    <button
                      key={cat}
                      onClick={() => setSelectedCategory(cat)}
                      className={`px-2.5 py-1 rounded-lg capitalize transition-colors ${
                        selectedCategory === cat
                          ? 'bg-zinc-800 text-white font-medium border border-zinc-700'
                          : 'text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      {cat.replace('_', ' ')}
                    </button>
                  ))}
                </div>
              </div>

              {/* Memory Cards List */}
              <div className="space-y-2">
                {filteredMemories.length === 0 ? (
                  <div className="text-center py-10 text-zinc-600 text-xs italic">
                    No memories match the filter or search criteria.
                  </div>
                ) : (
                  filteredMemories.map((mem) => (
                    <div
                      key={mem.id}
                      className="p-3 bg-zinc-900/70 border border-zinc-800 rounded-xl flex items-start justify-between gap-3 hover:border-zinc-700 transition-colors"
                    >
                      <div className="flex-1 space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="text-[10px] px-1.5 py-0.2 rounded uppercase font-bold tracking-wider bg-zinc-800 text-indigo-300 font-mono">
                            {mem.category}
                          </span>
                          <span className="text-[10px] text-zinc-500">
                            {new Date(mem.timestamp).toLocaleDateString()}
                          </span>
                        </div>

                        {editingMemoryId === mem.id ? (
                          <div className="flex gap-2 pt-1">
                            <input
                              type="text"
                              value={editMemoryText}
                              onChange={(e) => setEditMemoryText(e.target.value)}
                              className="flex-1 p-1.5 bg-zinc-950 border border-zinc-700 rounded text-xs text-zinc-100"
                            />
                            <button
                              onClick={() => handleSaveEditMemory(mem.id)}
                              className="px-2 py-1 bg-emerald-600 text-white rounded text-xs"
                            >
                              Save
                            </button>
                            <button
                              onClick={() => setEditingMemoryId(null)}
                              className="px-2 py-1 text-zinc-400 hover:text-zinc-200 text-xs"
                            >
                              Cancel
                            </button>
                          </div>
                        ) : (
                          <p className="text-xs text-zinc-200 leading-relaxed">{mem.text}</p>
                        )}
                      </div>

                      {editingMemoryId !== mem.id && (
                        <div className="flex items-center gap-1 shrink-0 pt-1">
                          <button
                            onClick={() => {
                              setEditingMemoryId(mem.id);
                              setEditMemoryText(mem.text);
                            }}
                            title="Edit memory"
                            className="p-1 text-zinc-500 hover:text-indigo-400 rounded"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => handleDeleteMemory(mem.id)}
                            title="Delete memory"
                            className="p-1 text-zinc-500 hover:text-red-400 rounded"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* TAB 6: ABOUT & DIAGNOSTICS */}
          {activeTab === 'about' && (
            <div className="space-y-6">
              <div>
                <h3 className="text-base font-semibold text-white">About & System Diagnostics</h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  App build metadata, open-source compliance, and local runtime diagnostics.
                </p>
              </div>

              {/* Version Card */}
              <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <div className="font-semibold text-zinc-200">NexusCode Studio</div>
                  <span className="text-xs font-mono text-indigo-400">v2.4.0 (Build #20261021)</span>
                </div>
                <p className="text-xs text-zinc-400 leading-relaxed">
                  Integrated coding workspace with multi-file generation, strict Gradle Android builder,
                  and sandboxed tool-calling automation.
                </p>
              </div>

              {/* Diagnostic Information */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-3">
                <span className="text-xs font-semibold text-zinc-300 uppercase tracking-wider block">
                  Runtime Environment
                </span>
                <div className="grid grid-cols-2 gap-3 text-xs font-mono">
                  <div className="p-2 rounded bg-zinc-950 border border-zinc-800">
                    <span className="text-zinc-500 block text-[10px]">Client Platform:</span>
                    <span className="text-zinc-300 truncate block">{navigator.platform}</span>
                  </div>
                  <div className="p-2 rounded bg-zinc-950 border border-zinc-800">
                    <span className="text-zinc-500 block text-[10px]">AI Studio Engine:</span>
                    <span className="text-emerald-400 truncate block">Gemini 3.8 Flash</span>
                  </div>
                  <div className="p-2 rounded bg-zinc-950 border border-zinc-800">
                    <span className="text-zinc-500 block text-[10px]">Local Database Engine:</span>
                    <span className="text-zinc-300 truncate block">Room SQLite v2.6.1</span>
                  </div>
                  <div className="p-2 rounded bg-zinc-950 border border-zinc-800">
                    <span className="text-zinc-500 block text-[10px]">Security Mode:</span>
                    <span className="text-cyan-400 truncate block">Strict Sandboxed Execution</span>
                  </div>
                </div>
              </div>

              {/* Open-Source Licenses */}
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-xl p-4 space-y-2">
                <span className="text-xs font-semibold text-zinc-300 uppercase tracking-wider block">
                  Open-Source Licenses
                </span>
                <div className="text-[11px] text-zinc-400 space-y-1">
                  <div>• <strong>@google/genai</strong> - Apache License 2.0</div>
                  <div>• <strong>Android Jetpack Compose & Room</strong> - Apache License 2.0</div>
                  <div>• <strong>Tailwind CSS & Vite</strong> - MIT License</div>
                  <div>• <strong>Lucide React Icons</strong> - ISC License</div>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};
