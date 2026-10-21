import React, { useState, useRef, useEffect } from 'react';
import {
  Terminal,
  Filter,
  Copy,
  Download,
  Trash2,
  Check,
  Sparkles,
  ArrowDown,
  AlertCircle,
  AlertTriangle,
  Info,
  CheckCircle2,
  Minimize2,
  Maximize2,
} from 'lucide-react';
import { BuildLogMessage } from '../types';

interface Props {
  logs: BuildLogMessage[];
  onClearLogs: () => void;
  onDiagnoseError: (errorText: string) => void;
  isOpen: boolean;
  onToggleOpen: () => void;
}

export const BuildLogViewer: React.FC<Props> = ({
  logs,
  onClearLogs,
  onDiagnoseError,
  isOpen,
  onToggleOpen,
}) => {
  const [filter, setFilter] = useState<'all' | 'error' | 'warn' | 'info' | 'success'>('all');
  const [autoScroll, setAutoScroll] = useState(true);
  const [copied, setCopied] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (autoScroll && isOpen) {
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll, isOpen]);

  const filteredLogs = logs.filter((log) => {
    if (filter === 'all') return true;
    return log.level === filter;
  });

  const errorCount = logs.filter((l) => l.level === 'error').length;
  const warnCount = logs.filter((l) => l.level === 'warn').length;

  const handleCopyLogs = async () => {
    const text = logs.map((l) => `[${l.timestamp}] [${l.level.toUpperCase()}] ${l.text}`).join('\n');
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleDownloadLog = () => {
    const text = logs.map((l) => `[${l.timestamp}] [${l.level.toUpperCase()}] ${l.text}`).join('\n');
    const blob = new Blob([text], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `build_log_${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (!isOpen) {
    return (
      <div className="h-7 bg-zinc-950 border-t border-zinc-800 px-3 flex items-center justify-between text-xs text-zinc-400 select-none shrink-0">
        <button
          onClick={onToggleOpen}
          className="flex items-center gap-2 hover:text-zinc-200 transition-colors font-medium text-[11px]"
        >
          <Terminal className="w-3.5 h-3.5 text-zinc-500" />
          <span>Build Console & Log Viewer</span>
          {errorCount > 0 && (
            <span className="px-1.5 py-0.2 bg-red-950 text-red-400 rounded-full text-[10px] border border-red-800/60 font-semibold">
              {errorCount} {errorCount === 1 ? 'Error' : 'Errors'}
            </span>
          )}
          {warnCount > 0 && (
            <span className="px-1.5 py-0.2 bg-amber-950 text-amber-400 rounded-full text-[10px] border border-amber-800/60 font-semibold">
              {warnCount} Warn
            </span>
          )}
        </button>

        <button
          onClick={onToggleOpen}
          className="text-zinc-500 hover:text-zinc-300 p-0.5"
          title="Expand Build Console"
        >
          <Maximize2 className="w-3 h-3" />
        </button>
      </div>
    );
  }

  return (
    <div className="h-56 bg-zinc-950 border-t border-zinc-800 flex flex-col text-xs text-zinc-300 select-none shrink-0 font-mono">
      {/* Console Toolbar */}
      <div className="h-8 bg-zinc-900 border-b border-zinc-800 px-3 flex items-center justify-between font-sans">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-1.5 font-semibold text-zinc-200 text-xs">
            <Terminal className="w-3.5 h-3.5 text-blue-400" />
            <span>Build Logs ({logs.length})</span>
          </div>

          {/* Level Filter Buttons */}
          <div className="flex items-center gap-1 text-[11px]">
            <button
              onClick={() => setFilter('all')}
              className={`px-2 py-0.5 rounded ${
                filter === 'all' ? 'bg-zinc-800 text-white font-medium' : 'text-zinc-500 hover:text-zinc-300'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilter('error')}
              className={`px-2 py-0.5 rounded flex items-center gap-1 ${
                filter === 'error' ? 'bg-red-950 text-red-300 font-medium' : 'text-zinc-500 hover:text-red-400'
              }`}
            >
              <AlertCircle className="w-3 h-3 text-red-400" />
              <span>{errorCount} Errors</span>
            </button>
            <button
              onClick={() => setFilter('warn')}
              className={`px-2 py-0.5 rounded flex items-center gap-1 ${
                filter === 'warn' ? 'bg-amber-950 text-amber-300 font-medium' : 'text-zinc-500 hover:text-amber-400'
              }`}
            >
              <AlertTriangle className="w-3 h-3 text-amber-400" />
              <span>{warnCount} Warnings</span>
            </button>
          </div>
        </div>

        {/* Actions (Auto-scroll, Copy, Download, Clear, Close) */}
        <div className="flex items-center gap-1 text-zinc-400">
          <button
            onClick={() => setAutoScroll(!autoScroll)}
            title={autoScroll ? 'Disable Auto-scroll' : 'Enable Auto-scroll'}
            className={`p-1 rounded ${autoScroll ? 'text-blue-400 bg-blue-950/40' : 'hover:bg-zinc-800'}`}
          >
            <ArrowDown className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={handleCopyLogs}
            title="Copy Logs"
            className="p-1 rounded hover:bg-zinc-800 hover:text-zinc-200"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          </button>
          <button
            onClick={handleDownloadLog}
            title="Download Log File"
            className="p-1 rounded hover:bg-zinc-800 hover:text-zinc-200"
          >
            <Download className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={onClearLogs}
            title="Clear Logs"
            className="p-1 rounded hover:bg-zinc-800 hover:text-zinc-200"
          >
            <Trash2 className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={onToggleOpen}
            title="Collapse Console"
            className="p-1 rounded hover:bg-zinc-800 hover:text-zinc-200 ml-1"
          >
            <Minimize2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Log Output Stream */}
      <div className="flex-1 overflow-y-auto p-2.5 space-y-1 font-mono text-[11px] leading-relaxed select-text">
        {filteredLogs.length === 0 ? (
          <div className="text-zinc-600 italic py-4 text-center font-sans">
            No build logs available. Click &quot;Build Project&quot; in the header to compile.
          </div>
        ) : (
          filteredLogs.map((log) => {
            let textColor = 'text-zinc-300';
            let badgeBg = 'bg-zinc-800 text-zinc-400';

            if (log.level === 'error') {
              textColor = 'text-red-300 font-semibold';
              badgeBg = 'bg-red-950/80 text-red-400 border border-red-800/60';
            } else if (log.level === 'warn') {
              textColor = 'text-amber-300';
              badgeBg = 'bg-amber-950/80 text-amber-400 border border-amber-800/60';
            } else if (log.level === 'success') {
              textColor = 'text-emerald-300 font-semibold';
              badgeBg = 'bg-emerald-950/80 text-emerald-400 border border-emerald-800/60';
            }

            return (
              <div
                key={log.id}
                className="group flex items-start gap-2 hover:bg-zinc-900/50 py-0.5 px-1.5 rounded transition-colors"
              >
                <span className="text-zinc-600 text-[10px] select-none shrink-0 mt-0.5">
                  [{log.timestamp}]
                </span>
                <span className={`text-[9px] uppercase px-1 rounded font-bold shrink-0 mt-0.5 ${badgeBg}`}>
                  {log.level}
                </span>
                <span className={`flex-1 break-words ${textColor}`}>{log.text}</span>

                {log.level === 'error' && (
                  <button
                    onClick={() => onDiagnoseError(log.text)}
                    className="opacity-0 group-hover:opacity-100 px-2 py-0.5 bg-red-600 hover:bg-red-500 text-white rounded text-[10px] font-sans font-medium flex items-center gap-1 shrink-0 transition-opacity"
                  >
                    <Sparkles className="w-3 h-3" />
                    <span>AI Fix</span>
                  </button>
                )}
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
};
