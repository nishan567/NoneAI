import React, { useState } from 'react';
import {
  Sparkles,
  HelpCircle,
  Wrench,
  Bug,
  TestTube2,
  AlertCircle,
  Send,
  Copy,
  Check,
  ArrowRight,
  PlusCircle,
  FileCode2,
} from 'lucide-react';
import { ProjectFile, SupportedLanguage } from '../types';
import { highlightCode } from '../utils/syntaxHighlighter';

interface Props {
  activeFile: ProjectFile | null;
  files: ProjectFile[];
  onInsertCode: (snippet: string) => void;
  onReplaceCode: (newContent: string) => void;
  onCreateNewFile: (path: string, content: string) => void;
  onOpenDiffModal: (original: string, updated: string, title: string) => void;
}

type AiMode = 'generate' | 'explain' | 'refactor' | 'debug' | 'tests' | 'error-fix';

export const AiAssistantPanel: React.FC<Props> = ({
  activeFile,
  files,
  onInsertCode,
  onReplaceCode,
  onCreateNewFile,
  onOpenDiffModal,
}) => {
  const [mode, setMode] = useState<AiMode>('generate');
  const [prompt, setPrompt] = useState('');
  const [errorInput, setErrorInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [resultText, setResultText] = useState('');
  const [extractedCode, setExtractedCode] = useState('');
  const [copied, setCopied] = useState(false);

  // Quick suggestion chips based on mode and language
  const suggestions: Record<AiMode, string[]> = {
    generate: [
      'Create an Android Jetpack Compose list screen with Material3 cards and pull-to-refresh',
      'Create a Kotlin StateFlow ViewModel for fetching data with coroutines',
      'Add a Room Database Entity and DAO for caching items locally',
      'Write a Python FastAPI async endpoint with Pydantic request validation',
      'Create a TypeScript custom React hook with debounce and caching',
    ],
    explain: [
      'Explain the architecture and how data flows through this file',
      'Analyze the time and space complexity of these methods',
      'Explain Android lifecycle considerations in this component',
    ],
    refactor: [
      'Refactor to follow modern clean architecture and SOLID principles',
      'Optimize performance and minimize object allocations',
      'Extract duplicate logic into reusable helper functions',
      'Make idiomatic with Kotlin standard library functions (apply, let, also)',
    ],
    debug: [
      'Scan for potential null pointer exceptions, unhandled exceptions, or leaks',
      'Check for coroutine job leaks and improper cancellation',
      'Detect race conditions or concurrency issues in shared state',
    ],
    tests: [
      'Generate comprehensive JUnit 5 unit tests with MockK',
      'Write unit tests covering edge cases and empty data lists',
      'Add UI test for Composable with createComposeRule',
    ],
    'error-fix': [
      'Unresolved reference: Cannot access ViewModel',
      'AAPT: error: resource not found in values/strings.xml',
      'NullPointerException: Attempt to invoke virtual method on a null object reference',
    ],
  };

  const handleExecuteAi = async (customPrompt?: string) => {
    const textToRun = customPrompt || prompt;
    if (!textToRun && mode !== 'explain' && mode !== 'debug' && mode !== 'tests' && mode !== 'refactor' && mode !== 'error-fix') {
      return;
    }

    setIsLoading(true);
    setResultText('');
    setExtractedCode('');

    try {
      if (mode === 'generate') {
        const res = await fetch('/api/gemini/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            prompt: textToRun,
            language: activeFile?.language || 'kotlin',
            contextCode: activeFile?.content || '',
            activeFilePath: activeFile?.path || '',
            projectSummary: `Project files count: ${files.length}. Active file: ${activeFile?.path}`,
          }),
        });
        const data = await res.json();
        if (data.error) throw new Error(data.error);

        const text = data.text || '';
        setResultText(text);

        // Extract code inside ``` ... ```
        const codeMatch = text.match(/```[a-zA-Z]*\n([\s\S]*?)```/);
        if (codeMatch && codeMatch[1]) {
          setExtractedCode(codeMatch[1].trim());
        }
      } else if (mode === 'error-fix') {
        const res = await fetch('/api/gemini/fix-error', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            errorMessage: errorInput || textToRun,
            activeFileContent: activeFile?.content || '',
            activeFilePath: activeFile?.path || '',
            language: activeFile?.language || 'kotlin',
            relatedFiles: files.slice(0, 3),
          }),
        });
        const data = await res.json();
        if (data.error) throw new Error(data.error);

        let formatted = `**Root Cause Diagnostics:**\n${data.rootCause || 'Identified potential issue'}\n\n`;
        if (data.diffSummary) {
          formatted += `**Summary of Changes:**\n${data.diffSummary}\n\n`;
        }
        if (data.stepByStepGuidance) {
          formatted += `**Step-by-Step Guidance:**\n${data.stepByStepGuidance}\n\n`;
        }
        setResultText(formatted);
        if (data.fixedContent) {
          setExtractedCode(data.fixedContent);
        }
      } else {
        // Explain, refactor, debug, tests
        const res = await fetch('/api/gemini/assist', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            mode: mode === 'tests' ? 'unit-tests' : mode,
            code: activeFile?.content || '',
            language: activeFile?.language || 'kotlin',
            activeFilePath: activeFile?.path || '',
            userGoal: textToRun,
          }),
        });
        const data = await res.json();
        if (data.error) throw new Error(data.error);

        const text = data.result || '';
        setResultText(text);

        const codeMatch = text.match(/```[a-zA-Z]*\n([\s\S]*?)```/);
        if (codeMatch && codeMatch[1]) {
          setExtractedCode(codeMatch[1].trim());
        }
      }
    } catch (err: any) {
      setResultText(`❌ Error processing request: ${err.message}`);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCopy = async (text: string) => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-80 sm:w-96 select-none shrink-0 overflow-hidden">
      {/* Header */}
      <div className="px-3.5 py-2.5 border-b border-zinc-800 flex items-center justify-between bg-zinc-950">
        <div className="flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-indigo-400" />
          <span className="font-semibold text-xs text-white uppercase tracking-wider">
            AI Coding Assistant
          </span>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-950/70 text-indigo-300 border border-indigo-800/40 font-mono">
          Gemini 3.8 Flash
        </span>
      </div>

      {/* Mode Navigation Tabs */}
      <div className="grid grid-cols-3 gap-1 p-2 bg-zinc-900/50 border-b border-zinc-800 text-[11px]">
        <button
          onClick={() => { setMode('generate'); setResultText(''); setExtractedCode(''); }}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            mode === 'generate' ? 'bg-indigo-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Sparkles className="w-3 h-3" /> Generate
        </button>
        <button
          onClick={() => { setMode('explain'); setResultText(''); setExtractedCode(''); }}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            mode === 'explain' ? 'bg-indigo-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <HelpCircle className="w-3 h-3" /> Explain
        </button>
        <button
          onClick={() => { setMode('refactor'); setResultText(''); setExtractedCode(''); }}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            mode === 'refactor' ? 'bg-indigo-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Wrench className="w-3 h-3" /> Refactor
        </button>
        <button
          onClick={() => { setMode('debug'); setResultText(''); setExtractedCode(''); }}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            mode === 'debug' ? 'bg-indigo-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Bug className="w-3 h-3" /> Debug
        </button>
        <button
          onClick={() => { setMode('tests'); setResultText(''); setExtractedCode(''); }}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            mode === 'tests' ? 'bg-indigo-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <TestTube2 className="w-3 h-3" /> Tests
        </button>
        <button
          onClick={() => { setMode('error-fix'); setResultText(''); setExtractedCode(''); }}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            mode === 'error-fix' ? 'bg-red-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <AlertCircle className="w-3 h-3" /> Fix Error
        </button>
      </div>

      {/* Target File Context Banner */}
      <div className="px-3 py-1.5 bg-zinc-950/70 border-b border-zinc-900 text-[11px] text-zinc-400 flex items-center justify-between">
        <span className="truncate">
          Active: <span className="text-zinc-200 font-mono">{activeFile?.path || 'None'}</span>
        </span>
        <span className="text-zinc-500 uppercase">{activeFile?.language || 'text'}</span>
      </div>

      {/* Input / Form Section */}
      <div className="p-3 border-b border-zinc-800 space-y-2.5">
        {mode === 'error-fix' ? (
          <div>
            <label className="text-[11px] font-medium text-zinc-400 block mb-1">
              Paste Build Error or Runtime Stack Trace:
            </label>
            <textarea
              rows={4}
              value={errorInput}
              onChange={(e) => setErrorInput(e.target.value)}
              placeholder="e.g. e: /app/MainActivity.kt: (24, 9): Unresolved reference: TaskRepository..."
              className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 placeholder-zinc-500 font-mono focus:outline-none focus:border-red-500 resize-none"
            />
          </div>
        ) : (
          <div>
            <label className="text-[11px] font-medium text-zinc-400 block mb-1">
              {mode === 'generate'
                ? 'What do you want to build or generate?'
                : mode === 'explain'
                ? 'Optional specific question or focus area:'
                : mode === 'refactor'
                ? 'Refactoring goals (e.g. clean code, speed, coroutines):'
                : mode === 'debug'
                ? 'Suspected bug or behavior to investigate:'
                : 'Target test framework or scenarios to test:'}
            </label>
            <textarea
              rows={3}
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder={
                mode === 'generate'
                  ? 'e.g., Create an Android Jetpack Compose bottom navigation bar with 3 tabs...'
                  : 'Leave blank to analyze entire active file, or specify focus...'
              }
              className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded-lg text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 resize-none"
            />
          </div>
        )}

        {/* Suggestion Chips */}
        <div>
          <span className="text-[10px] text-zinc-500 block mb-1 font-medium uppercase tracking-wider">
            Quick Prompts:
          </span>
          <div className="flex flex-wrap gap-1 max-h-24 overflow-y-auto no-scrollbar">
            {suggestions[mode]?.map((s, idx) => (
              <button
                key={idx}
                onClick={() => {
                  if (mode === 'error-fix') {
                    setErrorInput(s);
                  } else {
                    setPrompt(s);
                  }
                  handleExecuteAi(s);
                }}
                className="text-[10px] px-2 py-1 bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 rounded border border-zinc-800/80 transition-colors text-left truncate max-w-full"
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        {/* Submit Button */}
        <button
          onClick={() => handleExecuteAi()}
          disabled={isLoading}
          className="w-full py-2 bg-gradient-to-r from-indigo-600 to-blue-600 hover:from-indigo-500 hover:to-blue-500 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-lg shadow-indigo-600/20 flex items-center justify-center gap-1.5 transition-all"
        >
          {isLoading ? (
            <>
              <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              <span>Analyzing with Gemini...</span>
            </>
          ) : (
            <>
              <Send className="w-3.5 h-3.5" />
              <span>
                {mode === 'generate'
                  ? 'Generate Code'
                  : mode === 'error-fix'
                  ? 'Analyze & Fix Error'
                  : `Run ${mode.toUpperCase()}`}
              </span>
            </>
          )}
        </button>
      </div>

      {/* Result Output Area */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {resultText ? (
          <div className="space-y-3 text-xs">
            {/* Quick action bar for extracted code */}
            {extractedCode && (
              <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-2.5 space-y-2">
                <div className="flex items-center justify-between text-[11px] font-medium text-emerald-400">
                  <span className="flex items-center gap-1">
                    <FileCode2 className="w-3.5 h-3.5" />
                    Code Solution Ready ({extractedCode.split('\n').length} lines)
                  </span>
                  <button
                    onClick={() => handleCopy(extractedCode)}
                    className="flex items-center gap-1 text-zinc-400 hover:text-white"
                  >
                    {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copied ? 'Copied' : 'Copy'}</span>
                  </button>
                </div>

                <div className="flex flex-wrap gap-1.5 pt-1">
                  {activeFile && (
                    <>
                      <button
                        onClick={() => onInsertCode(extractedCode)}
                        className="px-2 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-[11px] font-medium flex items-center gap-1 transition-colors"
                      >
                        <PlusCircle className="w-3 h-3 text-blue-400" />
                        Insert Code
                      </button>
                      <button
                        onClick={() =>
                          onOpenDiffModal(
                            activeFile.content,
                            extractedCode,
                            `Review AI Changes to ${activeFile.path}`
                          )
                        }
                        className="px-2 py-1 bg-blue-600/30 border border-blue-500/40 hover:bg-blue-600/50 text-blue-200 rounded text-[11px] font-medium flex items-center gap-1 transition-colors"
                      >
                        <ArrowRight className="w-3 h-3" />
                        Review Diff
                      </button>
                      <button
                        onClick={() => onReplaceCode(extractedCode)}
                        className="px-2 py-1 bg-emerald-600/30 border border-emerald-500/40 hover:bg-emerald-600/50 text-emerald-200 rounded text-[11px] font-medium flex items-center gap-1 transition-colors"
                      >
                        Replace File
                      </button>
                    </>
                  )}
                  <button
                    onClick={() => {
                      const newPath = window.prompt('Save new file as (e.g. app/src/main/java/com/nonea/ai/Generated.kt):');
                      if (newPath) {
                        onCreateNewFile(newPath, extractedCode);
                      }
                    }}
                    className="px-2 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded text-[11px] font-medium transition-colors"
                  >
                    Save as New File
                  </button>
                </div>

                <div className="bg-zinc-950 p-2.5 rounded border border-zinc-800 font-mono text-[11px] max-h-48 overflow-y-auto select-text">
                  <pre
                    className="whitespace-pre-wrap text-zinc-300"
                    dangerouslySetInnerHTML={{
                      __html: highlightCode(extractedCode, activeFile?.language || 'kotlin'),
                    }}
                  />
                </div>
              </div>
            )}

            {/* Explanation / Notes text */}
            <div className="bg-zinc-900/60 border border-zinc-800/80 rounded-lg p-3 text-zinc-300 leading-relaxed font-sans select-text whitespace-pre-wrap">
              {resultText}
            </div>
          </div>
        ) : (
          <div className="h-full flex flex-col items-center justify-center text-center p-6 text-zinc-600">
            <Sparkles className="w-8 h-8 mb-2 stroke-1 text-zinc-700" />
            <p className="text-xs">Ask Gemini to write, explain, refactor, or debug code across your workspace.</p>
          </div>
        )}
      </div>
    </div>
  );
};
