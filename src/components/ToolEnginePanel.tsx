import React, { useState, useEffect, useRef } from 'react';
import {
  Cpu,
  ShieldCheck,
  AlertTriangle,
  Play,
  CheckCircle2,
  XCircle,
  Clock,
  Sparkles,
  Lock,
  Unlock,
  Eye,
  FileCode,
  Terminal,
  Send,
  Check,
  ChevronRight,
  Database,
  Search,
  ExternalLink,
  Volume2,
  Sliders,
  RotateCcw,
} from 'lucide-react';
import {
  InternalToolDefinition,
  ProposedToolCall,
  ToolExecutionResult,
  SecurityPolicyConfig,
  ToolAuditLogEntry,
} from '../types/tools';
import { ProjectFile } from '../types';

interface Props {
  files: ProjectFile[];
  onFileWritten?: (path: string, content: string) => void;
}

export const ToolEnginePanel: React.FC<Props> = ({ files, onFileWritten }) => {
  const [activeTab, setActiveTab] = useState<'interactive' | 'catalog' | 'policies' | 'audit'>('interactive');
  const [toolsCatalog, setToolsCatalog] = useState<InternalToolDefinition[]>([]);
  const [inputPrompt, setInputPrompt] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  // Active interaction chain
  const [aiThought, setAiThought] = useState<string>('');
  const [proposedCall, setProposedCall] = useState<ProposedToolCall | null>(null);
  const [executionResult, setExecutionResult] = useState<ToolExecutionResult | null>(null);
  const [finalAiResponse, setFinalAiResponse] = useState<string>('');

  // Policy Settings
  const [policyConfig, setPolicyConfig] = useState<SecurityPolicyConfig>({
    requireConfirmationForWrites: true,
    requireConfirmationForAppLaunch: false,
    requireConfirmationForNetwork: false,
    autoApproveReadOnly: true,
    strictPathSandbox: true,
    redactSecretsInLogs: true,
    executionTimeoutSeconds: 10,
  });

  // Audit Logs
  const [auditLogs, setAuditLogs] = useState<ToolAuditLogEntry[]>([
    {
      id: 'aud-1',
      timestamp: new Date().toLocaleTimeString(),
      toolName: 'System',
      level: 'info',
      message: 'Nexus Tool Engine initialized with 12 sandboxed tools and local policy guard.',
    },
    {
      id: 'aud-2',
      timestamp: new Date().toLocaleTimeString(),
      toolName: 'AutomationGuard',
      level: 'policy',
      message: 'Security blacklist loaded (settings, authenticators, banking surfaces blocked).',
    },
  ]);

  const auditEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Load tools catalog from server
    fetch('/api/tools/catalog')
      .then((res) => res.json())
      .then((data) => {
        if (Array.isArray(data)) setToolsCatalog(data);
      })
      .catch((err) => console.error('Failed to load tool catalog:', err));
  }, []);

  const addAuditLog = (toolName: string, level: ToolAuditLogEntry['level'], message: string, dataPreview?: string) => {
    setAuditLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        toolName,
        level,
        message,
        dataPreview,
      },
    ]);
  };

  // Sample prompt chips
  const samplePrompts = [
    'Open YouTube and search for Jetpack Compose state management',
    'Get current time in Tokyo, Japan',
    'Pause media playback and mute sound',
    'Create a note: "Implement offline caching in TaskRepository"',
    'Read project files: app/build.gradle.kts and settings.gradle.kts',
    'Write a new file: app/src/main/java/com/nonea/ai/DateUtils.kt with format helper methods',
    'Inspect recent build logs for error diagnostics',
  ];

  // Send request to Gemini with Tools
  const handleSendPrompt = async (customText?: string) => {
    const text = customText || inputPrompt;
    if (!text.trim()) return;

    setIsLoading(true);
    setAiThought('');
    setProposedCall(null);
    setExecutionResult(null);
    setFinalAiResponse('');

    addAuditLog('GeminiClient', 'info', `User request received: "${text}"`);

    try {
      const res = await fetch('/api/tools/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: text,
          workspaceFiles: files.map((f) => ({ path: f.path })),
        }),
      });

      const data = await res.json();
      if (data.error) throw new Error(data.error);

      setAiThought(data.text || '');

      if (data.proposedCalls && data.proposedCalls.length > 0) {
        const call = data.proposedCalls[0];
        setProposedCall(call);

        addAuditLog(
          call.toolName,
          'policy',
          `Gemini proposed tool '${call.toolName}'. Policy check: ${call.policyStatus.toUpperCase()} (${call.policyReason})`,
          JSON.stringify(call.arguments)
        );

        // If policy permits automatic execution without confirmation:
        if (call.policyStatus === 'permitted' && !call.requiresConfirmation) {
          await executeTool(call.toolName, call.arguments, true, text);
        }
      } else {
        setFinalAiResponse(data.text || 'Gemini answered directly without invoking any tools.');
      }
    } catch (err: any) {
      addAuditLog('Engine', 'error', `Failed to process tool request: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  // Execute tool locally with sandboxing & confirmation check
  const executeTool = async (
    toolName: string,
    args: Record<string, any>,
    userConfirmed: boolean,
    originalPrompt: string
  ) => {
    setIsLoading(true);
    addAuditLog(toolName, 'execution', `Executing local tool in sandbox: ${toolName}...`);

    try {
      const res = await fetch('/api/tools/execute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          toolName,
          arguments: args,
          userConfirmed,
          workspaceFiles: files.map((f) => ({ path: f.path, content: f.content })),
        }),
      });

      const execResult: ToolExecutionResult = await res.json();
      setExecutionResult(execResult);

      addAuditLog(
        toolName,
        execResult.success ? 'execution' : 'error',
        execResult.success
          ? `Tool '${toolName}' completed in ${execResult.executionTimeMs}ms.`
          : `Tool '${toolName}' failed: ${execResult.error}`,
        JSON.stringify(execResult.data || execResult.error)
      );

      // If file write was executed, update workspace
      if (execResult.success && toolName === 'write_project_file' && onFileWritten && args.path && args.content) {
        onFileWritten(args.path, args.content);
        addAuditLog('Workspace', 'info', `Synced modified file '${args.path}' into editor tabs.`);
      }

      // Resume turn: ask Gemini to summarize the tool result
      const resumeRes = await fetch('/api/tools/resume', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originalPrompt,
          toolName,
          toolResult: execResult.data || execResult.error,
        }),
      });

      const resumeData = await resumeRes.json();
      setFinalAiResponse(resumeData.text || 'Action complete.');
    } catch (err: any) {
      addAuditLog(toolName, 'error', `Execution failed: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-80 sm:w-96 select-none shrink-0 overflow-hidden font-sans">
      {/* Header */}
      <div className="px-3.5 py-2.5 border-b border-zinc-800 flex items-center justify-between bg-zinc-950">
        <div className="flex items-center gap-2">
          <Cpu className="w-4 h-4 text-cyan-400" />
          <span className="font-semibold text-xs text-white uppercase tracking-wider">
            Tool-Calling & Execution Engine
          </span>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-950/70 text-cyan-300 border border-cyan-800/40 font-mono">
          Strict Security Sandbox
        </span>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-4 gap-1 p-2 bg-zinc-900/50 border-b border-zinc-800 text-[11px]">
        <button
          onClick={() => setActiveTab('interactive')}
          className={`py-1.5 px-1 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'interactive' ? 'bg-cyan-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Play className="w-3 h-3" /> Console
        </button>
        <button
          onClick={() => setActiveTab('catalog')}
          className={`py-1.5 px-1 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'catalog' ? 'bg-cyan-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Database className="w-3 h-3" /> Tools ({toolsCatalog.length})
        </button>
        <button
          onClick={() => setActiveTab('policies')}
          className={`py-1.5 px-1 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'policies' ? 'bg-cyan-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <ShieldCheck className="w-3 h-3" /> Policy
        </button>
        <button
          onClick={() => setActiveTab('audit')}
          className={`py-1.5 px-1 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'audit' ? 'bg-cyan-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Clock className="w-3 h-3" /> Logs
        </button>
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {/* TAB 1: INTERACTIVE CONSOLE */}
        {activeTab === 'interactive' && (
          <div className="space-y-3 text-xs">
            {/* Input Form */}
            <div className="bg-zinc-900/80 border border-zinc-800 rounded-lg p-2.5 space-y-2">
              <label className="text-[11px] font-semibold text-zinc-300 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-cyan-400" />
                Ask Gemini to Request App Actions:
              </label>
              <textarea
                rows={2}
                value={inputPrompt}
                onChange={(e) => setInputPrompt(e.target.value)}
                placeholder="e.g. Search YouTube for Android Compose tutorials, or open Settings..."
                className="w-full p-2 bg-zinc-950 border border-zinc-800 rounded text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-cyan-500 resize-none font-sans"
              />
              <button
                onClick={() => handleSendPrompt()}
                disabled={isLoading || !inputPrompt.trim()}
                className="w-full py-1.5 bg-cyan-600 hover:bg-cyan-500 disabled:opacity-50 text-white rounded font-medium flex items-center justify-center gap-1.5 shadow-md shadow-cyan-600/20 transition-all text-xs"
              >
                {isLoading ? (
                  <>
                    <div className="w-3 h-3 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    <span>Evaluating with Gemini...</span>
                  </>
                ) : (
                  <>
                    <Send className="w-3 h-3" />
                    <span>Dispatch to Tool Engine</span>
                  </>
                )}
              </button>
            </div>

            {/* Quick Sample Chips */}
            <div className="space-y-1">
              <span className="text-[10px] text-zinc-500 font-semibold uppercase tracking-wider block">
                Quick Test Actions:
              </span>
              <div className="space-y-1 max-h-32 overflow-y-auto no-scrollbar">
                {samplePrompts.map((p, idx) => (
                  <button
                    key={idx}
                    onClick={() => {
                      setInputPrompt(p);
                      handleSendPrompt(p);
                    }}
                    className="w-full text-left text-[11px] px-2 py-1 bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 rounded border border-zinc-800/80 truncate transition-colors flex items-center justify-between"
                  >
                    <span className="truncate">{p}</span>
                    <ChevronRight className="w-3 h-3 text-zinc-600 shrink-0" />
                  </button>
                ))}
              </div>
            </div>

            {/* SEPARATION OF REASONING & LOCAL EXECUTOR DISPLAY */}
            {proposedCall && (
              <div className="bg-zinc-900 border border-cyan-800/40 rounded-lg p-3 space-y-2.5 animate-in fade-in duration-200">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-bold text-cyan-400 uppercase tracking-wider flex items-center gap-1">
                    <Cpu className="w-3.5 h-3.5" /> Step 1: AI Proposed Action
                  </span>
                  <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono uppercase font-bold ${
                    proposedCall.riskLevel === 'safe'
                      ? 'bg-emerald-950 text-emerald-300'
                      : proposedCall.riskLevel === 'medium'
                      ? 'bg-amber-950 text-amber-300'
                      : 'bg-red-950 text-red-300'
                  }`}>
                    {proposedCall.riskLevel} Risk
                  </span>
                </div>

                <div className="bg-zinc-950 p-2 rounded border border-zinc-800 font-mono text-[11px] text-emerald-400">
                  <div className="text-zinc-500 text-[10px]">Tool:</div>
                  <div className="font-bold text-white text-xs">{proposedCall.toolName}</div>
                  <div className="text-zinc-500 text-[10px] mt-1">Arguments:</div>
                  <pre className="text-zinc-300 whitespace-pre-wrap select-text text-[10px] overflow-auto max-h-24">
                    {JSON.stringify(proposedCall.arguments, null, 2)}
                  </pre>
                </div>

                {/* Step 2: Policy & Permission Verification */}
                <div className="space-y-1">
                  <span className="text-[10px] font-bold text-zinc-400 uppercase tracking-wider block">
                    Step 2: Local Policy & Automation Guard Check
                  </span>
                  <div className={`p-2 rounded border text-[11px] flex items-center justify-between ${
                    proposedCall.policyStatus === 'permitted'
                      ? 'bg-emerald-950/40 border-emerald-800/50 text-emerald-300'
                      : proposedCall.policyStatus === 'needs_confirmation'
                      ? 'bg-amber-950/40 border-amber-800/50 text-amber-300'
                      : 'bg-red-950/40 border-red-800/50 text-red-300'
                  }`}>
                    <span>{proposedCall.policyReason}</span>
                    <span className="font-semibold uppercase text-[10px] shrink-0 ml-2">
                      [{proposedCall.policyStatus}]
                    </span>
                  </div>
                </div>

                {/* Step 3: Human-In-The-Loop Confirmation Button */}
                {proposedCall.requiresConfirmation && !executionResult && (
                  <div className="bg-amber-950/30 border border-amber-800/50 rounded-lg p-2.5 space-y-2">
                    <div className="flex items-center gap-1.5 text-amber-300 font-semibold text-[11px]">
                      <AlertTriangle className="w-3.5 h-3.5" />
                      Human Confirmation Required
                    </div>
                    <p className="text-[10px] text-zinc-400">
                      The AI cannot execute this action without your explicit consent.
                    </p>
                    <div className="flex items-center gap-2 pt-1">
                      <button
                        onClick={() => {
                          setProposedCall(null);
                          addAuditLog(proposedCall.toolName, 'approval', 'Action execution rejected by user.');
                        }}
                        className="flex-1 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded text-xs font-medium"
                      >
                        Reject
                      </button>
                      <button
                        onClick={() =>
                          executeTool(
                            proposedCall.toolName,
                            proposedCall.arguments,
                            true,
                            inputPrompt
                          )
                        }
                        className="flex-1 py-1 bg-emerald-600 hover:bg-emerald-500 text-white rounded text-xs font-medium shadow-md shadow-emerald-600/20"
                      >
                        Approve & Execute
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Step 4: Execution Output Card */}
            {executionResult && (
              <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-3 space-y-2 animate-in fade-in">
                <div className="flex items-center justify-between text-[11px]">
                  <span className="font-bold text-zinc-200 flex items-center gap-1.5">
                    {executionResult.success ? (
                      <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                    ) : (
                      <XCircle className="w-3.5 h-3.5 text-red-400" />
                    )}
                    Step 3: Action Execution Result
                  </span>
                  <span className="font-mono text-[10px] text-zinc-500">
                    {executionResult.executionTimeMs}ms
                  </span>
                </div>

                <div className="bg-zinc-950 p-2 rounded border border-zinc-800 font-mono text-[10px] max-h-36 overflow-auto select-text text-zinc-300">
                  <pre>{JSON.stringify(executionResult.data || executionResult.error, null, 2)}</pre>
                </div>
              </div>
            )}

            {/* Step 5: Final Model Answer */}
            {finalAiResponse && (
              <div className="bg-zinc-900/90 border border-cyan-800/40 rounded-lg p-3 space-y-1 text-zinc-200 leading-relaxed text-xs select-text">
                <div className="font-bold text-cyan-300 text-[11px] mb-1">
                  Gemini Synthesis:
                </div>
                {finalAiResponse}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: TOOL CATALOG */}
        {activeTab === 'catalog' && (
          <div className="space-y-2 text-xs">
            <span className="text-[10px] text-zinc-500 uppercase tracking-wider block font-semibold">
              Supported App Actions (12 Registered Tools):
            </span>
            <div className="space-y-2">
              {toolsCatalog.map((tool) => (
                <div
                  key={tool.name}
                  className="bg-zinc-900/70 border border-zinc-800 rounded-lg p-2.5 space-y-1.5 hover:border-zinc-700 transition-colors"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono font-bold text-cyan-300 text-[11px]">{tool.name}</span>
                    <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono uppercase font-bold ${
                      tool.riskLevel === 'safe'
                        ? 'bg-emerald-950 text-emerald-300'
                        : tool.riskLevel === 'medium'
                        ? 'bg-amber-950 text-amber-300'
                        : 'bg-red-950 text-red-300'
                    }`}>
                      {tool.riskLevel}
                    </span>
                  </div>
                  <p className="text-[11px] text-zinc-400 leading-relaxed">{tool.description}</p>

                  <div className="bg-zinc-950 p-1.5 rounded border border-zinc-800/80 font-mono text-[10px] text-zinc-400 space-y-0.5">
                    <span className="text-zinc-600 block">Arguments:</span>
                    {tool.arguments.map((arg, idx) => (
                      <div key={idx} className="truncate">
                        • <span className="text-zinc-300">{arg.name}</span>: {arg.type} {arg.required ? '(required)' : '(optional)'}
                      </div>
                    ))}
                  </div>

                  <button
                    onClick={() => {
                      setActiveTab('interactive');
                      setInputPrompt(`Execute tool ${tool.name} with default parameters`);
                    }}
                    className="w-full py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded text-[10px] font-medium transition-colors"
                  >
                    Load into Console
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* TAB 3: POLICY & PERMISSIONS */}
        {activeTab === 'policies' && (
          <div className="space-y-3 text-xs leading-relaxed">
            <div className="bg-zinc-900 p-3 rounded-lg border border-zinc-800 space-y-3">
              <h4 className="font-semibold text-zinc-200 flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-cyan-400" />
                Security Policies & Sandboxing
              </h4>
              <p className="text-[11px] text-zinc-400">
                Separates AI reasoning from device privilege. Controls when Gemini requires explicit human confirmation.
              </p>

              <div className="space-y-2 pt-1">
                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800/80 cursor-pointer">
                  <span className="text-zinc-300">Require confirmation for file writes</span>
                  <input
                    type="checkbox"
                    checked={policyConfig.requireConfirmationForWrites}
                    onChange={(e) =>
                      setPolicyConfig((prev) => ({ ...prev, requireConfirmationForWrites: e.target.checked }))
                    }
                    className="accent-cyan-500 rounded"
                  />
                </label>

                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800/80 cursor-pointer">
                  <span className="text-zinc-300">Strict path traversal sandbox (no &quot;../&quot;)</span>
                  <input
                    type="checkbox"
                    checked={policyConfig.strictPathSandbox}
                    disabled
                    className="accent-cyan-500 rounded"
                  />
                </label>

                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800/80 cursor-pointer">
                  <span className="text-zinc-300">Secret redaction in all execution logs</span>
                  <input
                    type="checkbox"
                    checked={policyConfig.redactSecretsInLogs}
                    disabled
                    className="accent-cyan-500 rounded"
                  />
                </label>

                <label className="flex items-center justify-between p-2 rounded bg-zinc-950 border border-zinc-800/80 cursor-pointer">
                  <span className="text-zinc-300">Auto-approve safe read-only queries</span>
                  <input
                    type="checkbox"
                    checked={policyConfig.autoApproveReadOnly}
                    onChange={(e) =>
                      setPolicyConfig((prev) => ({ ...prev, autoApproveReadOnly: e.target.checked }))
                    }
                    className="accent-cyan-500 rounded"
                  />
                </label>
              </div>
            </div>

            <div className="bg-red-950/20 border border-red-800/30 rounded-lg p-3 space-y-1.5 text-[11px] text-red-300">
              <span className="font-semibold block text-red-200">Device Surface Blacklist:</span>
              <p className="text-zinc-400">
                System settings, password managers (Bitwarden, Authenticator), and payment apps (Google Wallet, PayPal) are permanently blocked from automated interactions.
              </p>
            </div>
          </div>
        )}

        {/* TAB 4: REDACTED AUDIT LOG */}
        {activeTab === 'audit' && (
          <div className="space-y-2 text-xs font-mono">
            <div className="flex items-center justify-between text-[11px] text-zinc-500">
              <span>Event Timeline ({auditLogs.length})</span>
              <button
                onClick={() => setAuditLogs([])}
                className="text-zinc-500 hover:text-zinc-300 font-sans text-[10px]"
              >
                Clear Logs
              </button>
            </div>

            <div className="space-y-1 max-h-96 overflow-y-auto">
              {auditLogs.map((log) => {
                let badgeColor = 'text-zinc-400 bg-zinc-800';
                if (log.level === 'policy') badgeColor = 'text-cyan-400 bg-cyan-950 border border-cyan-800/50';
                if (log.level === 'execution') badgeColor = 'text-emerald-400 bg-emerald-950 border border-emerald-800/50';
                if (log.level === 'error') badgeColor = 'text-red-400 bg-red-950 border border-red-800/50';
                if (log.level === 'approval') badgeColor = 'text-amber-400 bg-amber-950 border border-amber-800/50';

                return (
                  <div key={log.id} className="p-2 rounded bg-zinc-900/60 border border-zinc-800/80 space-y-1">
                    <div className="flex items-center justify-between text-[10px]">
                      <span className="text-zinc-500">[{log.timestamp}]</span>
                      <span className={`px-1 rounded uppercase font-bold text-[9px] ${badgeColor}`}>
                        {log.level}
                      </span>
                    </div>
                    <div className="text-zinc-200 text-[11px]">{log.message}</div>
                    {log.dataPreview && (
                      <div className="bg-zinc-950 p-1.5 rounded text-[10px] text-zinc-400 truncate">
                        {log.dataPreview}
                      </div>
                    )}
                  </div>
                );
              })}
              <div ref={auditEndRef} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
