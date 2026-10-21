import React, { useState, useRef, useEffect } from 'react';
import {
  Terminal,
  Play,
  Trash2,
  ShieldCheck,
  AlertTriangle,
  ChevronRight,
  CornerDownLeft,
} from 'lucide-react';
import { PendingCommand } from '../types';

interface TerminalLine {
  id: string;
  type: 'command' | 'stdout' | 'stderr' | 'info';
  text: string;
  timestamp: string;
}

interface Props {
  onRequestCommandConfirmation: (pending: PendingCommand) => void;
}

export const SandboxedTerminal: React.FC<Props> = ({ onRequestCommandConfirmation }) => {
  const [inputCommand, setInputCommand] = useState('');
  const [history, setHistory] = useState<string[]>([]);
  const [historyIndex, setHistoryIndex] = useState<number>(-1);
  const [lines, setLines] = useState<TerminalLine[]>([
    {
      id: 'init-1',
      type: 'info',
      text: 'NexusCode Sandboxed Environment v2.4 (Security Mode: Strict Confirmation)',
      timestamp: new Date().toLocaleTimeString(),
    },
    {
      id: 'init-2',
      type: 'info',
      text: 'All shell commands execute in an isolated sandbox. AI-suggested commands require human confirmation.',
      timestamp: new Date().toLocaleTimeString(),
    },
  ]);

  const outputEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    outputEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [lines]);

  const quickCommands = [
    './gradlew assembleDebug',
    'git status',
    'git log --oneline -n 5',
    'git branch',
    'node -v',
    'python --version',
  ];

  const executeCommandInSandbox = async (cmd: string) => {
    const timestamp = new Date().toLocaleTimeString();
    setLines((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        type: 'command',
        text: `$ ${cmd}`,
        timestamp,
      },
    ]);

    try {
      const res = await fetch('/api/execute-sandboxed', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          command: cmd,
          userConfirmed: true, // verified by modal or user manual run
        }),
      });

      const data = await res.json();
      if (data.stdout) {
        setLines((prev) => [
          ...prev,
          {
            id: Math.random().toString(),
            type: 'stdout',
            text: data.stdout,
            timestamp: new Date().toLocaleTimeString(),
          },
        ]);
      }
      if (data.stderr) {
        setLines((prev) => [
          ...prev,
          {
            id: Math.random().toString(),
            type: 'stderr',
            text: data.stderr,
            timestamp: new Date().toLocaleTimeString(),
          },
        ]);
      }
    } catch (err: any) {
      setLines((prev) => [
        ...prev,
        {
          id: Math.random().toString(),
          type: 'stderr',
          text: `Command error: ${err.message}`,
          timestamp: new Date().toLocaleTimeString(),
        },
      ]);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = inputCommand.trim();
    if (!trimmed) return;

    setHistory((prev) => [...prev, trimmed]);
    setHistoryIndex(-1);
    setInputCommand('');

    // If it's a build or git or system command, prompt confirmation as per instructions:
    // "Never automatically execute arbitrary AI-generated shell commands without showing them to the user and obtaining appropriate confirmation."
    onRequestCommandConfirmation({
      command: trimmed,
      cwd: '/workspace/project',
      description: 'Terminal Shell Execution',
      riskLevel: trimmed.includes('rm') || trimmed.includes('clean') ? 'high' : 'safe',
      onConfirm: () => executeCommandInSandbox(trimmed),
      onCancel: () => {
        setLines((prev) => [
          ...prev,
          {
            id: Math.random().toString(),
            type: 'info',
            text: `[ABORTED] Execution of '${trimmed}' was denied by user.`,
            timestamp: new Date().toLocaleTimeString(),
          },
        ]);
      },
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (history.length === 0) return;
      const nextIdx = historyIndex === -1 ? history.length - 1 : Math.max(0, historyIndex - 1);
      setHistoryIndex(nextIdx);
      setInputCommand(history[nextIdx]);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (historyIndex === -1) return;
      const nextIdx = historyIndex + 1;
      if (nextIdx >= history.length) {
        setHistoryIndex(-1);
        setInputCommand('');
      } else {
        setHistoryIndex(nextIdx);
        setInputCommand(history[nextIdx]);
      }
    }
  };

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-80 sm:w-96 select-none shrink-0 overflow-hidden font-mono text-xs">
      {/* Header */}
      <div className="px-3.5 py-2.5 border-b border-zinc-800 flex items-center justify-between bg-zinc-950">
        <div className="flex items-center gap-2">
          <Terminal className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-xs text-white uppercase tracking-wider font-sans">
            Sandboxed Terminal
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-emerald-400 flex items-center gap-1 font-sans">
            <ShieldCheck className="w-3 h-3" /> Sandboxed
          </span>
          <button
            onClick={() => setLines([])}
            title="Clear terminal"
            className="p-1 text-zinc-500 hover:text-zinc-300 rounded"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Quick Command Chips */}
      <div className="p-2 border-b border-zinc-800/80 bg-zinc-900/40 flex items-center gap-1 overflow-x-auto no-scrollbar font-sans">
        <span className="text-[10px] text-zinc-500 shrink-0 font-medium">Quick:</span>
        {quickCommands.map((qc, idx) => (
          <button
            key={idx}
            onClick={() => {
              onRequestCommandConfirmation({
                command: qc,
                cwd: '/workspace/project',
                description: `Run ${qc}`,
                riskLevel: 'safe',
                onConfirm: () => executeCommandInSandbox(qc),
                onCancel: () => {},
              });
            }}
            className="text-[10px] px-2 py-0.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 rounded border border-zinc-800 shrink-0 font-mono transition-colors"
          >
            {qc}
          </button>
        ))}
      </div>

      {/* Terminal Output stream */}
      <div className="flex-1 overflow-y-auto p-3 space-y-1.5 select-text">
        {lines.map((l) => {
          if (l.type === 'command') {
            return (
              <div key={l.id} className="text-zinc-100 font-semibold flex items-start gap-1">
                <ChevronRight className="w-3.5 h-3.5 text-blue-400 shrink-0 mt-0.5" />
                <span className="text-emerald-400">{l.text}</span>
              </div>
            );
          }
          if (l.type === 'stderr') {
            return (
              <div key={l.id} className="text-red-400 pl-4 whitespace-pre-wrap leading-relaxed">
                {l.text}
              </div>
            );
          }
          if (l.type === 'info') {
            return (
              <div key={l.id} className="text-zinc-500 pl-4 italic text-[11px]">
                {l.text}
              </div>
            );
          }
          return (
            <div key={l.id} className="text-zinc-300 pl-4 whitespace-pre-wrap leading-relaxed">
              {l.text}
            </div>
          );
        })}
        <div ref={outputEndRef} />
      </div>

      {/* Command input prompt */}
      <form onSubmit={handleSubmit} className="p-2 border-t border-zinc-800 bg-zinc-900/60 flex items-center gap-1.5">
        <span className="text-emerald-400 font-bold">$</span>
        <input
          type="text"
          value={inputCommand}
          onChange={(e) => setInputCommand(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type command and press enter..."
          className="flex-1 bg-transparent text-zinc-100 placeholder-zinc-600 focus:outline-none text-xs font-mono"
        />
        <button
          type="submit"
          className="p-1 text-zinc-400 hover:text-white rounded hover:bg-zinc-800"
          title="Execute"
        >
          <CornerDownLeft className="w-3.5 h-3.5" />
        </button>
      </form>
    </div>
  );
};
