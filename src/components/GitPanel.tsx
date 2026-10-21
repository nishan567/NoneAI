import React, { useState } from 'react';
import {
  GitBranch,
  GitCommit as GitCommitIcon,
  Check,
  Plus,
  Minus,
  RotateCcw,
  History,
  FileCode,
  FilePlus,
  FileMinus,
  Edit3,
  GitPullRequest,
  CheckCheck,
} from 'lucide-react';
import { ProjectFile, GitCommit, GitChange } from '../types';

interface Props {
  files: ProjectFile[];
  commits: GitCommit[];
  onCommit: (message: string) => void;
  onDiscardChanges: (path: string) => void;
  onOpenDiff: (file: ProjectFile) => void;
}

export const GitPanel: React.FC<Props> = ({
  files,
  commits,
  onCommit,
  onDiscardChanges,
  onOpenDiff,
}) => {
  const [commitMessage, setCommitMessage] = useState('');
  const [currentBranch, setCurrentBranch] = useState('main');
  const [stagedFiles, setStagedFiles] = useState<Set<string>>(new Set());
  const [selectedCommit, setSelectedCommit] = useState<GitCommit | null>(null);
  const [activeTab, setActiveTab] = useState<'changes' | 'history'>('changes');

  // Compute modified / untracked files
  const modifiedFiles = files.filter((f) => f.isModified);

  const toggleStage = (path: string) => {
    setStagedFiles((prev) => {
      const next = new Set(prev);
      if (next.has(path)) next.delete(path);
      else next.add(path);
      return next;
    });
  };

  const stageAll = () => {
    setStagedFiles(new Set(modifiedFiles.map((f) => f.path)));
  };

  const unstageAll = () => {
    setStagedFiles(new Set());
  };

  const handleCommitSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!commitMessage.trim()) return;
    onCommit(commitMessage.trim());
    setCommitMessage('');
    setStagedFiles(new Set());
  };

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-80 sm:w-96 select-none shrink-0 overflow-hidden">
      {/* Header */}
      <div className="px-3.5 py-2.5 border-b border-zinc-800 flex items-center justify-between bg-zinc-950">
        <div className="flex items-center gap-2">
          <GitBranch className="w-4 h-4 text-blue-400" />
          <span className="font-semibold text-xs text-white uppercase tracking-wider">
            Source Control (Git)
          </span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-[10px] px-2 py-0.5 rounded bg-zinc-900 border border-zinc-800 font-mono text-blue-300">
            {currentBranch}
          </span>
        </div>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-2 gap-1 p-2 bg-zinc-900/50 border-b border-zinc-800 text-[11px]">
        <button
          onClick={() => setActiveTab('changes')}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1.5 transition-colors ${
            activeTab === 'changes' ? 'bg-blue-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <GitPullRequest className="w-3 h-3" />
          <span>Changes ({modifiedFiles.length})</span>
        </button>
        <button
          onClick={() => setActiveTab('history')}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1.5 transition-colors ${
            activeTab === 'history' ? 'bg-blue-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <History className="w-3 h-3" />
          <span>Commit Log ({commits.length})</span>
        </button>
      </div>

      {/* Tab: Changes */}
      {activeTab === 'changes' && (
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Commit Message Box */}
          <form onSubmit={handleCommitSubmit} className="p-3 border-b border-zinc-800 space-y-2">
            <textarea
              rows={2}
              value={commitMessage}
              onChange={(e) => setCommitMessage(e.target.value)}
              placeholder="Commit message (e.g. feat: implement Jetpack Compose UI)"
              className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-blue-500 resize-none font-sans"
            />
            <button
              type="submit"
              disabled={!commitMessage.trim() || modifiedFiles.length === 0}
              className="w-full py-1.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white rounded-lg text-xs font-semibold shadow-md shadow-blue-600/20 flex items-center justify-center gap-1.5 transition-all"
            >
              <Check className="w-3.5 h-3.5" />
              <span>Commit Changes</span>
            </button>
          </form>

          {/* Staging & Changes List */}
          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            {/* Header with Stage All / Unstage All */}
            <div className="flex items-center justify-between text-xs text-zinc-400">
              <span className="font-semibold uppercase text-[10px] tracking-wider text-zinc-500">
                Working Tree Changes
              </span>
              <div className="flex items-center gap-2">
                <button
                  onClick={stageAll}
                  className="text-[11px] text-blue-400 hover:underline"
                >
                  Stage All
                </button>
                <span className="text-zinc-700">•</span>
                <button
                  onClick={unstageAll}
                  className="text-[11px] text-zinc-400 hover:underline"
                >
                  Unstage All
                </button>
              </div>
            </div>

            {modifiedFiles.length === 0 ? (
              <div className="text-center py-8 text-zinc-600 text-xs">
                <CheckCheck className="w-8 h-8 mx-auto mb-2 text-zinc-700" />
                Working tree is clean. No uncommitted modifications.
              </div>
            ) : (
              <div className="space-y-1">
                {modifiedFiles.map((file) => {
                  const isStaged = stagedFiles.has(file.path);
                  return (
                    <div
                      key={file.path}
                      className="group flex items-center justify-between p-2 rounded-lg bg-zinc-900/60 border border-zinc-800/80 text-xs hover:border-zinc-700 transition-colors"
                    >
                      <div
                        onClick={() => onOpenDiff(file)}
                        className="flex items-center gap-2 min-w-0 flex-1 cursor-pointer"
                      >
                        <span className="w-2 h-2 rounded-full bg-amber-400 shrink-0" />
                        <span className="font-mono text-[11px] text-zinc-200 truncate">
                          {file.path}
                        </span>
                      </div>

                      <div className="flex items-center gap-1 shrink-0 ml-2">
                        <button
                          onClick={() => toggleStage(file.path)}
                          title={isStaged ? 'Unstage' : 'Stage for commit'}
                          className={`p-1 rounded text-[11px] font-bold ${
                            isStaged
                              ? 'text-emerald-400 hover:bg-emerald-950/40'
                              : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
                          }`}
                        >
                          {isStaged ? <Minus className="w-3.5 h-3.5" /> : <Plus className="w-3.5 h-3.5" />}
                        </button>
                        <button
                          onClick={() => {
                            if (confirm(`Discard modifications to ${file.path}?`)) {
                              onDiscardChanges(file.path);
                            }
                          }}
                          title="Discard changes"
                          className="p-1 text-zinc-500 hover:text-red-400 hover:bg-zinc-800 rounded"
                        >
                          <RotateCcw className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab: Commit History */}
      {activeTab === 'history' && (
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {commits.length === 0 ? (
            <div className="text-center py-8 text-zinc-600 text-xs">No commits recorded yet.</div>
          ) : (
            commits.map((commit) => (
              <div
                key={commit.id}
                onClick={() => setSelectedCommit(selectedCommit?.id === commit.id ? null : commit)}
                className={`p-2.5 rounded-lg border cursor-pointer transition-colors ${
                  selectedCommit?.id === commit.id
                    ? 'bg-blue-950/30 border-blue-600/50 text-blue-200'
                    : 'bg-zinc-900/60 border-zinc-800/80 text-zinc-300 hover:border-zinc-700'
                }`}
              >
                <div className="flex items-center justify-between text-[11px] mb-1">
                  <span className="font-mono text-blue-400 font-semibold">{commit.shortId}</span>
                  <span className="text-zinc-500 text-[10px]">{commit.timestamp}</span>
                </div>
                <div className="text-xs font-medium text-zinc-200 mb-1">{commit.message}</div>
                <div className="text-[10px] text-zinc-400 flex items-center justify-between">
                  <span>{commit.author}</span>
                  <span className="font-mono">{commit.changedFiles.length} files</span>
                </div>

                {selectedCommit?.id === commit.id && (
                  <div className="mt-2 pt-2 border-t border-zinc-800/80 space-y-1">
                    <span className="text-[10px] text-zinc-500 block">Committed Files:</span>
                    {commit.changedFiles.map((path, idx) => (
                      <div key={idx} className="font-mono text-[10px] text-zinc-400 truncate pl-2">
                        • {path}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};
