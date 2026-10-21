import React, { useState, useEffect } from 'react';
import { Header } from './components/Header';
import { Sidebar, ActiveSidebarTab } from './components/Sidebar';
import { FileTree } from './components/FileTree';
import { CodeEditor } from './components/CodeEditor';
import { AiAssistantPanel } from './components/AiAssistantPanel';
import { MultiFileGenerator } from './components/MultiFileGenerator';
import { AndroidBuildPanel } from './components/AndroidBuildPanel';
import { GitPanel } from './components/GitPanel';
import { SandboxedTerminal } from './components/SandboxedTerminal';
import { ToolEnginePanel } from './components/ToolEnginePanel';
import { SettingsScreen } from './components/SettingsScreen';
import { BuildLogViewer } from './components/BuildLogViewer';
import { CommandConfirmModal } from './components/CommandConfirmModal';
import { DiffModal } from './components/DiffModal';
import {
  ProjectFile,
  ProjectTemplate,
  GitCommit,
  BuildLogMessage,
  BuildResult,
  MultiFileSpec,
  PendingCommand,
  SupportedLanguage,
} from './types';
import { STARTER_TEMPLATES } from './templates/starterProjects';
import { getLanguageFromPath } from './utils/syntaxHighlighter';

export default function App() {
  // Current active project
  const [currentProjectName, setCurrentProjectName] = useState<string>('Android Compose Starter');
  const [files, setFiles] = useState<ProjectFile[]>(() => {
    const saved = localStorage.getItem('nexus_code_files');
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      } catch {
        // Fallback to default
      }
    }
    return STARTER_TEMPLATES[0].files;
  });

  // Editor Tabs
  const [openTabs, setOpenTabs] = useState<string[]>(() => {
    return ['app/src/main/java/com/nexus/app/MainActivity.kt', 'settings.gradle.kts'];
  });
  const [activeFilePath, setActiveFilePath] = useState<string>(
    'app/src/main/java/com/nexus/app/MainActivity.kt'
  );

  // Active navigation tab
  const [activeSidebarTab, setActiveSidebarTab] = useState<ActiveSidebarTab>('explorer');

  // Build logs & results
  const [buildLogs, setBuildLogs] = useState<BuildLogMessage[]>([
    {
      id: 'init-log-1',
      timestamp: new Date().toLocaleTimeString(),
      level: 'info',
      text: 'NexusCode Studio Workspace initialized.',
    },
    {
      id: 'init-log-2',
      timestamp: new Date().toLocaleTimeString(),
      level: 'info',
      text: 'Gradle Build Tools API 34 ready. AI Engine connected with Gemini 3.8 Flash.',
    },
  ]);
  const [isBuilding, setIsBuilding] = useState<boolean>(false);
  const [buildResult, setBuildResult] = useState<BuildResult | null>(null);
  const [isConsoleOpen, setIsConsoleOpen] = useState<boolean>(true);

  // Git state
  const [commits, setCommits] = useState<GitCommit[]>([
    {
      id: 'init-commit-1',
      shortId: '9fa3c12',
      message: 'chore: initial project scaffolding & Gradle configuration',
      author: 'Nexus Developer <dev@nexus.internal>',
      timestamp: 'Today at 09:30 AM',
      changedFiles: ['settings.gradle.kts', 'build.gradle.kts', 'app/build.gradle.kts'],
    },
  ]);

  // Command confirmation modal state
  const [pendingCommand, setPendingCommand] = useState<PendingCommand | null>(null);

  // Diff comparison modal state
  const [diffModalState, setDiffModalState] = useState<{
    isOpen: boolean;
    filePath: string;
    originalCode: string;
    newCode: string;
    language: SupportedLanguage;
    title?: string;
  } | null>(null);

  // Auto-save to localStorage
  useEffect(() => {
    try {
      localStorage.setItem('nexus_code_files', JSON.stringify(files));
    } catch {
      // Storage quota or disabled
    }
  }, [files]);

  // Ensure active tab points to a valid file
  useEffect(() => {
    if (files.length > 0 && !files.some((f) => f.path === activeFilePath)) {
      setActiveFilePath(files[0].path);
      if (!openTabs.includes(files[0].path)) {
        setOpenTabs((prev) => [...prev, files[0].path]);
      }
    }
  }, [files, activeFilePath, openTabs]);

  const activeFile = files.find((f) => f.path === activeFilePath) || null;

  // File selection
  const handleSelectFile = (path: string) => {
    setActiveFilePath(path);
    if (!openTabs.includes(path)) {
      setOpenTabs((prev) => [...prev, path]);
    }
  };

  // Tab close
  const handleCloseTab = (path: string) => {
    const newTabs = openTabs.filter((t) => t !== path);
    setOpenTabs(newTabs);
    if (activeFilePath === path) {
      if (newTabs.length > 0) {
        setActiveFilePath(newTabs[newTabs.length - 1]);
      } else if (files.length > 0) {
        setActiveFilePath(files[0].path);
        setOpenTabs([files[0].path]);
      }
    }
  };

  // Content update
  const handleUpdateContent = (newContent: string) => {
    setFiles((prev) =>
      prev.map((f) =>
        f.path === activeFilePath ? { ...f, content: newContent, isModified: true } : f
      )
    );
  };

  // Change language
  const handleChangeLanguage = (lang: SupportedLanguage) => {
    setFiles((prev) =>
      prev.map((f) => (f.path === activeFilePath ? { ...f, language: lang } : f))
    );
  };

  // Save active file
  const handleSaveFile = () => {
    setFiles((prev) =>
      prev.map((f) => (f.path === activeFilePath ? { ...f, isModified: false } : f))
    );
    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'info',
        text: `File saved: ${activeFilePath}`,
      },
    ]);
  };

  // Create new file
  const handleCreateFile = (path: string, content = '') => {
    const normalized = path.replace(/^\//, '');
    const lang = getLanguageFromPath(normalized);
    const newFile: ProjectFile = {
      path: normalized,
      content,
      language: lang,
      isModified: true,
    };
    setFiles((prev) => [...prev.filter((f) => f.path !== normalized), newFile]);
    setActiveFilePath(normalized);
    if (!openTabs.includes(normalized)) {
      setOpenTabs((prev) => [...prev, normalized]);
    }
    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'info',
        text: `Created file: ${normalized}`,
      },
    ]);
  };

  // Delete file
  const handleDeleteFile = (path: string) => {
    setFiles((prev) => prev.filter((f) => f.path !== path));
    handleCloseTab(path);
  };

  // Rename file
  const handleRenameFile = (oldPath: string, newPath: string) => {
    const lang = getLanguageFromPath(newPath);
    setFiles((prev) =>
      prev.map((f) => (f.path === oldPath ? { ...f, path: newPath, language: lang } : f))
    );
    setOpenTabs((prev) => prev.map((t) => (t === oldPath ? newPath : t)));
    if (activeFilePath === oldPath) {
      setActiveFilePath(newPath);
    }
  };

  // Switch template
  const handleSelectTemplate = (template: ProjectTemplate) => {
    setCurrentProjectName(template.name);
    setFiles(template.files);
    const firstPath = template.files[0]?.path || '';
    setActiveFilePath(firstPath);
    setOpenTabs([firstPath]);
    setBuildResult(null);
    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'info',
        text: `Loaded template: ${template.name}`,
      },
    ]);
  };

  // Load NoneaAI attached Android project
  const handleLoadNoneaAI = async () => {
    try {
      setIsBuilding(true);
      const res = await fetch('/api/projects/sample-nonea');
      const data = await res.json();
      if (data.error) throw new Error(data.error);

      if (data.files && data.files.length > 0) {
        setCurrentProjectName(data.name || 'NoneaAI Android App (v2.3)');
        setFiles(data.files);
        // Find main activity or prominent file
        const main = data.files.find((f: any) => f.path.includes('MainActivity.kt')) || data.files[0];
        setActiveFilePath(main.path);
        setOpenTabs([main.path, 'NoneaAI/build.gradle.kts']);
        setBuildLogs((prev) => [
          ...prev,
          {
            id: Math.random().toString(),
            timestamp: new Date().toLocaleTimeString(),
            level: 'success',
            text: `Successfully loaded NoneaAI Android project (${data.files.length} real Kotlin files)!`,
          },
        ]);
      }
    } catch (err: any) {
      setBuildLogs((prev) => [
        ...prev,
        {
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          level: 'error',
          text: `Could not load NoneaAI project: ${err.message}`,
        },
      ]);
    } finally {
      setIsBuilding(false);
    }
  };

  // Import files from user
  const handleImportFiles = (importedFiles: ProjectFile[]) => {
    setFiles((prev) => {
      const paths = new Set(importedFiles.map((f) => f.path));
      const remaining = prev.filter((f) => !paths.has(f.path));
      return [...remaining, ...importedFiles];
    });
    if (importedFiles.length > 0) {
      setActiveFilePath(importedFiles[0].path);
      setOpenTabs((prev) => Array.from(new Set([...prev, importedFiles[0].path])));
    }
    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'info',
        text: `Imported ${importedFiles.length} file(s) into workspace.`,
      },
    ]);
  };

  // Reset Project to Starter Default
  const handleResetProject = () => {
    if (confirm('Reset workspace to default Android Jetpack Compose starter project?')) {
      handleSelectTemplate(STARTER_TEMPLATES[0]);
    }
  };

  // Trigger strict Android build verification
  // Rule: "Never report a build as successful unless the build process actually succeeds."
  const handleBuildProject = async () => {
    setIsBuilding(true);
    setIsConsoleOpen(true);
    const startMsg: BuildLogMessage = {
      id: Math.random().toString(),
      timestamp: new Date().toLocaleTimeString(),
      level: 'info',
      text: 'Starting Android Gradle Verification & Compiler...',
    };
    setBuildLogs((prev) => [...prev, startMsg]);

    try {
      const res = await fetch('/api/android/build', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ files }),
      });
      const data: BuildResult = await res.json();
      setBuildResult(data);

      const parsedLogLines = data.logs.split('\n').filter(Boolean);
      const newMessages: BuildLogMessage[] = parsedLogLines.map((line) => {
        let level: BuildLogMessage['level'] = 'info';
        if (line.includes('[ERROR]') || line.includes('FAILED') || line.includes('error:')) level = 'error';
        else if (line.includes('[WARN]')) level = 'warn';
        else if (line.includes('[SUCCESS]') || line.includes('SUCCESSFUL')) level = 'success';
        else if (line.includes('[DEBUG]')) level = 'debug';

        return {
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          level,
          text: line.replace(/^\[.*?\]\s*\[.*?\]\s*/, ''),
        };
      });

      setBuildLogs((prev) => [...prev, ...newMessages]);
    } catch (err: any) {
      setBuildLogs((prev) => [
        ...prev,
        {
          id: Math.random().toString(),
          timestamp: new Date().toLocaleTimeString(),
          level: 'error',
          text: `Build process failed: ${err.message}`,
        },
      ]);
    } finally {
      setIsBuilding(false);
    }
  };

  // Diagnose Error with AI
  const handleDiagnoseError = (errorText: string) => {
    setActiveSidebarTab('ai-assist');
    // We can simulate an automated error analysis
    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'info',
        text: 'Routing error stack trace to Gemini Diagnostics Engine...',
      },
    ]);
  };

  // Batch apply multi-file generation
  const handleApplyMultiFiles = (newFiles: MultiFileSpec[]) => {
    setFiles((prev) => {
      const newMap = new Map(prev.map((f) => [f.path, f]));
      for (const nf of newFiles) {
        newMap.set(nf.path, {
          path: nf.path,
          content: nf.content,
          language: nf.language,
          isModified: true,
        });
      }
      return Array.from(newMap.values());
    });

    if (newFiles.length > 0) {
      setActiveFilePath(newFiles[0].path);
      setOpenTabs((prev) => Array.from(new Set([...prev, ...newFiles.map((f) => f.path)])));
    }

    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'success',
        text: `Applied ${newFiles.length} generated files to project.`,
      },
    ]);
  };

  // Git Commit
  const handleGitCommit = (message: string) => {
    const modified = files.filter((f) => f.isModified);
    if (modified.length === 0) return;

    const shortId = Math.random().toString(16).substring(2, 9);
    const newCommit: GitCommit = {
      id: Math.random().toString(),
      shortId,
      message,
      author: 'Nexus Developer <dev@nexuscode.internal>',
      timestamp: 'Just now',
      changedFiles: modified.map((f) => f.path),
    };

    setCommits((prev) => [newCommit, ...prev]);
    // Clear modified flag
    setFiles((prev) => prev.map((f) => ({ ...f, isModified: false })));

    setBuildLogs((prev) => [
      ...prev,
      {
        id: Math.random().toString(),
        timestamp: new Date().toLocaleTimeString(),
        level: 'info',
        text: `[Git] Created commit ${shortId}: "${message}" (${modified.length} files changed)`,
      },
    ]);
  };

  // Git Discard Changes
  const handleDiscardChanges = (path: string) => {
    const templateMatch = STARTER_TEMPLATES[0].files.find((f) => f.path === path);
    if (templateMatch) {
      setFiles((prev) =>
        prev.map((f) => (f.path === path ? { ...templateMatch, isModified: false } : f))
      );
    } else {
      setFiles((prev) =>
        prev.map((f) => (f.path === path ? { ...f, isModified: false } : f))
      );
    }
  };

  // Open Diff Modal
  const handleOpenDiffModal = (original: string, updated: string, title: string) => {
    if (!activeFile) return;
    setDiffModalState({
      isOpen: true,
      filePath: activeFile.path,
      originalCode: original,
      newCode: updated,
      language: activeFile.language,
      title,
    });
  };

  return (
    <div className="flex flex-col h-screen w-screen overflow-hidden bg-zinc-950 font-sans text-zinc-100">
      {/* 1. Top Global Navigation Header */}
      <Header
        currentProjectName={currentProjectName}
        files={files}
        onSelectTemplate={handleSelectTemplate}
        onLoadNoneaAI={handleLoadNoneaAI}
        onBuildProject={handleBuildProject}
        onImportFiles={handleImportFiles}
        onResetProject={handleResetProject}
        onOpenSettings={() => setActiveSidebarTab('settings')}
        isBuilding={isBuilding}
        activeTab={activeSidebarTab}
      />

      {/* 2. Main Workspace Body */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Activity Bar */}
        <Sidebar
          activeTab={activeSidebarTab}
          onChangeTab={setActiveSidebarTab}
          gitChangesCount={files.filter((f) => f.isModified).length}
          buildStatus={
            isBuilding ? 'building' : buildResult?.success ? 'success' : buildResult ? 'failed' : 'idle'
          }
        />

        {/* Dynamic Sidebar Panel based on active selection */}
        {activeSidebarTab === 'explorer' && (
          <FileTree
            files={files}
            activeFilePath={activeFilePath}
            onSelectFile={handleSelectFile}
            onCreateFile={handleCreateFile}
            onDeleteFile={handleDeleteFile}
            onRenameFile={handleRenameFile}
          />
        )}

        {activeSidebarTab === 'search' && (
          <div className="w-64 bg-zinc-950 border-r border-zinc-800 p-3 flex flex-col gap-2 shrink-0 select-none text-xs">
            <span className="font-semibold text-zinc-400 uppercase tracking-wider text-[11px]">
              Search In Project
            </span>
            <input
              type="text"
              placeholder="Search across all files..."
              className="w-full p-2 bg-zinc-900 border border-zinc-800 rounded text-xs text-zinc-200 placeholder-zinc-500 focus:outline-none focus:border-blue-500 font-mono"
            />
            <div className="text-[11px] text-zinc-500 pt-2">
              Tip: Press <kbd className="px-1 py-0.5 bg-zinc-800 rounded border border-zinc-700">Ctrl+F</kbd> inside the editor to find & replace within the active file.
            </div>
          </div>
        )}

        {activeSidebarTab === 'ai-assist' && (
          <AiAssistantPanel
            activeFile={activeFile}
            files={files}
            onInsertCode={(code) => {
              if (activeFile) {
                handleUpdateContent(activeFile.content + '\n\n' + code);
              }
            }}
            onReplaceCode={(code) => {
              handleUpdateContent(code);
            }}
            onCreateNewFile={handleCreateFile}
            onOpenDiffModal={handleOpenDiffModal}
          />
        )}

        {activeSidebarTab === 'tool-engine' && (
          <ToolEnginePanel
            files={files}
            onFileWritten={(filePath, newContent) => {
              handleCreateFile(filePath, newContent);
            }}
          />
        )}

        {activeSidebarTab === 'multi-file' && (
          <MultiFileGenerator
            currentFiles={files}
            onApplyMultiFiles={handleApplyMultiFiles}
            onRunSuggestedCommand={(cmd, purpose) => {
              setPendingCommand({
                command: cmd,
                cwd: '/workspace/project',
                description: purpose,
                riskLevel: 'safe',
                onConfirm: () => {
                  setPendingCommand(null);
                  handleBuildProject();
                },
                onCancel: () => setPendingCommand(null),
              });
            }}
          />
        )}

        {activeSidebarTab === 'android-build' && (
          <AndroidBuildPanel
            files={files}
            onTriggerBuild={handleBuildProject}
            isBuilding={isBuilding}
            buildResult={buildResult}
            onFixBuildError={(errorLog) => {
              setActiveSidebarTab('ai-assist');
              setBuildLogs((prev) => [
                ...prev,
                {
                  id: Math.random().toString(),
                  timestamp: new Date().toLocaleTimeString(),
                  level: 'info',
                  text: 'Diagnosing build error with Gemini...',
                },
              ]);
            }}
          />
        )}

        {activeSidebarTab === 'git' && (
          <GitPanel
            files={files}
            commits={commits}
            onCommit={handleGitCommit}
            onDiscardChanges={handleDiscardChanges}
            onOpenDiff={(file) => {
              handleOpenDiffModal(
                STARTER_TEMPLATES[0].files.find((f) => f.path === file.path)?.content || '',
                file.content,
                `Git Diff: ${file.path}`
              );
            }}
          />
        )}

        {activeSidebarTab === 'terminal' && (
          <SandboxedTerminal
            onRequestCommandConfirmation={(pending) => {
              setPendingCommand({
                ...pending,
                onConfirm: () => {
                  setPendingCommand(null);
                  pending.onConfirm();
                },
                onCancel: () => {
                  setPendingCommand(null);
                  pending.onCancel();
                },
              });
            }}
          />
        )}

        {/* Center Canvas: Settings Screen OR Main Code Editor with Tabs & Build Log Viewer */}
        {activeSidebarTab === 'settings' ? (
          <SettingsScreen onBackToEditor={() => setActiveSidebarTab('explorer')} />
        ) : (
          <main className="flex-1 flex flex-col overflow-hidden">
            <CodeEditor
              activeFile={activeFile}
              openTabs={openTabs}
              onSelectTab={handleSelectFile}
              onCloseTab={handleCloseTab}
              onUpdateContent={handleUpdateContent}
              onChangeLanguage={handleChangeLanguage}
              onSaveFile={handleSaveFile}
              onTriggerAiAssist={(mode) => setActiveSidebarTab('ai-assist')}
            />

            {/* Bottom Build Log & Console Viewer */}
            <BuildLogViewer
              logs={buildLogs}
              onClearLogs={() => setBuildLogs([])}
              onDiagnoseError={handleDiagnoseError}
              isOpen={isConsoleOpen}
              onToggleOpen={() => setIsConsoleOpen(!isConsoleOpen)}
            />
          </main>
        )}
      </div>

      {/* 3. Global Sandboxed Command Confirmation Modal */}
      <CommandConfirmModal pendingCommand={pendingCommand} />

      {/* 4. Global Code Diff Review Modal */}
      {diffModalState && diffModalState.isOpen && (
        <DiffModal
          filePath={diffModalState.filePath}
          originalCode={diffModalState.originalCode}
          newCode={diffModalState.newCode}
          language={diffModalState.language}
          title={diffModalState.title}
          onApply={() => {
            handleUpdateContent(diffModalState.newCode);
            setDiffModalState(null);
          }}
          onClose={() => setDiffModalState(null)}
        />
      )}
    </div>
  );
}
