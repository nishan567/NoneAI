import React, { useState } from 'react';
import {
  Smartphone,
  Tablet,
  RotateCw,
  Sun,
  Moon,
  Sparkles,
  Layers,
  Network,
  Send,
  Mic,
  Bot,
  User,
  CheckCircle2,
  Plus,
  RefreshCw,
  Sliders,
  Play,
  Globe,
  ExternalLink,
} from 'lucide-react';
import { ProjectFile } from '../types';

interface Props {
  files: ProjectFile[];
  currentProjectName: string;
  activeFilePath: string;
}

export const DeviceVisualizer: React.FC<Props> = ({
  files,
  currentProjectName,
  activeFilePath,
}) => {
  const [deviceMode, setDeviceMode] = useState<'phone' | 'tablet' | 'architecture'>('phone');
  const [themeMode, setThemeMode] = useState<'dark' | 'light'>('dark');
  const [orientation, setOrientation] = useState<'portrait' | 'landscape'>('portrait');
  const [activeScreenTab, setActiveScreenTab] = useState<'chat' | 'settings' | 'automation'>('chat');

  // Interactive mock state for NoneaAI / Compose screens
  const [chatMessages, setChatMessages] = useState<
    { sender: 'user' | 'assistant'; text: string; time: string }[]
  >([
    {
      sender: 'assistant',
      text: "Hello! I'm NoneaAI, your voice-activated local automation assistant. What can I automate or research for you today?",
      time: '10:42 AM',
    },
    {
      sender: 'user',
      text: 'Launch Spotify and start my Daily Focus playlist, then turn on Do Not Disturb.',
      time: '10:43 AM',
    },
    {
      sender: 'assistant',
      text: 'Routed to LocalRouterApps: Launching Spotify and preparing playback intent. AutomationGuard policy verified.',
      time: '10:43 AM',
    },
  ]);
  const [chatInput, setChatInput] = useState('');
  const [isOrbActive, setIsOrbActive] = useState(true);

  // Interactive mock state for Android Compose Starter tasks
  const [tasks, setTasks] = useState([
    { id: 1, title: 'Initialize Jetpack Compose project', done: true },
    { id: 2, title: 'Configure Gradle KTS build dependencies', done: true },
    { id: 3, title: 'Connect Room SQLite persistent storage', done: false },
    { id: 4, title: 'Integrate Gemini AI assistant actions', done: false },
  ]);
  const [newTaskInput, setNewTaskInput] = useState('');

  const isNonea =
    currentProjectName.toLowerCase().includes('nonea') ||
    files.some((f) => f.path.includes('com/nonea/ai'));

  const isWebProject =
    files.some((f) => f.path.endsWith('.html')) ||
    currentProjectName.toLowerCase().includes('html') ||
    currentProjectName.toLowerCase().includes('web');

  const htmlFile = files.find((f) => f.path.endsWith('index.html')) || files.find((f) => f.path.endsWith('.html'));
  const cssFile = files.find((f) => f.path.endsWith('.css'));
  const jsFile = files.find((f) => f.path.endsWith('.js') && !f.path.includes('worker'));

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim()) return;
    const newMsg = {
      sender: 'user' as const,
      text: chatInput.trim(),
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
    const reply = {
      sender: 'assistant' as const,
      text: `GeminiProvider received: "${chatInput.trim()}". Analyzing action policy with SecretRedactor...`,
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
    setChatMessages((prev) => [...prev, newMsg, reply]);
    setChatInput('');
  };

  const handleAddTask = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskInput.trim()) return;
    setTasks((prev) => [
      ...prev,
      { id: Date.now(), title: newTaskInput.trim(), done: false },
    ]);
    setNewTaskInput('');
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-zinc-950 overflow-hidden select-none">
      {/* Visualizer Top Bar Controls */}
      <div className="h-10 bg-zinc-900 border-b border-zinc-800 px-4 flex items-center justify-between text-xs text-zinc-300 shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 font-semibold text-white">
            <Smartphone className="w-4 h-4 text-emerald-400" />
            <span>Interactive Device Visualizer</span>
          </div>

          <div className="h-4 w-px bg-zinc-800" />

          {/* Mode Tabs */}
          <div className="flex items-center gap-1 bg-zinc-950 p-0.5 rounded-lg border border-zinc-800 text-[11px]">
            <button
              onClick={() => setDeviceMode('phone')}
              className={`px-2.5 py-1 rounded-md font-medium flex items-center gap-1 transition-colors ${
                deviceMode === 'phone'
                  ? 'bg-zinc-800 text-white shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>Mobile Phone</span>
            </button>
            <button
              onClick={() => setDeviceMode('tablet')}
              className={`px-2.5 py-1 rounded-md font-medium flex items-center gap-1 transition-colors ${
                deviceMode === 'tablet'
                  ? 'bg-zinc-800 text-white shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              <Tablet className="w-3.5 h-3.5" />
              <span>Tablet</span>
            </button>
            <button
              onClick={() => setDeviceMode('architecture')}
              className={`px-2.5 py-1 rounded-md font-medium flex items-center gap-1 transition-colors ${
                deviceMode === 'architecture'
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              <Network className="w-3.5 h-3.5" />
              <span>Architecture Graph</span>
            </button>
          </div>
        </div>

        {/* View Controls (Rotation, Dark/Light theme) */}
        {deviceMode !== 'architecture' && (
          <div className="flex items-center gap-2">
            <button
              onClick={() => setOrientation(orientation === 'portrait' ? 'landscape' : 'portrait')}
              title="Rotate Device"
              className="p-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition-colors flex items-center gap-1 text-[11px]"
            >
              <RotateCw className="w-3.5 h-3.5" />
              <span className="hidden sm:inline capitalize">{orientation}</span>
            </button>
            <button
              onClick={() => setThemeMode(themeMode === 'dark' ? 'light' : 'dark')}
              title="Toggle App Theme"
              className="p-1.5 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-zinc-300 transition-colors"
            >
              {themeMode === 'dark' ? <Sun className="w-3.5 h-3.5 text-amber-400" /> : <Moon className="w-3.5 h-3.5 text-blue-400" />}
            </button>
          </div>
        )}
      </div>

      {/* Main Canvas Area */}
      <div className="flex-1 overflow-auto p-4 sm:p-8 flex items-center justify-center bg-zinc-950/90 relative">
        {/* Subtle grid background */}
        <div
          className="absolute inset-0 pointer-events-none opacity-20"
          style={{
            backgroundImage:
              'radial-gradient(circle at 1px 1px, #3f3f46 1px, transparent 0)',
            backgroundSize: '24px 24px',
          }}
        />

        {/* 1. Android / Mobile Device Frame */}
        {deviceMode !== 'architecture' && !isWebProject && (
          <div
            className={`relative transition-all duration-300 shadow-2xl rounded-[44px] border-[10px] border-zinc-800 bg-zinc-900 flex flex-col overflow-hidden ring-1 ring-zinc-700/60 ${
              deviceMode === 'phone'
                ? orientation === 'portrait'
                  ? 'w-[370px] h-[720px]'
                  : 'w-[720px] h-[370px]'
                : orientation === 'portrait'
                ? 'w-[520px] h-[720px]'
                : 'w-[720px] h-[520px]'
            }`}
          >
            {/* Speaker / Punch-hole camera notch */}
            <div className="absolute top-2.5 left-1/2 -translate-x-1/2 z-30 flex items-center justify-center">
              <div className="w-3.5 h-3.5 rounded-full bg-zinc-950 border border-zinc-800 shadow-inner flex items-center justify-center">
                <div className="w-1.5 h-1.5 rounded-full bg-blue-900/40" />
              </div>
            </div>

            {/* Android System Status Bar */}
            <div
              className={`h-7 px-5 flex items-center justify-between text-[11px] font-medium z-20 shrink-0 ${
                themeMode === 'dark' ? 'bg-zinc-950 text-zinc-300' : 'bg-zinc-100 text-zinc-800'
              }`}
            >
              <span>10:44</span>
              <div className="flex items-center gap-1.5 text-[10px]">
                <span>5G</span>
                <span>100%</span>
              </div>
            </div>

            {/* App Screen Container */}
            <div
              className={`flex-1 flex flex-col overflow-hidden ${
                themeMode === 'dark' ? 'bg-zinc-950 text-white' : 'bg-white text-zinc-900'
              }`}
            >
              {/* If NoneaAI Project */}
              {isNonea ? (
                <div className="flex-1 flex flex-col overflow-hidden">
                  {/* Top Bar */}
                  <div
                    className={`px-4 py-3 border-b flex items-center justify-between shrink-0 ${
                      themeMode === 'dark'
                        ? 'bg-zinc-900 border-zinc-800'
                        : 'bg-purple-50 border-purple-100 text-purple-950'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <div className="w-7 h-7 rounded-full bg-purple-600 flex items-center justify-center text-white shadow-md shadow-purple-600/30">
                        <Bot className="w-4 h-4" />
                      </div>
                      <div>
                        <div className="font-semibold text-xs leading-none">NoneaAI Assistant</div>
                        <div className="text-[10px] text-emerald-500 font-medium mt-0.5">
                          ● Voice & Automation Active
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => setActiveScreenTab('chat')}
                        className={`px-2 py-1 rounded text-[10px] font-medium ${
                          activeScreenTab === 'chat'
                            ? 'bg-purple-600 text-white'
                            : 'text-zinc-400 hover:text-white'
                        }`}
                      >
                        Chat
                      </button>
                      <button
                        onClick={() => setActiveScreenTab('automation')}
                        className={`px-2 py-1 rounded text-[10px] font-medium ${
                          activeScreenTab === 'automation'
                            ? 'bg-purple-600 text-white'
                            : 'text-zinc-400 hover:text-white'
                        }`}
                      >
                        Actions
                      </button>
                      <button
                        onClick={() => setActiveScreenTab('settings')}
                        className={`px-2 py-1 rounded text-[10px] font-medium ${
                          activeScreenTab === 'settings'
                            ? 'bg-purple-600 text-white'
                            : 'text-zinc-400 hover:text-white'
                        }`}
                      >
                        Config
                      </button>
                    </div>
                  </div>

                  {/* Body Content based on Active Screen Tab */}
                  {activeScreenTab === 'chat' && (
                    <div className="flex-1 flex flex-col justify-between overflow-hidden">
                      {/* Interactive Orb Animation */}
                      <div className="py-3 flex flex-col items-center justify-center shrink-0">
                        <div
                          onClick={() => setIsOrbActive(!isOrbActive)}
                          className={`w-14 h-14 rounded-full cursor-pointer transition-all duration-500 flex items-center justify-center shadow-lg ${
                            isOrbActive
                              ? 'bg-gradient-to-tr from-purple-600 via-indigo-500 to-cyan-400 shadow-purple-500/50 scale-105 animate-pulse ring-4 ring-purple-500/20'
                              : 'bg-zinc-800 shadow-none scale-95 opacity-60'
                          }`}
                        >
                          <Mic className="w-6 h-6 text-white" />
                        </div>
                        <span className="text-[10px] text-zinc-500 mt-1 font-mono">
                          {isOrbActive ? 'Listening / Ready' : 'Tap to Activate Orb'}
                        </span>
                      </div>

                      {/* Chat messages scrollable */}
                      <div className="flex-1 overflow-y-auto px-4 space-y-2.5 text-xs">
                        {chatMessages.map((msg, idx) => (
                          <div
                            key={idx}
                            className={`flex flex-col ${
                              msg.sender === 'user' ? 'items-end' : 'items-start'
                            }`}
                          >
                            <div
                              className={`max-w-[85%] rounded-2xl px-3 py-2 leading-relaxed ${
                                msg.sender === 'user'
                                  ? 'bg-purple-600 text-white rounded-br-none shadow-md shadow-purple-600/20'
                                  : themeMode === 'dark'
                                  ? 'bg-zinc-900 text-zinc-200 border border-zinc-800 rounded-bl-none'
                                  : 'bg-purple-50 text-purple-950 border border-purple-100 rounded-bl-none'
                              }`}
                            >
                              {msg.text}
                            </div>
                            <span className="text-[9px] text-zinc-500 mt-0.5 px-1">{msg.time}</span>
                          </div>
                        ))}
                      </div>

                      {/* Input field */}
                      <form
                        onSubmit={handleSendMessage}
                        className={`p-2.5 border-t flex items-center gap-1.5 shrink-0 ${
                          themeMode === 'dark' ? 'bg-zinc-900 border-zinc-800' : 'bg-zinc-50 border-zinc-200'
                        }`}
                      >
                        <input
                          type="text"
                          value={chatInput}
                          onChange={(e) => setChatInput(e.target.value)}
                          placeholder="Ask NoneaAI or issue action..."
                          className={`flex-1 px-3 py-1.5 rounded-full text-xs focus:outline-none ${
                            themeMode === 'dark'
                              ? 'bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500'
                              : 'bg-white border border-zinc-300 text-zinc-800 placeholder-zinc-400'
                          }`}
                        />
                        <button
                          type="submit"
                          className="w-7 h-7 rounded-full bg-purple-600 hover:bg-purple-500 text-white flex items-center justify-center shadow-md transition-colors"
                        >
                          <Send className="w-3.5 h-3.5" />
                        </button>
                      </form>
                    </div>
                  )}

                  {activeScreenTab === 'automation' && (
                    <div className="flex-1 p-4 space-y-3 overflow-y-auto text-xs">
                      <div className="font-semibold text-purple-400">Local Automation Actions</div>
                      {[
                        { title: 'Accessibility Service Control', desc: 'NoneaAccessibilityService.kt', active: true },
                        { title: 'YouTube Media Player Links', desc: 'YouTubeLinks.kt router', active: true },
                        { title: 'Voice Input Hotword Guard', desc: 'VoiceTypes.kt engine', active: false },
                        { title: 'Secret Redactor Filter', desc: 'SecretRedactor.kt sanitizer', active: true },
                      ].map((item, i) => (
                        <div
                          key={i}
                          className="p-2.5 rounded-xl border border-zinc-800 bg-zinc-900/60 flex items-center justify-between"
                        >
                          <div>
                            <div className="font-medium text-zinc-200">{item.title}</div>
                            <div className="text-[10px] text-zinc-500 font-mono">{item.desc}</div>
                          </div>
                          <input
                            type="checkbox"
                            checked={item.active}
                            readOnly
                            className="rounded accent-purple-600 w-4 h-4 cursor-pointer"
                          />
                        </div>
                      ))}
                    </div>
                  )}

                  {activeScreenTab === 'settings' && (
                    <div className="flex-1 p-4 space-y-3 overflow-y-auto text-xs">
                      <div className="font-semibold text-purple-400">Gemini & Persona Settings</div>
                      <div className="space-y-2">
                        <div className="p-2.5 rounded-xl border border-zinc-800 bg-zinc-900/60">
                          <label className="text-[11px] text-zinc-400 block mb-1">Active Model</label>
                          <div className="font-mono text-zinc-200 font-semibold">gemini-3.8-flash</div>
                        </div>
                        <div className="p-2.5 rounded-xl border border-zinc-800 bg-zinc-900/60">
                          <label className="text-[11px] text-zinc-400 block mb-1">Persona Tone</label>
                          <div className="text-zinc-200">Helpful, Concise, Local Automation Expert</div>
                        </div>
                        <div className="p-2.5 rounded-xl border border-zinc-800 bg-zinc-900/60">
                          <label className="text-[11px] text-zinc-400 block mb-1">Stream Sanitization</label>
                          <div className="text-emerald-400 font-medium">Enabled (StreamSanitizer.kt)</div>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                /* Default Jetpack Compose Task Manager Screen */
                <div className="flex-1 flex flex-col justify-between overflow-hidden">
                  {/* TopAppBar */}
                  <div
                    className={`px-4 py-3 border-b flex items-center justify-between shrink-0 ${
                      themeMode === 'dark'
                        ? 'bg-zinc-900 border-zinc-800'
                        : 'bg-blue-600 text-white border-blue-700'
                    }`}
                  >
                    <div className="font-semibold text-xs">Nexus Task Engine</div>
                    <span className="text-[10px] px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-300 font-mono">
                      StateFlow
                    </span>
                  </div>

                  {/* Task List */}
                  <div className="flex-1 overflow-y-auto p-4 space-y-2">
                    {tasks.map((task) => (
                      <div
                        key={task.id}
                        onClick={() =>
                          setTasks((prev) =>
                            prev.map((t) => (t.id === task.id ? { ...t, done: !t.done } : t))
                          )
                        }
                        className={`p-3 rounded-xl border flex items-center justify-between cursor-pointer transition-all ${
                          task.done
                            ? themeMode === 'dark'
                              ? 'bg-zinc-900/40 border-zinc-800 text-zinc-500 line-through'
                              : 'bg-zinc-100 border-zinc-200 text-zinc-400 line-through'
                            : themeMode === 'dark'
                            ? 'bg-zinc-900 border-zinc-800/80 text-zinc-100 hover:border-blue-500/50'
                            : 'bg-white border-zinc-200 text-zinc-900 shadow-sm hover:border-blue-500/50'
                        }`}
                      >
                        <span className="text-xs font-medium">{task.title}</span>
                        <input
                          type="checkbox"
                          checked={task.done}
                          onChange={() => {}}
                          className="rounded accent-blue-600 w-4 h-4 cursor-pointer pointer-events-none"
                        />
                      </div>
                    ))}
                  </div>

                  {/* Add Input form + FAB style */}
                  <form
                    onSubmit={handleAddTask}
                    className={`p-3 border-t flex items-center gap-2 shrink-0 ${
                      themeMode === 'dark' ? 'bg-zinc-900 border-zinc-800' : 'bg-zinc-50 border-zinc-200'
                    }`}
                  >
                    <input
                      type="text"
                      value={newTaskInput}
                      onChange={(e) => setNewTaskInput(e.target.value)}
                      placeholder="Add new action item..."
                      className={`flex-1 px-3 py-2 rounded-lg text-xs focus:outline-none ${
                        themeMode === 'dark'
                          ? 'bg-zinc-950 border border-zinc-800 text-zinc-100 placeholder-zinc-500'
                          : 'bg-white border border-zinc-300 text-zinc-800 placeholder-zinc-400'
                      }`}
                    />
                    <button
                      type="submit"
                      className="w-8 h-8 rounded-lg bg-blue-600 hover:bg-blue-500 text-white flex items-center justify-center shadow-md transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </form>
                </div>
              )}
            </div>

            {/* Android Home Navigation Bar Pill */}
            <div
              className={`h-4 flex items-center justify-center shrink-0 ${
                themeMode === 'dark' ? 'bg-zinc-950' : 'bg-white'
              }`}
            >
              <div className="w-24 h-1 rounded-full bg-zinc-600" />
            </div>
          </div>
        )}

        {/* 2. Web Application Live Preview (if HTML/CSS project) */}
        {isWebProject && (
          <div className="w-full max-w-2xl h-[640px] bg-white rounded-xl shadow-2xl border border-zinc-800 overflow-hidden flex flex-col">
            <div className="h-8 bg-zinc-900 px-3 flex items-center gap-2 border-b border-zinc-800 text-xs text-zinc-400 shrink-0">
              <Globe className="w-3.5 h-3.5 text-blue-400" />
              <span className="font-mono text-[11px] text-zinc-300">http://localhost:3000/preview</span>
            </div>
            <iframe
              title="Web Preview"
              srcDoc={`
                <!DOCTYPE html>
                <html>
                  <head>
                    <style>${cssFile?.content || ''}</style>
                  </head>
                  <body>
                    ${htmlFile?.content || '<h2>No HTML content</h2>'}
                    <script>${jsFile?.content || ''}</script>
                  </body>
                </html>
              `}
              className="w-full flex-1 border-none bg-white"
            />
          </div>
        )}

        {/* 3. Architecture & Dependency Flow Visualizer */}
        {deviceMode === 'architecture' && (
          <div className="w-full max-w-4xl bg-zinc-900 border border-zinc-800 rounded-2xl p-6 shadow-2xl text-xs space-y-6">
            <div className="flex items-center justify-between border-b border-zinc-800 pb-3">
              <div>
                <h3 className="font-semibold text-sm text-white flex items-center gap-2">
                  <Network className="w-4 h-4 text-indigo-400" />
                  {currentProjectName} Architecture Map
                </h3>
                <p className="text-xs text-zinc-400 mt-0.5">
                  Visual relationship between UI Composable layers, ViewModels, Repositories, and Network/Gemini providers.
                </p>
              </div>
              <span className="text-[10px] px-2 py-0.5 rounded bg-indigo-950 text-indigo-300 border border-indigo-800 font-mono">
                Clean Architecture MVVM
              </span>
            </div>

            {/* Visual Node Diagram */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              {/* Column 1: UI / Composable Layer */}
              <div className="space-y-2">
                <span className="font-semibold text-[11px] text-cyan-400 uppercase tracking-wider block">
                  1. UI / View Layer
                </span>
                <div className="p-3 bg-zinc-950 border border-cyan-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-cyan-300 font-mono text-[11px]">
                    {isNonea ? 'ChatScreen.kt' : 'HomeScreen.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">Jetpack Compose UI components & Scaffolds</p>
                </div>
                <div className="p-3 bg-zinc-950 border border-cyan-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-cyan-300 font-mono text-[11px]">
                    {isNonea ? 'Orb.kt / VoiceUi.kt' : 'MainActivity.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">Interactive animation & audio states</p>
                </div>
              </div>

              {/* Column 2: Presentation / ViewModel Layer */}
              <div className="space-y-2">
                <span className="font-semibold text-[11px] text-purple-400 uppercase tracking-wider block">
                  2. ViewModel Layer
                </span>
                <div className="p-3 bg-zinc-950 border border-purple-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-purple-300 font-mono text-[11px]">
                    {isNonea ? 'ChatViewModel.kt' : 'MainViewModel.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">
                    Kotlin Coroutines, StateFlow, & UI events
                  </p>
                </div>
                <div className="p-3 bg-zinc-950 border border-purple-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-purple-300 font-mono text-[11px]">
                    {isNonea ? 'AutomationPolicy.kt' : 'TaskState.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">Validation guard and action dispatch</p>
                </div>
              </div>

              {/* Column 3: Domain / Automation Router */}
              <div className="space-y-2">
                <span className="font-semibold text-[11px] text-amber-400 uppercase tracking-wider block">
                  3. Domain & Routers
                </span>
                <div className="p-3 bg-zinc-950 border border-amber-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-amber-300 font-mono text-[11px]">
                    {isNonea ? 'AutomationExecutor.kt' : 'TaskRepository.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">Orchestrates business actions & queries</p>
                </div>
                <div className="p-3 bg-zinc-950 border border-amber-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-amber-300 font-mono text-[11px]">
                    {isNonea ? 'LocalRouterApps.kt' : 'DataStore.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">Local device app packages & intents</p>
                </div>
              </div>

              {/* Column 4: Data & AI Provider Layer */}
              <div className="space-y-2">
                <span className="font-semibold text-[11px] text-emerald-400 uppercase tracking-wider block">
                  4. Data & Gemini AI
                </span>
                <div className="p-3 bg-zinc-950 border border-emerald-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-emerald-300 font-mono text-[11px]">
                    {isNonea ? 'GeminiProvider.kt' : 'RoomDatabase.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">
                    AI generation & streaming sanitizer
                  </p>
                </div>
                <div className="p-3 bg-zinc-950 border border-emerald-800/50 rounded-xl space-y-1">
                  <div className="font-semibold text-emerald-300 font-mono text-[11px]">
                    {isNonea ? 'SecretRedactor.kt' : 'SQLiteDao.kt'}
                  </div>
                  <p className="text-[10px] text-zinc-400">Security redaction and persistent state</p>
                </div>
              </div>
            </div>

            {/* Project Metrics Summary */}
            <div className="pt-4 border-t border-zinc-800 flex flex-wrap items-center justify-between gap-3 text-zinc-400 text-xs">
              <div className="flex items-center gap-4">
                <span>
                  Total Files: <strong className="text-zinc-200">{files.length}</strong>
                </span>
                <span>
                  Kotlin Classes:{' '}
                  <strong className="text-purple-300">
                    {files.filter((f) => f.path.endsWith('.kt')).length}
                  </strong>
                </span>
                <span>
                  Build DSL:{' '}
                  <strong className="text-emerald-300">
                    {files.filter((f) => f.path.endsWith('.kts') || f.path.endsWith('.gradle')).length}
                  </strong>
                </span>
              </div>
              <span className="text-zinc-500 font-mono text-[11px]">
                Active Target: {activeFilePath}
              </span>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
