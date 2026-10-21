import React, { useState } from 'react';
import {
  Layers,
  Sparkles,
  CheckCheck,
  FileCode,
  Terminal,
  Check,
  Copy,
  ChevronRight,
  Info,
} from 'lucide-react';
import { ProjectFile, MultiFileResponse, MultiFileSpec } from '../types';
import { highlightCode } from '../utils/syntaxHighlighter';

interface Props {
  currentFiles: ProjectFile[];
  onApplyMultiFiles: (files: MultiFileSpec[]) => void;
  onRunSuggestedCommand?: (command: string, purpose: string) => void;
}

export const MultiFileGenerator: React.FC<Props> = ({
  currentFiles,
  onApplyMultiFiles,
  onRunSuggestedCommand,
}) => {
  const [prompt, setPrompt] = useState('');
  const [projectType, setProjectType] = useState<'android' | 'python' | 'fullstack' | 'kotlin'>('android');
  const [isLoading, setIsLoading] = useState(false);
  const [response, setResponse] = useState<MultiFileResponse | null>(null);
  const [activePreviewIndex, setActivePreviewIndex] = useState(0);
  const [applied, setApplied] = useState(false);

  const presets = [
    {
      label: 'Android Room Database & Repository',
      type: 'android' as const,
      prompt: 'Create a complete Android Room database module with UserEntity, UserDao, AppDatabase, and UserRepository with StateFlow reactive queries.',
    },
    {
      label: 'Android Authentication Screen & ViewModel',
      type: 'android' as const,
      prompt: 'Generate an Android Jetpack Compose Login and Register flow with email/password validation, AuthViewModel, and Navigation destination graph.',
    },
    {
      label: 'Python Microservice with Models & Routes',
      type: 'python' as const,
      prompt: 'Create a modular FastAPI project with routers/items.py, models/item.py, database.py, and unit test test_items.py.',
    },
    {
      label: 'Fullstack React + Express CRUD API',
      type: 'fullstack' as const,
      prompt: 'Build a coordinated multi-file CRUD feature: Express backend router, controller, TypeScript interface, and React frontend table component.',
    },
  ];

  const handleGenerate = async (customPrompt?: string, customType?: any) => {
    const text = customPrompt || prompt;
    if (!text.trim()) return;

    setIsLoading(true);
    setApplied(false);

    try {
      const res = await fetch('/api/gemini/multi-file-generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: text,
          projectType: customType || projectType,
          currentFiles: currentFiles.map((f) => ({ path: f.path })),
        }),
      });

      const data = await res.json();
      if (data.error) throw new Error(data.error);

      setResponse(data);
      setActivePreviewIndex(0);
    } catch (err: any) {
      setResponse({
        summary: `Error generating files: ${err.message}`,
        buildInstructions: 'Please verify project configuration and try again.',
        files: [],
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleApplyAll = () => {
    if (!response || !response.files || response.files.length === 0) return;
    onApplyMultiFiles(response.files);
    setApplied(true);
    setTimeout(() => setApplied(false), 3000);
  };

  const currentFile = response?.files?.[activePreviewIndex];

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-80 sm:w-96 select-none shrink-0 overflow-hidden">
      {/* Header */}
      <div className="px-3.5 py-2.5 border-b border-zinc-800 flex items-center justify-between bg-zinc-950">
        <div className="flex items-center gap-2">
          <Layers className="w-4 h-4 text-purple-400" />
          <span className="font-semibold text-xs text-white uppercase tracking-wider">
            Multi-File Generator
          </span>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded-full bg-purple-950/70 text-purple-300 border border-purple-800/40">
          Architecture Engine
        </span>
      </div>

      {/* Input Form */}
      <div className="p-3 border-b border-zinc-800 space-y-2.5">
        <div>
          <div className="flex items-center justify-between mb-1">
            <label className="text-[11px] font-medium text-zinc-400">Target Framework:</label>
            <select
              value={projectType}
              onChange={(e) => setProjectType(e.target.value as any)}
              className="bg-zinc-900 border border-zinc-800 text-zinc-200 rounded px-2 py-0.5 text-[10px] focus:outline-none"
            >
              <option value="android">Android (Kotlin / Compose / Gradle)</option>
              <option value="python">Python (FastAPI / Backend)</option>
              <option value="fullstack">TypeScript / Fullstack</option>
              <option value="kotlin">Kotlin Multiplatform</option>
            </select>
          </div>
          <textarea
            rows={3}
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="e.g. Generate a complete Android Room Database architecture with Entity, Dao, Database, and Repository..."
            className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-purple-500 resize-none font-sans"
          />
        </div>

        {/* Quick Presets */}
        <div>
          <span className="text-[10px] text-zinc-500 block mb-1 font-medium uppercase tracking-wider">
            Architecture Templates:
          </span>
          <div className="space-y-1">
            {presets.map((p, idx) => (
              <button
                key={idx}
                onClick={() => {
                  setPrompt(p.prompt);
                  setProjectType(p.type);
                  handleGenerate(p.prompt, p.type);
                }}
                className="w-full text-left text-[11px] px-2 py-1.5 bg-zinc-900 hover:bg-zinc-800 text-zinc-300 hover:text-white rounded border border-zinc-800/80 transition-colors flex items-center justify-between group"
              >
                <span className="truncate">{p.label}</span>
                <ChevronRight className="w-3 h-3 text-zinc-500 group-hover:text-purple-400" />
              </button>
            ))}
          </div>
        </div>

        <button
          onClick={() => handleGenerate()}
          disabled={isLoading || !prompt.trim()}
          className="w-full py-2 bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-lg shadow-purple-600/20 flex items-center justify-center gap-1.5 transition-all"
        >
          {isLoading ? (
            <>
              <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              <span>Generating Multi-File Architecture...</span>
            </>
          ) : (
            <>
              <Sparkles className="w-3.5 h-3.5" />
              <span>Generate Coordinated Files</span>
            </>
          )}
        </button>
      </div>

      {/* Generated Files Preview & Batch Apply */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {response ? (
          <div className="space-y-3">
            {/* Summary card */}
            <div className="bg-zinc-900/80 border border-zinc-800 rounded-lg p-2.5 text-xs text-zinc-300 space-y-1">
              <div className="font-semibold text-purple-300 flex items-center gap-1.5">
                <Info className="w-3.5 h-3.5" />
                {response.summary || 'Architecture Plan Ready'}
              </div>
              <p className="text-[11px] text-zinc-400">
                Generated {response.files?.length || 0} coordinated files.
              </p>
            </div>

            {/* Batch Apply Button */}
            <button
              onClick={handleApplyAll}
              disabled={applied}
              className={`w-full py-2 text-xs font-semibold rounded-lg shadow-lg flex items-center justify-center gap-1.5 transition-all ${
                applied
                  ? 'bg-emerald-600 text-white'
                  : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-600/20'
              }`}
            >
              {applied ? (
                <>
                  <Check className="w-3.5 h-3.5" />
                  <span>All Files Applied to Project!</span>
                </>
              ) : (
                <>
                  <CheckCheck className="w-3.5 h-3.5" />
                  <span>Apply All {response.files?.length} Files to Project</span>
                </>
              )}
            </button>

            {/* File List selector tabs */}
            <div className="space-y-1">
              <span className="text-[10px] font-semibold text-zinc-500 uppercase tracking-wider block">
                Generated Files:
              </span>
              <div className="space-y-1 max-h-40 overflow-y-auto">
                {response.files?.map((file, idx) => (
                  <button
                    key={idx}
                    onClick={() => setActivePreviewIndex(idx)}
                    className={`w-full text-left p-2 rounded text-xs transition-colors border ${
                      activePreviewIndex === idx
                        ? 'bg-purple-950/40 border-purple-500/50 text-purple-200'
                        : 'bg-zinc-900/60 border-zinc-800/80 text-zinc-400 hover:text-zinc-200'
                    }`}
                  >
                    <div className="font-mono text-[11px] font-medium truncate">{file.path}</div>
                    <div className="text-[10px] text-zinc-500 truncate mt-0.5">{file.description}</div>
                  </button>
                ))}
              </div>
            </div>

            {/* Active File Content Viewer */}
            {currentFile && (
              <div className="bg-zinc-900 border border-zinc-800 rounded-lg overflow-hidden">
                <div className="px-3 py-1.5 bg-zinc-950 border-b border-zinc-800 flex items-center justify-between text-[11px]">
                  <span className="font-mono text-zinc-300 truncate max-w-[200px]">{currentFile.path}</span>
                  <span className="text-zinc-500 uppercase text-[10px]">{currentFile.language}</span>
                </div>
                <div className="p-2.5 max-h-60 overflow-auto bg-zinc-950 font-mono text-[11px] select-text">
                  <pre
                    className="whitespace-pre text-zinc-300"
                    dangerouslySetInnerHTML={{
                      __html: highlightCode(currentFile.content, currentFile.language),
                    }}
                  />
                </div>
              </div>
            )}

            {/* Suggested Commands (e.g. ./gradlew build) */}
            {response.suggestedCommands && response.suggestedCommands.length > 0 && (
              <div className="bg-zinc-900/60 border border-zinc-800 rounded-lg p-2.5 space-y-2">
                <span className="text-[10px] font-semibold text-zinc-400 uppercase tracking-wider block flex items-center gap-1">
                  <Terminal className="w-3 h-3 text-zinc-500" /> Suggested Verification Commands:
                </span>
                {response.suggestedCommands.map((sc, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between gap-2 p-1.5 bg-zinc-950 rounded border border-zinc-800/80 text-[11px]"
                  >
                    <code className="text-emerald-400 font-mono truncate flex-1">{sc.command}</code>
                    {onRunSuggestedCommand && (
                      <button
                        onClick={() => onRunSuggestedCommand(sc.command, sc.purpose)}
                        className="px-2 py-0.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-[10px] shrink-0"
                      >
                        Run
                      </button>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ) : (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-zinc-600">
            <Layers className="w-8 h-8 mb-2 stroke-1 text-zinc-700" />
            <p className="text-xs">
              Generate entire architectures or multi-file modules in one click with accurate file paths.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
