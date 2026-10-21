import React, { useState } from 'react';
import {
  Folder,
  FolderOpen,
  FileCode,
  FileText,
  Plus,
  Trash2,
  Edit2,
  Search,
  FilePlus,
  FolderPlus,
  ChevronRight,
  ChevronDown,
} from 'lucide-react';
import { ProjectFile, SupportedLanguage } from '../types';
import { buildFileTree, FileTreeNode, getLanguageFromPath } from '../utils/syntaxHighlighter';

interface Props {
  files: ProjectFile[];
  activeFilePath: string;
  onSelectFile: (path: string) => void;
  onCreateFile: (path: string, content?: string) => void;
  onDeleteFile: (path: string) => void;
  onRenameFile: (oldPath: string, newPath: string) => void;
}

export const FileTree: React.FC<Props> = ({
  files,
  activeFilePath,
  onSelectFile,
  onCreateFile,
  onDeleteFile,
  onRenameFile,
}) => {
  const [expandedFolders, setExpandedFolders] = useState<Record<string, boolean>>({
    '': true,
    'app': true,
    'app/src': true,
    'app/src/main': true,
    'app/src/main/java': true,
    'NoneaAI': true,
    'NoneaAI/app': true,
    'NoneaAI/app/src': true,
    'NoneaAI/app/src/main': true,
    'NoneaAI/app/src/main/java': true,
    'NoneaAI/app/src/main/java/com': true,
    'NoneaAI/app/src/main/java/com/nonea': true,
    'NoneaAI/app/src/main/java/com/nonea/ai': true,
  });

  const [searchQuery, setSearchQuery] = useState('');
  const [isCreatingFile, setIsCreatingFile] = useState(false);
  const [newFileName, setNewFileName] = useState('');
  const [renameTarget, setRenameTarget] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');

  const tree = React.useMemo(() => buildFileTree(files), [files]);

  const toggleFolder = (folderPath: string) => {
    setExpandedFolders((prev) => ({
      ...prev,
      [folderPath]: !prev[folderPath],
    }));
  };

  const handleCreateFileSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newFileName.trim()) return;
    onCreateFile(newFileName.trim(), '');
    setNewFileName('');
    setIsCreatingFile(false);
  };

  const handleRenameSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!renameTarget || !renameValue.trim()) return;
    onRenameFile(renameTarget, renameValue.trim());
    setRenameTarget(null);
    setRenameValue('');
  };

  // Render individual file icon based on extension
  const renderFileIcon = (path: string, language?: SupportedLanguage) => {
    const ext = path.split('.').pop()?.toLowerCase();

    if (ext === 'kt' || ext === 'kts') {
      return <span className="text-purple-400 font-bold text-[10px] w-4 text-center">KT</span>;
    }
    if (ext === 'java') {
      return <span className="text-amber-500 font-bold text-[10px] w-4 text-center">JA</span>;
    }
    if (ext === 'py') {
      return <span className="text-blue-400 font-bold text-[10px] w-4 text-center">PY</span>;
    }
    if (ext === 'xml') {
      return <span className="text-red-400 font-bold text-[10px] w-4 text-center">XML</span>;
    }
    if (ext === 'json') {
      return <span className="text-yellow-400 font-bold text-[10px] w-4 text-center">&#123;&#125;</span>;
    }
    if (ext === 'gradle') {
      return <span className="text-emerald-400 font-bold text-[10px] w-4 text-center">GD</span>;
    }
    if (ext === 'ts' || ext === 'tsx') {
      return <span className="text-sky-400 font-bold text-[10px] w-4 text-center">TS</span>;
    }
    if (ext === 'js' || ext === 'jsx') {
      return <span className="text-yellow-400 font-bold text-[10px] w-4 text-center">JS</span>;
    }
    if (ext === 'html') {
      return <span className="text-orange-400 font-bold text-[10px] w-4 text-center">&lt;&gt;</span>;
    }
    if (ext === 'css') {
      return <span className="text-pink-400 font-bold text-[10px] w-4 text-center">#</span>;
    }
    if (ext === 'md') {
      return <span className="text-teal-400 font-bold text-[10px] w-4 text-center">MD</span>;
    }
    return <FileCode className="w-3.5 h-3.5 text-zinc-400" />;
  };

  // Recursive tree renderer
  const renderNode = (node: FileTreeNode, depth = 0): React.ReactNode => {
    if (node.name === 'root') {
      return node.children?.map((child) => renderNode(child, depth));
    }

    const isFolder = node.isDirectory;
    const isExpanded = !!expandedFolders[node.path];
    const isSelected = activeFilePath === node.path;
    const isModified = files.find((f) => f.path === node.path)?.isModified;

    // Filter check
    if (searchQuery) {
      if (!isFolder && !node.name.toLowerCase().includes(searchQuery.toLowerCase())) {
        return null;
      }
    }

    return (
      <div key={node.path} className="select-none text-xs">
        {isFolder ? (
          <div>
            <button
              onClick={() => toggleFolder(node.path)}
              style={{ paddingLeft: `${depth * 12 + 6}px` }}
              className="w-full flex items-center gap-1.5 py-1 text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900/60 transition-colors text-left group"
            >
              {isExpanded ? (
                <ChevronDown className="w-3.5 h-3.5 text-zinc-500" />
              ) : (
                <ChevronRight className="w-3.5 h-3.5 text-zinc-500" />
              )}
              {isExpanded ? (
                <FolderOpen className="w-4 h-4 text-amber-400/90" />
              ) : (
                <Folder className="w-4 h-4 text-amber-400/70" />
              )}
              <span className="truncate font-medium">{node.name}</span>
            </button>

            {isExpanded && node.children && (
              <div>
                {node.children.map((child) => renderNode(child, depth + 1))}
              </div>
            )}
          </div>
        ) : (
          <div
            style={{ paddingLeft: `${depth * 12 + 18}px` }}
            className={`group relative flex items-center justify-between py-1 px-2 cursor-pointer transition-colors ${
              isSelected
                ? 'bg-blue-600/15 text-blue-300 font-medium border-l-2 border-blue-500'
                : 'text-zinc-300 hover:text-white hover:bg-zinc-900/60'
            }`}
            onClick={() => onSelectFile(node.path)}
          >
            <div className="flex items-center gap-1.5 truncate flex-1 min-w-0">
              {renderFileIcon(node.path, node.language)}
              <span className="truncate">{node.name}</span>
              {isModified && (
                <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" title="Unsaved changes" />
              )}
            </div>

            {/* Quick action buttons on hover */}
            <div className="opacity-0 group-hover:opacity-100 flex items-center gap-1 shrink-0 ml-1">
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setRenameTarget(node.path);
                  setRenameValue(node.path);
                }}
                title="Rename file"
                className="p-1 hover:text-blue-400 hover:bg-zinc-800 rounded"
              >
                <Edit2 className="w-3 h-3 text-zinc-400" />
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  if (confirm(`Delete ${node.name}?`)) {
                    onDeleteFile(node.path);
                  }
                }}
                title="Delete file"
                className="p-1 hover:text-red-400 hover:bg-zinc-800 rounded"
              >
                <Trash2 className="w-3 h-3 text-zinc-400" />
              </button>
            </div>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-64 select-none shrink-0">
      {/* Title & Actions */}
      <div className="px-3 py-2.5 border-b border-zinc-800/80 flex items-center justify-between">
        <span className="text-[11px] font-bold text-zinc-400 tracking-wider uppercase">
          Explorer ({files.length} files)
        </span>
        <div className="flex items-center gap-1">
          <button
            onClick={() => setIsCreatingFile(true)}
            title="New File"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            <FilePlus className="w-3.5 h-3.5" />
          </button>
          <button
            onClick={() => {
              const name = window.prompt('Enter folder path (e.g. app/src/test):');
              if (name) {
                onCreateFile(`${name}/.gitkeep`, '');
              }
            }}
            title="New Folder"
            className="p-1 rounded hover:bg-zinc-800 text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            <FolderPlus className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Filter / Search input */}
      <div className="p-2 border-b border-zinc-800/60">
        <div className="relative">
          <Search className="w-3.5 h-3.5 text-zinc-500 absolute left-2 top-2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Filter files..."
            className="w-full pl-7 pr-2 py-1 bg-zinc-900 border border-zinc-800 rounded text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-blue-500"
          />
        </div>
      </div>

      {/* New file input form */}
      {isCreatingFile && (
        <form onSubmit={handleCreateFileSubmit} className="p-2 bg-zinc-900 border-b border-zinc-800">
          <div className="text-[10px] text-zinc-400 mb-1">New File Path (e.g. app/ui/CustomScreen.kt):</div>
          <div className="flex gap-1">
            <input
              type="text"
              autoFocus
              value={newFileName}
              onChange={(e) => setNewFileName(e.target.value)}
              placeholder="filename.ext"
              className="flex-1 px-2 py-1 bg-zinc-950 border border-zinc-700 rounded text-xs text-zinc-100 focus:outline-none focus:border-blue-500 font-mono"
            />
            <button
              type="submit"
              className="px-2 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded text-xs font-medium"
            >
              Add
            </button>
            <button
              type="button"
              onClick={() => setIsCreatingFile(false)}
              className="px-1.5 py-1 text-zinc-400 hover:text-zinc-200 text-xs"
            >
              ✕
            </button>
          </div>
        </form>
      )}

      {/* Rename dialog */}
      {renameTarget && (
        <form onSubmit={handleRenameSubmit} className="p-2 bg-zinc-900 border-b border-zinc-800">
          <div className="text-[10px] text-zinc-400 mb-1">Rename Path:</div>
          <div className="flex gap-1">
            <input
              type="text"
              autoFocus
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              className="flex-1 px-2 py-1 bg-zinc-950 border border-zinc-700 rounded text-xs text-zinc-100 focus:outline-none focus:border-blue-500 font-mono"
            />
            <button
              type="submit"
              className="px-2 py-1 bg-emerald-600 hover:bg-emerald-500 text-white rounded text-xs font-medium"
            >
              Save
            </button>
            <button
              type="button"
              onClick={() => setRenameTarget(null)}
              className="px-1.5 py-1 text-zinc-400 hover:text-zinc-200 text-xs"
            >
              ✕
            </button>
          </div>
        </form>
      )}

      {/* File Tree List */}
      <div className="flex-1 overflow-y-auto py-1">
        {renderNode(tree)}
      </div>
    </div>
  );
};
