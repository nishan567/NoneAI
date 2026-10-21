import React, { useState } from 'react';
import {
  Smartphone,
  Play,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  FileCheck,
  PackageCheck,
  Terminal,
  BookOpen,
  Sparkles,
  Download,
  Copy,
  Check,
} from 'lucide-react';
import { ProjectFile, BuildResult } from '../types';

interface Props {
  files: ProjectFile[];
  onTriggerBuild: () => void;
  isBuilding: boolean;
  buildResult: BuildResult | null;
  onFixBuildError: (errorLog: string) => void;
}

export const AndroidBuildPanel: React.FC<Props> = ({
  files,
  onTriggerBuild,
  isBuilding,
  buildResult,
  onFixBuildError,
}) => {
  const [activeTab, setActiveTab] = useState<'build' | 'checklist' | 'instructions'>('build');
  const [copied, setCopied] = useState(false);

  // Check project structure compliance
  const filePaths = files.map((f) => f.path);
  const hasSettings = filePaths.some((p) => p.endsWith('settings.gradle.kts') || p.endsWith('settings.gradle'));
  const hasRootBuild = filePaths.some((p) => p === 'build.gradle.kts' || p === 'build.gradle' || p.endsWith('/build.gradle.kts'));
  const hasAppBuild = filePaths.some((p) => p.includes('app/build.gradle'));
  const hasManifest = filePaths.some((p) => p.endsWith('AndroidManifest.xml'));
  const hasStrings = filePaths.some((p) => p.endsWith('strings.xml'));
  const kotlinSourceCount = files.filter((f) => f.path.endsWith('.kt') || f.path.endsWith('.java')).length;

  const checklistItems = [
    {
      title: 'settings.gradle.kts',
      subtitle: 'Root build repository & plugin management',
      passed: hasSettings,
    },
    {
      title: 'build.gradle.kts',
      subtitle: 'Project-level Android & Kotlin plugins',
      passed: hasRootBuild,
    },
    {
      title: 'app/build.gradle.kts',
      subtitle: 'Android SDK, dependencies, and Compose config',
      passed: hasAppBuild,
    },
    {
      title: 'AndroidManifest.xml',
      subtitle: 'App manifest, permissions, and launch activity',
      passed: hasManifest,
    },
    {
      title: 'res/values/strings.xml',
      subtitle: 'App name and localized string resources',
      passed: hasStrings,
    },
    {
      title: `Source Files (${kotlinSourceCount} found)`,
      subtitle: 'Kotlin / Java classes and UI composables',
      passed: kotlinSourceCount > 0,
    },
  ];

  const allPassed = checklistItems.every((item) => item.passed);

  const handleCopyLogs = async () => {
    if (!buildResult) return;
    await navigator.clipboard.writeText(buildResult.logs);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  return (
    <div className="h-full flex flex-col bg-zinc-950 text-zinc-300 border-r border-zinc-800 w-80 sm:w-96 select-none shrink-0 overflow-hidden">
      {/* Header */}
      <div className="px-3.5 py-2.5 border-b border-zinc-800 flex items-center justify-between bg-zinc-950">
        <div className="flex items-center gap-2">
          <Smartphone className="w-4 h-4 text-emerald-400" />
          <span className="font-semibold text-xs text-white uppercase tracking-wider">
            Android Gradle Studio
          </span>
        </div>
        <span className="text-[10px] px-2 py-0.5 rounded-full bg-zinc-900 text-zinc-400 border border-zinc-800">
          Gradle 8.4 • AGP 8.2
        </span>
      </div>

      {/* Tabs */}
      <div className="grid grid-cols-3 gap-1 p-2 bg-zinc-900/50 border-b border-zinc-800 text-[11px]">
        <button
          onClick={() => setActiveTab('build')}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'build' ? 'bg-emerald-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <Play className="w-3 h-3" /> Build Run
        </button>
        <button
          onClick={() => setActiveTab('checklist')}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'checklist' ? 'bg-emerald-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <FileCheck className="w-3 h-3" /> Structure
        </button>
        <button
          onClick={() => setActiveTab('instructions')}
          className={`py-1.5 px-2 rounded font-medium flex items-center justify-center gap-1 transition-colors ${
            activeTab === 'instructions' ? 'bg-emerald-600 text-white shadow-sm' : 'text-zinc-400 hover:text-white hover:bg-zinc-800'
          }`}
        >
          <BookOpen className="w-3 h-3" /> Guide
        </button>
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {activeTab === 'build' && (
          <div className="space-y-3">
            {/* Build action card */}
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-3 space-y-2.5">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-zinc-200">Gradle Compiler Verification</span>
                <span className={`text-[10px] px-2 py-0.5 rounded font-mono ${
                  allPassed ? 'bg-emerald-950 text-emerald-300' : 'bg-amber-950 text-amber-300'
                }`}>
                  {allPassed ? 'Config Valid' : 'Incomplete'}
                </span>
              </div>
              <p className="text-[11px] text-zinc-400 leading-relaxed">
                Executes static analysis, Kotlin syntax compilation, resource validation, and Android manifest merger.
                Never reports success unless all steps strictly pass.
              </p>

              <button
                onClick={onTriggerBuild}
                disabled={isBuilding}
                className="w-full py-2 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white rounded-lg text-xs font-semibold shadow-lg shadow-emerald-600/20 flex items-center justify-center gap-1.5 transition-all"
              >
                {isBuilding ? (
                  <>
                    <div className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                    <span>Executing Gradle Build...</span>
                  </>
                ) : (
                  <>
                    <Play className="w-3.5 h-3.5 fill-current" />
                    <span>Run Android Build Verification</span>
                  </>
                )}
              </button>
            </div>

            {/* Build Result Status Card */}
            {buildResult && (
              <div
                className={`rounded-lg p-3 border text-xs space-y-2 ${
                  buildResult.success
                    ? 'bg-emerald-950/30 border-emerald-800/50 text-emerald-200'
                    : 'bg-red-950/30 border-red-800/50 text-red-200'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 font-semibold">
                    {buildResult.success ? (
                      <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                    ) : (
                      <XCircle className="w-4 h-4 text-red-400" />
                    )}
                    <span>{buildResult.success ? 'BUILD SUCCESSFUL' : 'BUILD FAILED'}</span>
                  </div>
                  <button
                    onClick={handleCopyLogs}
                    className="flex items-center gap-1 text-[11px] text-zinc-400 hover:text-white"
                  >
                    {copied ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                    <span>{copied ? 'Copied' : 'Copy Logs'}</span>
                  </button>
                </div>

                {buildResult.success && buildResult.apkPath && (
                  <div className="bg-emerald-950/60 p-2 rounded border border-emerald-800/60 text-[11px] font-mono text-emerald-300 flex items-center justify-between">
                    <span className="truncate">APK: {buildResult.apkPath}</span>
                    <PackageCheck className="w-4 h-4 text-emerald-400 shrink-0 ml-1" />
                  </div>
                )}

                {/* If errors, display list with AI Auto-Fix button */}
                {!buildResult.success && buildResult.errors.length > 0 && (
                  <div className="space-y-2 pt-1">
                    <div className="text-[11px] font-semibold text-red-300">Compilation Errors:</div>
                    <div className="space-y-1">
                      {buildResult.errors.map((err, idx) => (
                        <div
                          key={idx}
                          className="p-2 bg-red-950/70 border border-red-800/60 rounded text-[11px] font-mono text-red-200 break-words"
                        >
                          {err}
                        </div>
                      ))}
                    </div>

                    <button
                      onClick={() => onFixBuildError(buildResult.errors.join('\n') + '\n' + buildResult.logs)}
                      className="w-full py-1.5 bg-red-600 hover:bg-red-500 text-white rounded text-xs font-medium flex items-center justify-center gap-1.5 shadow-md shadow-red-600/20 transition-all"
                    >
                      <Sparkles className="w-3.5 h-3.5" />
                      <span>Diagnose & Fix Build with AI</span>
                    </button>
                  </div>
                )}
              </div>
            )}

            {/* Build Log Terminal View */}
            {buildResult?.logs && (
              <div className="bg-zinc-950 border border-zinc-800 rounded-lg overflow-hidden">
                <div className="px-3 py-1.5 bg-zinc-900 border-b border-zinc-800 flex items-center justify-between text-[11px] text-zinc-400">
                  <span className="font-mono flex items-center gap-1">
                    <Terminal className="w-3 h-3" /> Console Output
                  </span>
                  <span>Gradle Daemon</span>
                </div>
                <div className="p-2.5 max-h-56 overflow-auto font-mono text-[11px] text-zinc-300 whitespace-pre leading-relaxed select-text">
                  {buildResult.logs}
                </div>
              </div>
            )}
          </div>
        )}

        {/* Structure Checklist Tab */}
        {activeTab === 'checklist' && (
          <div className="space-y-2.5 text-xs">
            <p className="text-zinc-400 text-[11px] leading-relaxed">
              Required Android Gradle structure for successful compilation:
            </p>
            <div className="space-y-1.5">
              {checklistItems.map((item, idx) => (
                <div
                  key={idx}
                  className={`p-2.5 rounded-lg border flex items-center justify-between transition-colors ${
                    item.passed
                      ? 'bg-zinc-900/80 border-emerald-800/40 text-zinc-200'
                      : 'bg-zinc-900/80 border-red-800/40 text-zinc-300'
                  }`}
                >
                  <div className="min-w-0 pr-2">
                    <div className="font-mono font-medium text-[11px] truncate flex items-center gap-1.5">
                      <span>{item.title}</span>
                    </div>
                    <div className="text-[10px] text-zinc-500 truncate mt-0.5">{item.subtitle}</div>
                  </div>
                  {item.passed ? (
                    <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                  ) : (
                    <XCircle className="w-4 h-4 text-red-400 shrink-0" />
                  )}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Build Instructions Tab */}
        {activeTab === 'instructions' && (
          <div className="space-y-3 text-xs leading-relaxed text-zinc-300 select-text">
            <div className="bg-zinc-900 p-3 rounded-lg border border-zinc-800 space-y-2">
              <h4 className="font-semibold text-emerald-400 flex items-center gap-1.5">
                <BookOpen className="w-3.5 h-3.5" /> Building on Local Machine
              </h4>
              <p className="text-[11px] text-zinc-400">
                1. Make sure **JDK 17 or 21** is installed:
              </p>
              <pre className="bg-zinc-950 p-2 rounded font-mono text-[11px] text-zinc-300">java -version</pre>

              <p className="text-[11px] text-zinc-400 mt-2">
                2. Run the Gradle build task:
              </p>
              <pre className="bg-zinc-950 p-2 rounded font-mono text-[11px] text-emerald-400">
                ./gradlew assembleDebug
              </pre>

              <p className="text-[11px] text-zinc-400 mt-2">
                3. Install on connected device or emulator:
              </p>
              <pre className="bg-zinc-950 p-2 rounded font-mono text-[11px] text-blue-400">
                ./gradlew installDebug
              </pre>

              <p className="text-[11px] text-zinc-400 mt-2">
                4. Generated APK Location:
              </p>
              <pre className="bg-zinc-950 p-2 rounded font-mono text-[11px] text-amber-300 truncate">
                app/build/outputs/apk/debug/app-debug.apk
              </pre>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
