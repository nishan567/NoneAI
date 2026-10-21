export type SupportedLanguage =
  | 'kotlin'
  | 'java'
  | 'python'
  | 'javascript'
  | 'typescript'
  | 'xml'
  | 'json'
  | 'html'
  | 'css'
  | 'gradle'
  | 'bash'
  | 'sql'
  | 'markdown'
  | 'plaintext';

export interface ProjectFile {
  path: string; // e.g. "app/src/main/java/com/nonea/ai/MainActivity.kt"
  content: string;
  language: SupportedLanguage;
  isModified?: boolean;
}

export interface ProjectTemplate {
  id: string;
  name: string;
  category: 'Android' | 'Kotlin' | 'Java' | 'Python' | 'JavaScript/TypeScript' | 'Web';
  description: string;
  icon: string;
  files: ProjectFile[];
  buildInstructions: string;
  defaultRunCommand: string;
}

export interface GitCommit {
  id: string;
  shortId: string;
  message: string;
  author: string;
  timestamp: string;
  changedFiles: string[];
}

export interface GitChange {
  path: string;
  status: 'modified' | 'added' | 'deleted' | 'untracked';
  staged: boolean;
  oldContent?: string;
  newContent: string;
}

export interface BuildLogMessage {
  id: string;
  timestamp: string;
  level: 'info' | 'warn' | 'error' | 'success' | 'debug';
  text: string;
}

export interface BuildResult {
  success: boolean;
  status: 'SUCCESS' | 'FAILED' | 'ERROR';
  logs: string;
  errors: string[];
  warnings: string[];
  apkPath?: string;
}

export interface MultiFileSpec {
  path: string;
  content: string;
  language: SupportedLanguage;
  description: string;
}

export interface MultiFileResponse {
  summary: string;
  buildInstructions: string;
  files: MultiFileSpec[];
  suggestedCommands?: {
    command: string;
    purpose: string;
  }[];
}

export interface PendingCommand {
  command: string;
  cwd: string;
  description: string;
  riskLevel: 'safe' | 'medium' | 'high';
  onConfirm: () => void;
  onCancel: () => void;
}
