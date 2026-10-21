import React, { useState, useRef, useEffect } from 'react';
import {
  Copy,
  Check,
  Search,
  ZoomIn,
  ZoomOut,
  Save,
  Code,
  X,
  FileCode,
  Sparkles,
  ChevronRight,
  Maximize2,
  Minimize2,
} from 'lucide-react';
import { ProjectFile, SupportedLanguage } from '../types';
import { highlightCode, getLanguageFromPath } from '../utils/syntaxHighlighter';

interface Props {
  activeFile: ProjectFile | null;
  openTabs: string[];
  onSelectTab: (path: string) => void;
  onCloseTab: (path: string) => void;
  onUpdateContent: (content: string) => void;
  onChangeLanguage: (lang: SupportedLanguage) => void;
  onSaveFile: () => void;
  onTriggerAiAssist?: (mode: string) => void;
}

const SUPPORTED_LANGUAGES: { id: SupportedLanguage; label: string }[] = [
  { id: 'kotlin', label: 'Kotlin (.kt, .kts)' },
  { id: 'java', label: 'Java (.java)' },
  { id: 'python', label: 'Python (.py)' },
  { id: 'typescript', label: 'TypeScript (.ts, .tsx)' },
  { id: 'javascript', label: 'JavaScript (.js, .jsx)' },
  { id: 'xml', label: 'XML / Android Manifest' },
  { id: 'gradle', label: 'Gradle DSL' },
  { id: 'json', label: 'JSON' },
  { id: 'html', label: 'HTML5' },
  { id: 'css', label: 'CSS3' },
  { id: 'bash', label: 'Shell / Bash' },
  { id: 'sql', label: 'SQL' },
  { id: 'markdown', label: 'Markdown' },
  { id: 'plaintext', label: 'Plain Text' },
];

export const CodeEditor: React.FC<Props> = ({
  activeFile,
  openTabs,
  onSelectTab,
  onCloseTab,
  onUpdateContent,
  onChangeLanguage,
  onSaveFile,
  onTriggerAiAssist,
}) => {
  const [copied, setCopied] = useState(false);
  const [fontSize, setFontSize] = useState(13);
  const [cursorPos, setCursorPos] = useState({ line: 1, col: 1 });
  const [showFind, setShowFind] = useState(false);
  const [findQuery, setFindQuery] = useState('');
  const [replaceQuery, setReplaceQuery] = useState('');
  const [isFullScreen, setIsFullScreen] = useState(false);

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const lineNumbersRef = useRef<HTMLDivElement>(null);
  const highlightOverlayRef = useRef<HTMLPreElement>(null);

  // Sync scrolling between textarea, highlight layer, and line numbers
  const handleScroll = (e: React.UIEvent<HTMLTextAreaElement>) => {
    const target = e.currentTarget;
    if (lineNumbersRef.current) {
      lineNumbersRef.current.scrollTop = target.scrollTop;
    }
    if (highlightOverlayRef.current) {
      highlightOverlayRef.current.scrollTop = target.scrollTop;
      highlightOverlayRef.current.scrollLeft = target.scrollLeft;
    }
  };

  // Track cursor position
  const updateCursorPosition = () => {
    if (!textareaRef.current) return;
    const pos = textareaRef.current.selectionStart || 0;
    const textBefore = (activeFile?.content || '').slice(0, pos);
    const lines = textBefore.split('\n');
    setCursorPos({
      line: lines.length,
      col: lines[lines.length - 1].length + 1,
    });
  };

  // Keyboard shortcut listener (Ctrl+S, Ctrl+F, Tab)
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
      e.preventDefault();
      onSaveFile();
      return;
    }

    if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
      e.preventDefault();
      setShowFind(true);
      return;
    }

    // Tab key support
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = textareaRef.current;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const current = activeFile?.content || '';
      const replacement = '    '; // 4 spaces

      const updated = current.substring(0, start) + replacement + current.substring(end);
      onUpdateContent(updated);

      setTimeout(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 4;
        updateCursorPosition();
      }, 0);
    }
  };

  const handleCopyCode = async () => {
    if (!activeFile) return;
    await navigator.clipboard.writeText(activeFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleFindReplace = () => {
    if (!findQuery || !activeFile) return;
    const updated = activeFile.content.replaceAll(findQuery, replaceQuery);
    onUpdateContent(updated);
  };

  if (!activeFile) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center bg-zinc-950 text-zinc-500 p-8">
        <Code className="w-16 h-16 text-zinc-800 mb-4 stroke-1" />
        <h3 className="text-lg font-medium text-zinc-400">No File Opened</h3>
        <p className="text-xs text-zinc-600 mt-1 max-w-sm text-center">
          Select a file from the explorer on the left or create a new file to start coding.
        </p>
      </div>
    );
  }

  const lines = (activeFile.content || '').split('\n');
  const lineCount = lines.length;

  // Breadcrumbs parts
  const breadcrumbs = activeFile.path.split('/');

  return (
    <div className={`flex-1 flex flex-col bg-zinc-950 overflow-hidden ${isFullScreen ? 'fixed inset-0 z-50' : ''}`}>
      {/* 1. Multi-Tab Bar */}
      <div className="h-9 bg-zinc-900/90 border-b border-zinc-800 flex items-center overflow-x-auto select-none no-scrollbar shrink-0">
        {openTabs.map((path) => {
          const isActive = path === activeFile.path;
          const fileName = path.split('/').pop() || path;
          return (
            <div
              key={path}
              onClick={() => onSelectTab(path)}
              className={`group flex items-center gap-2 px-3 h-full border-r border-zinc-800/80 cursor-pointer text-xs font-medium transition-colors ${
                isActive
                  ? 'bg-zinc-950 text-zinc-100 border-t-2 border-t-blue-500 shadow-inner'
                  : 'bg-zinc-900/50 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900'
              }`}
            >
              <FileCode className={`w-3.5 h-3.5 ${isActive ? 'text-blue-400' : 'text-zinc-500'}`} />
              <span className="truncate max-w-[140px]">{fileName}</span>
              {activeFile.isModified && isActive && (
                <span className="w-2 h-2 rounded-full bg-amber-400 shrink-0" />
              )}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseTab(path);
                }}
                className="opacity-0 group-hover:opacity-100 p-0.5 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
              >
                <X className="w-3 h-3" />
              </button>
            </div>
          );
        })}
      </div>

      {/* 2. Breadcrumbs & Editor Toolbar */}
      <div className="h-8 bg-zinc-950/90 border-b border-zinc-800/70 px-3 flex items-center justify-between text-xs text-zinc-400 select-none shrink-0">
        {/* Breadcrumb path */}
        <div className="flex items-center gap-1 font-mono text-[11px] truncate flex-1 mr-4">
          {breadcrumbs.map((part, idx) => (
            <React.Fragment key={idx}>
              {idx > 0 && <ChevronRight className="w-3 h-3 text-zinc-600 shrink-0" />}
              <span className={idx === breadcrumbs.length - 1 ? 'text-zinc-200 font-medium' : 'text-zinc-500'}>
                {part}
              </span>
            </React.Fragment>
          ))}
        </div>

        {/* Toolbar Controls */}
        <div className="flex items-center gap-1.5 shrink-0">
          {/* Quick AI Trigger */}
          {onTriggerAiAssist && (
            <button
              onClick={() => onTriggerAiAssist('explain')}
              className="px-2 py-0.5 rounded bg-indigo-950/60 text-indigo-300 border border-indigo-800/50 hover:bg-indigo-900/60 text-[11px] flex items-center gap-1 transition-colors"
            >
              <Sparkles className="w-3 h-3 text-indigo-400" />
              <span className="hidden sm:inline">Explain</span>
            </button>
          )}

          {/* Language selector */}
          <select
            value={activeFile.language}
            onChange={(e) => onChangeLanguage(e.target.value as SupportedLanguage)}
            className="bg-zinc-900 border border-zinc-800 text-zinc-300 rounded px-2 py-0.5 text-[11px] font-mono focus:outline-none focus:border-blue-500 cursor-pointer"
          >
            {SUPPORTED_LANGUAGES.map((l) => (
              <option key={l.id} value={l.id}>
                {l.label}
              </option>
            ))}
          </select>

          {/* Find */}
          <button
            onClick={() => setShowFind(!showFind)}
            title="Find & Replace (Ctrl+F)"
            className={`p-1 rounded transition-colors ${
              showFind ? 'bg-blue-600/20 text-blue-400' : 'hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <Search className="w-3.5 h-3.5" />
          </button>

          {/* Zoom In/Out */}
          <button
            onClick={() => setFontSize((s) => Math.max(10, s - 1))}
            title="Decrease font size"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          >
            <ZoomOut className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => setFontSize((s) => Math.min(22, s + 1))}
            title="Increase font size"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          >
            <ZoomIn className="w-3.5 h-3.5" />
          </button>

          {/* Copy */}
          <button
            onClick={handleCopyCode}
            title="Copy Code"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
          </button>

          {/* Save */}
          <button
            onClick={onSaveFile}
            title="Save File (Ctrl+S)"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          >
            <Save className="w-3.5 h-3.5" />
          </button>

          {/* Full Screen */}
          <button
            onClick={() => setIsFullScreen(!isFullScreen)}
            title={isFullScreen ? 'Exit Full Screen' : 'Full Screen'}
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          >
            {isFullScreen ? <Minimize2 className="w-3.5 h-3.5" /> : <Maximize2 className="w-3.5 h-3.5" />}
          </button>
        </div>
      </div>

      {/* 3. Find & Replace Bar */}
      {showFind && (
        <div className="bg-zinc-900 border-b border-zinc-800 px-4 py-2 flex items-center gap-3 text-xs animate-in slide-in-from-top-1">
          <div className="flex items-center gap-1.5 flex-1 max-w-sm">
            <span className="text-zinc-500 font-mono text-[10px]">Find:</span>
            <input
              type="text"
              value={findQuery}
              onChange={(e) => setFindQuery(e.target.value)}
              placeholder="Search..."
              className="flex-1 bg-zinc-950 border border-zinc-700 rounded px-2 py-1 text-zinc-200 text-xs focus:outline-none focus:border-blue-500 font-mono"
            />
          </div>
          <div className="flex items-center gap-1.5 flex-1 max-w-sm">
            <span className="text-zinc-500 font-mono text-[10px]">Replace:</span>
            <input
              type="text"
              value={replaceQuery}
              onChange={(e) => setReplaceQuery(e.target.value)}
              placeholder="Replace with..."
              className="flex-1 bg-zinc-950 border border-zinc-700 rounded px-2 py-1 text-zinc-200 text-xs focus:outline-none focus:border-blue-500 font-mono"
            />
          </div>
          <button
            onClick={handleFindReplace}
            className="px-2.5 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded text-xs font-medium"
          >
            Replace All
          </button>
          <button
            onClick={() => setShowFind(false)}
            className="text-zinc-400 hover:text-zinc-200 p-1"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* 4. Core Editor Canvas (Line Numbers + Syntax-highlighted code editor) */}
      <div className="flex-1 flex relative overflow-hidden bg-zinc-950">
        {/* Line Numbers Gutter */}
        <div
          ref={lineNumbersRef}
          className="w-12 bg-zinc-950/80 border-r border-zinc-900 text-right pr-3 pt-3 select-none text-zinc-600 font-mono shrink-0 overflow-hidden"
          style={{ fontSize: `${fontSize}px`, lineHeight: `${fontSize * 1.6}px` }}
        >
          {Array.from({ length: lineCount }).map((_, i) => (
            <div
              key={i}
              className={`leading-relaxed ${
                cursorPos.line === i + 1 ? 'text-zinc-300 font-bold' : ''
              }`}
            >
              {i + 1}
            </div>
          ))}
        </div>

        {/* Editor Area */}
        <div className="flex-1 relative h-full overflow-hidden">
          {/* Syntax highlighting background layer */}
          <pre
            ref={highlightOverlayRef}
            aria-hidden="true"
            className="absolute inset-0 p-3 m-0 font-mono pointer-events-none whitespace-pre overflow-hidden text-zinc-200 select-none z-0"
            style={{
              fontSize: `${fontSize}px`,
              lineHeight: `${fontSize * 1.6}px`,
              fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
            }}
            dangerouslySetInnerHTML={{
              __html: highlightCode(activeFile.content, activeFile.language),
            }}
          />

          {/* Transparent editable textarea for input and cursor navigation */}
          <textarea
            ref={textareaRef}
            value={activeFile.content}
            onChange={(e) => {
              onUpdateContent(e.target.value);
              updateCursorPosition();
            }}
            onKeyUp={updateCursorPosition}
            onClick={updateCursorPosition}
            onScroll={handleScroll}
            onKeyDown={handleKeyDown}
            spellCheck={false}
            autoCapitalize="off"
            autoComplete="off"
            className="absolute inset-0 p-3 m-0 font-mono bg-transparent text-transparent caret-white resize-none outline-none whitespace-pre overflow-auto z-10 selection:bg-blue-600/30 selection:text-white"
            style={{
              fontSize: `${fontSize}px`,
              lineHeight: `${fontSize * 1.6}px`,
              fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
            }}
          />
        </div>
      </div>

      {/* 5. Status Bar */}
      <div className="h-6 bg-zinc-900 border-t border-zinc-800/80 px-3 flex items-center justify-between text-[11px] text-zinc-400 font-mono select-none shrink-0">
        <div className="flex items-center gap-3">
          <span>
            Ln {cursorPos.line}, Col {cursorPos.col}
          </span>
          <span>{lineCount} lines</span>
          <span>{activeFile.content.length} chars</span>
        </div>

        <div className="flex items-center gap-4">
          <span>UTF-8</span>
          <span>Spaces: 4</span>
          <span className="uppercase text-zinc-300">{activeFile.language}</span>
          <span className="text-emerald-400">● Git: main</span>
        </div>
      </div>
    </div>
  );
};
