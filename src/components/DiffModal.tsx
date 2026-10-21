import React from 'react';
import { ArrowRight, Check, X } from 'lucide-react';
import { SupportedLanguage } from '../types';
import { highlightCode } from '../utils/syntaxHighlighter';

interface Props {
  filePath: string;
  originalCode: string;
  newCode: string;
  language: SupportedLanguage;
  title?: string;
  summary?: string;
  onApply: () => void;
  onClose: () => void;
}

export const DiffModal: React.FC<Props> = ({
  filePath,
  originalCode,
  newCode,
  language,
  title = 'Review Code Changes',
  summary,
  onApply,
  onClose,
}) => {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in duration-200">
      <div className="w-full max-w-5xl h-[85vh] bg-zinc-900 border border-zinc-700 rounded-xl shadow-2xl flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800 bg-zinc-950/70">
          <div>
            <h3 className="font-semibold text-zinc-100 text-base">{title}</h3>
            <p className="text-xs text-zinc-400 font-mono mt-0.5 truncate max-w-xl">
              Target File: <span className="text-blue-400">{filePath}</span>
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-200 p-1.5 rounded-lg hover:bg-zinc-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {summary && (
          <div className="px-6 py-2.5 bg-blue-950/20 border-b border-blue-900/30 text-xs text-blue-300">
            {summary}
          </div>
        )}

        {/* Diff comparison columns */}
        <div className="flex-1 grid grid-cols-2 divide-x divide-zinc-800 overflow-hidden text-xs font-mono">
          {/* Current / Original */}
          <div className="flex flex-col h-full overflow-hidden bg-zinc-950/40">
            <div className="px-4 py-2 border-b border-zinc-800/80 bg-red-950/20 text-red-300 font-sans font-medium flex items-center justify-between">
              <span>Current File Version</span>
              <span className="text-[10px] text-zinc-500 uppercase">{originalCode.split('\n').length} lines</span>
            </div>
            <div className="flex-1 overflow-auto p-4 select-text">
              <pre
                className="leading-relaxed whitespace-pre font-mono text-zinc-300"
                dangerouslySetInnerHTML={{ __html: highlightCode(originalCode, language) }}
              />
            </div>
          </div>

          {/* Proposed New Code */}
          <div className="flex flex-col h-full overflow-hidden bg-zinc-950/40">
            <div className="px-4 py-2 border-b border-zinc-800/80 bg-emerald-950/20 text-emerald-300 font-sans font-medium flex items-center justify-between">
              <span>Proposed AI Modification</span>
              <span className="text-[10px] text-emerald-400 uppercase">{newCode.split('\n').length} lines</span>
            </div>
            <div className="flex-1 overflow-auto p-4 select-text">
              <pre
                className="leading-relaxed whitespace-pre font-mono text-zinc-200"
                dangerouslySetInnerHTML={{ __html: highlightCode(newCode, language) }}
              />
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between px-6 py-3.5 border-t border-zinc-800 bg-zinc-950/80">
          <span className="text-xs text-zinc-400">
            Clicking &apos;Apply to File&apos; will overwrite the target file with the proposed version.
          </span>
          <div className="flex items-center gap-3">
            <button
              onClick={onClose}
              className="px-4 py-2 text-xs font-medium text-zinc-300 hover:text-white bg-zinc-800 hover:bg-zinc-700 rounded-lg transition-colors"
            >
              Discard Changes
            </button>
            <button
              onClick={onApply}
              className="px-5 py-2 text-xs font-medium text-white bg-blue-600 hover:bg-blue-500 rounded-lg shadow-lg shadow-blue-600/25 transition-all flex items-center gap-1.5"
            >
              <Check className="w-3.5 h-3.5" />
              Apply to File
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
