import React, { useRef } from 'react';
import {
  Code2,
  Download,
  Upload,
  Play,
  Sparkles,
  Smartphone,
  ChevronDown,
  RotateCcw,
  CheckCircle2,
  FolderGit2,
  Plus,
  Settings,
} from 'lucide-react';
import JSZip from 'jszip';
import { ProjectFile, ProjectTemplate } from '../types';
import { STARTER_TEMPLATES } from '../templates/starterProjects';
import { getLanguageFromPath } from '../utils/syntaxHighlighter';

interface Props {
  currentProjectName: string;
  files: ProjectFile[];
  onSelectTemplate: (template: ProjectTemplate) => void;
  onLoadNoneaAI: () => void;
  onBuildProject: () => void;
  onImportFiles: (importedFiles: ProjectFile[]) => void;
  onResetProject: () => void;
  onOpenSettings?: () => void;
  isBuilding: boolean;
  activeTab: string;
}

export const Header: React.FC<Props> = ({
  currentProjectName,
  files,
  onSelectTemplate,
  onLoadNoneaAI,
  onBuildProject,
  onImportFiles,
  onResetProject,
  onOpenSettings,
  isBuilding,
  activeTab,
}) => {
  const [showProjectMenu, setShowProjectMenu] = React.useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const zipInputRef = useRef<HTMLInputElement>(null);

  // Handle single / multiple file uploads
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const uploadedFiles = e.target.files;
    if (!uploadedFiles || uploadedFiles.length === 0) return;

    const newFiles: ProjectFile[] = [];
    const readCount = uploadedFiles.length;
    let completed = 0;

    Array.from(uploadedFiles).forEach((file) => {
      const reader = new FileReader();
      reader.onload = (event) => {
        const text = event.target?.result as string;
        newFiles.push({
          path: file.name,
          content: text || '',
          language: getLanguageFromPath(file.name),
          isModified: true,
        });
        completed++;
        if (completed === readCount) {
          onImportFiles(newFiles);
        }
      };
      reader.readAsText(file);
    });

    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  // Handle ZIP file upload & unzipping
  const handleZipUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const uploadedFiles = e.target.files;
    if (!uploadedFiles || uploadedFiles.length === 0) return;

    const file = uploadedFiles[0];
    try {
      const zip = new JSZip();
      const contents = await zip.loadAsync(file);
      const imported: ProjectFile[] = [];

      for (const [relativePath, zipEntry] of Object.entries(contents.files)) {
        if (!zipEntry.dir) {
          try {
            const text = await zipEntry.async('text');
            imported.push({
              path: relativePath,
              content: text,
              language: getLanguageFromPath(relativePath),
              isModified: false,
            });
          } catch {
            // Ignore binary entries
          }
        }
      }

      if (imported.length > 0) {
        onImportFiles(imported);
      }
    } catch (err) {
      console.error('Failed to unzip archive:', err);
    }

    if (zipInputRef.current) zipInputRef.current.value = '';
  };

  // Export full project as a .zip file
  const handleExportZip = async () => {
    try {
      const zip = new JSZip();
      files.forEach((file) => {
        zip.file(file.path, file.content);
      });

      const blob = await zip.generateAsync({ type: 'blob' });
      const sanitizedName = currentProjectName.replace(/[^a-zA-Z0-9_-]/g, '_').toLowerCase();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${sanitizedName}_project.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to export zip:', err);
    }
  };

  return (
    <header className="h-14 bg-zinc-950 border-b border-zinc-800 px-4 flex items-center justify-between text-zinc-200 select-none z-20 shrink-0">
      {/* Brand & Project Switcher */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2 font-semibold text-sm tracking-tight text-white">
          <div className="w-8 h-8 rounded-lg bg-gradient-to-tr from-blue-600 via-indigo-600 to-purple-600 flex items-center justify-center shadow-lg shadow-indigo-600/30">
            <Code2 className="w-4 h-4 text-white" />
          </div>
          <span className="bg-gradient-to-r from-white via-zinc-200 to-zinc-400 bg-clip-text text-transparent">
            NexusCode
          </span>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-zinc-800 text-indigo-400 border border-zinc-700/60 font-mono">
            v2.4
          </span>
        </div>

        <div className="h-4 w-px bg-zinc-800 hidden sm:block" />

        {/* Project Selector Dropdown */}
        <div className="relative">
          <button
            onClick={() => setShowProjectMenu(!showProjectMenu)}
            className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-zinc-900 hover:bg-zinc-800/80 border border-zinc-800 text-xs font-medium text-zinc-200 transition-colors"
          >
            <Smartphone className="w-3.5 h-3.5 text-blue-400" />
            <span className="max-w-[150px] sm:max-w-[220px] truncate">{currentProjectName}</span>
            <ChevronDown className="w-3.5 h-3.5 text-zinc-400" />
          </button>

          {showProjectMenu && (
            <div
              className="absolute left-0 mt-1.5 w-72 bg-zinc-900 border border-zinc-800 rounded-xl shadow-2xl p-1.5 z-50 text-xs animate-in fade-in slide-in-from-top-1"
              onMouseLeave={() => setShowProjectMenu(false)}
            >
              <div className="px-2.5 py-1.5 text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">
                Special User Attachment
              </div>
              <button
                onClick={() => {
                  onLoadNoneaAI();
                  setShowProjectMenu(false);
                }}
                className="w-full text-left px-2.5 py-2 rounded-lg hover:bg-zinc-800 flex items-center justify-between text-zinc-200 transition-colors group"
              >
                <div className="flex items-center gap-2">
                  <Smartphone className="w-4 h-4 text-purple-400" />
                  <div>
                    <div className="font-medium text-purple-300">NoneaAI Android App</div>
                    <div className="text-[10px] text-zinc-400">Attached v2.3 (30+ Real Kotlin Files)</div>
                  </div>
                </div>
                <span className="text-[10px] px-1.5 py-0.5 bg-purple-950/60 text-purple-300 rounded border border-purple-800/40">
                  Ready
                </span>
              </button>

              <a
                href="/api/download/nonea-ai.zip"
                download="NoneaAI-v2_3-apps.zip"
                onClick={() => setShowProjectMenu(false)}
                className="w-full text-left px-2.5 py-1.5 rounded-lg bg-purple-950/40 hover:bg-purple-900/40 border border-purple-800/40 text-purple-200 flex items-center justify-between transition-colors mt-1"
              >
                <div className="flex items-center gap-1.5">
                  <Download className="w-3.5 h-3.5 text-purple-400" />
                  <span className="font-medium text-[11px]">Download Original ZIP Archive</span>
                </div>
                <span className="text-[10px] font-mono text-purple-400">.zip</span>
              </a>

              <div className="my-1 border-t border-zinc-800" />
              <div className="px-2.5 py-1.5 text-[10px] font-semibold text-zinc-500 uppercase tracking-wider">
                Project Templates
              </div>

              {STARTER_TEMPLATES.map((tmpl) => (
                <button
                  key={tmpl.id}
                  onClick={() => {
                    onSelectTemplate(tmpl);
                    setShowProjectMenu(false);
                  }}
                  className="w-full text-left px-2.5 py-1.5 rounded-lg hover:bg-zinc-800 flex items-center justify-between text-zinc-300 hover:text-white transition-colors"
                >
                  <span className="truncate">{tmpl.name}</span>
                  <span className="text-[10px] text-zinc-500">{tmpl.category}</span>
                </button>
              ))}

              <div className="my-1 border-t border-zinc-800" />
              <button
                onClick={() => {
                  onResetProject();
                  setShowProjectMenu(false);
                }}
                className="w-full text-left px-2.5 py-1.5 rounded-lg hover:bg-red-950/40 text-red-400 flex items-center gap-1.5 transition-colors"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Reset Project to Default
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Primary Actions & Controls */}
      <div className="flex items-center gap-2 sm:gap-3">
        {/* Save Status Badge */}
        <div className="hidden md:flex items-center gap-1.5 text-[11px] text-zinc-400 px-2 py-1 rounded bg-zinc-900 border border-zinc-800/80">
          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
          <span>Synced locally</span>
        </div>

        {/* Import Files / Zip */}
        <div className="flex items-center gap-1">
          <input
            type="file"
            ref={fileInputRef}
            onChange={handleFileUpload}
            multiple
            className="hidden"
          />
          <input
            type="file"
            ref={zipInputRef}
            onChange={handleZipUpload}
            accept=".zip"
            className="hidden"
          />

          <button
            onClick={() => zipInputRef.current?.click()}
            title="Import project .zip archive"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-xs text-zinc-300 hover:text-white transition-colors"
          >
            <Upload className="w-3.5 h-3.5 text-zinc-400" />
            <span className="hidden sm:inline">Import ZIP</span>
          </button>

          <button
            onClick={handleExportZip}
            title="Export all files as a .zip"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 text-xs text-zinc-300 hover:text-white transition-colors"
          >
            <Download className="w-3.5 h-3.5 text-zinc-400" />
            <span className="hidden sm:inline">Export ZIP</span>
          </button>

          <a
            href="/api/download/nonea-ai.zip"
            download="NoneaAI-v2_3-apps.zip"
            title="Download the user-attached NoneaAI-v2_3-apps.zip archive"
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-purple-950/60 hover:bg-purple-900/60 border border-purple-800/60 text-xs text-purple-300 hover:text-purple-100 transition-colors"
          >
            <Download className="w-3.5 h-3.5 text-purple-400" />
            <span className="hidden md:inline">Download NoneaAI ZIP</span>
          </a>
        </div>

        {/* Primary Run / Build Button */}
        <button
          onClick={onBuildProject}
          disabled={isBuilding}
          className="flex items-center gap-2 px-3.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-semibold shadow-lg shadow-emerald-600/20 active:scale-95 transition-all"
        >
          {isBuilding ? (
            <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          ) : (
            <Play className="w-3.5 h-3.5 fill-current" />
          )}
          <span>{isBuilding ? 'Building...' : 'Build Project'}</span>
        </button>

        {onOpenSettings && (
          <button
            onClick={onOpenSettings}
            title="Settings & Room Memory"
            className={`p-2 rounded-lg border text-xs transition-colors ${
              activeTab === 'settings'
                ? 'bg-zinc-800 text-indigo-400 border-indigo-500/50'
                : 'bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-white border-zinc-800'
            }`}
          >
            <Settings className="w-4 h-4" />
          </button>
        )}
      </div>
    </header>
  );
};
