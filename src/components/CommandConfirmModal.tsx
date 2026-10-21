import React from 'react';
import { AlertTriangle, ShieldCheck, Terminal, X } from 'lucide-react';
import { PendingCommand } from '../types';

interface Props {
  pendingCommand: PendingCommand | null;
}

export const CommandConfirmModal: React.FC<Props> = ({ pendingCommand }) => {
  if (!pendingCommand) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-lg bg-zinc-900 border border-zinc-700/80 rounded-xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-zinc-800 bg-zinc-900/90">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400 border border-amber-500/20">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-semibold text-zinc-100 text-sm sm:text-base">Command Execution Confirmation</h3>
              <p className="text-xs text-zinc-400">Sandboxed Environment Security Protocol</p>
            </div>
          </div>
          <button
            onClick={pendingCommand.onCancel}
            className="text-zinc-400 hover:text-zinc-200 p-1.5 rounded-lg hover:bg-zinc-800 transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4 text-sm">
          <div className="bg-amber-950/30 border border-amber-800/40 rounded-lg p-3 text-xs text-amber-200/90 leading-relaxed">
            <span className="font-semibold block text-amber-300 mb-1">Human Confirmation Required</span>
            AI-generated shell commands are kept strictly sandboxed. Please review the command below and approve execution.
          </div>

          <div>
            <label className="text-xs font-medium text-zinc-400 mb-1.5 flex items-center gap-1.5">
              <Terminal className="w-3.5 h-3.5 text-zinc-400" /> Command to Execute:
            </label>
            <div className="bg-zinc-950 border border-zinc-800 rounded-lg p-3 font-mono text-xs text-emerald-400 overflow-x-auto select-all">
              <span className="text-zinc-600 select-none">$ </span>
              {pendingCommand.command}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 text-xs">
            <div className="bg-zinc-950/50 p-2.5 rounded-lg border border-zinc-800/80">
              <span className="text-zinc-500 block">Working Directory:</span>
              <span className="font-mono text-zinc-300 truncate block">{pendingCommand.cwd}</span>
            </div>
            <div className="bg-zinc-950/50 p-2.5 rounded-lg border border-zinc-800/80">
              <span className="text-zinc-500 block">Security Sandbox:</span>
              <span className="inline-flex items-center gap-1 text-emerald-400 font-medium">
                <ShieldCheck className="w-3.5 h-3.5" /> Enforced
              </span>
            </div>
          </div>

          {pendingCommand.description && (
            <p className="text-xs text-zinc-400 italic">
              Purpose: {pendingCommand.description}
            </p>
          )}
        </div>

        {/* Footer actions */}
        <div className="flex items-center justify-end gap-3 px-5 py-3.5 border-t border-zinc-800 bg-zinc-900/60">
          <button
            onClick={pendingCommand.onCancel}
            className="px-4 py-2 text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800 hover:bg-zinc-700 rounded-lg transition-colors"
          >
            Deny & Cancel
          </button>
          <button
            onClick={pendingCommand.onConfirm}
            className="px-4 py-2 text-xs font-medium text-white bg-emerald-600 hover:bg-emerald-500 rounded-lg shadow-lg shadow-emerald-600/20 transition-all flex items-center gap-1.5"
          >
            <ShieldCheck className="w-3.5 h-3.5" />
            Confirm & Execute in Sandbox
          </button>
        </div>
      </div>
    </div>
  );
};
