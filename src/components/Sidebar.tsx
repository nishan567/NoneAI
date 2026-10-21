import React from 'react';
import {
  Files,
  Search,
  Sparkles,
  Layers,
  Smartphone,
  GitBranch,
  Terminal,
  HelpCircle,
  Settings,
} from 'lucide-react';

export type ActiveSidebarTab =
  | 'explorer'
  | 'search'
  | 'ai-assist'
  | 'tool-engine'
  | 'multi-file'
  | 'android-build'
  | 'git'
  | 'terminal'
  | 'settings';

interface Props {
  activeTab: ActiveSidebarTab;
  onChangeTab: (tab: ActiveSidebarTab) => void;
  gitChangesCount?: number;
  buildStatus?: 'idle' | 'success' | 'failed' | 'building';
}

export const Sidebar: React.FC<Props> = ({
  activeTab,
  onChangeTab,
  gitChangesCount = 0,
  buildStatus = 'idle',
}) => {
  const navItems = [
    {
      id: 'explorer' as ActiveSidebarTab,
      label: 'Explorer',
      icon: Files,
    },
    {
      id: 'search' as ActiveSidebarTab,
      label: 'Search Files',
      icon: Search,
    },
    {
      id: 'tool-engine' as ActiveSidebarTab,
      label: 'Tool-Calling & Action Engine',
      icon: Sparkles,
      badge: 'Tools',
      badgeColor: 'bg-cyan-950 text-cyan-400 border border-cyan-800/40',
    },
    {
      id: 'ai-assist' as ActiveSidebarTab,
      label: 'AI Coding Assistant',
      icon: Sparkles,
      badge: 'Gemini',
      badgeColor: 'bg-indigo-950 text-indigo-400 border border-indigo-800/40',
    },
    {
      id: 'multi-file' as ActiveSidebarTab,
      label: 'Multi-File Generator',
      icon: Layers,
    },
    {
      id: 'android-build' as ActiveSidebarTab,
      label: 'Android Builder & Logs',
      icon: Smartphone,
      statusDot: buildStatus,
    },
    {
      id: 'git' as ActiveSidebarTab,
      label: 'Source Control (Git)',
      icon: GitBranch,
      countBadge: gitChangesCount > 0 ? gitChangesCount : undefined,
    },
    {
      id: 'terminal' as ActiveSidebarTab,
      label: 'Sandboxed Terminal',
      icon: Terminal,
    },
  ];

  return (
    <aside className="w-12 bg-zinc-950 border-r border-zinc-800 flex flex-col items-center py-2.5 justify-between select-none z-10 shrink-0">
      <div className="flex flex-col items-center gap-1.5 w-full">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;

          return (
            <button
              key={item.id}
              onClick={() => onChangeTab(item.id)}
              title={item.label}
              className={`relative w-10 h-10 rounded-lg flex items-center justify-center transition-all ${
                isActive
                  ? 'bg-zinc-800 text-white shadow-sm'
                  : 'text-zinc-400 hover:text-zinc-200 hover:bg-zinc-900'
              }`}
            >
              {/* Active left indicator strip */}
              {isActive && (
                <div className="absolute left-0 top-2 bottom-2 w-0.5 bg-blue-500 rounded-r" />
              )}
              <Icon className="w-5 h-5" />

              {/* Changes count badge */}
              {item.countBadge !== undefined && (
                <span className="absolute top-1.5 right-1.5 px-1 py-0.2 bg-blue-600 text-[9px] font-bold text-white rounded-full min-w-3.5 text-center leading-tight">
                  {item.countBadge}
                </span>
              )}

              {/* Status dot for Android build */}
              {item.statusDot && item.statusDot !== 'idle' && (
                <span
                  className={`absolute top-1.5 right-1.5 w-2 h-2 rounded-full ${
                    item.statusDot === 'building'
                      ? 'bg-amber-400 animate-ping'
                      : item.statusDot === 'success'
                      ? 'bg-emerald-400'
                      : 'bg-red-400'
                  }`}
                />
              )}
            </button>
          );
        })}
      </div>

      <div className="flex flex-col items-center gap-1.5">
        <button
          onClick={() => onChangeTab('settings')}
          title="Settings, Memory & Privacy"
          className={`w-10 h-10 rounded-lg flex items-center justify-center transition-colors ${
            activeTab === 'settings'
              ? 'bg-zinc-800 text-indigo-400'
              : 'text-zinc-500 hover:text-zinc-300 hover:bg-zinc-900'
          }`}
        >
          <Settings className="w-4 h-4" />
        </button>
        <button
          onClick={() => onChangeTab('android-build')}
          title="Build Documentation & Info"
          className="w-10 h-10 rounded-lg flex items-center justify-center text-zinc-500 hover:text-zinc-300 hover:bg-zinc-900 transition-colors"
        >
          <HelpCircle className="w-4 h-4" />
        </button>
      </div>
    </aside>
  );
};
