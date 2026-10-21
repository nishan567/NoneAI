import { FunctionDeclaration, Type } from '@google/genai';
import { InternalToolDefinition, ProposedToolCall, ToolExecutionResult } from '../src/types/tools';
import fs from 'fs';
import path from 'path';

// --- Secret Redactor ---
// Inspired by NoneaAI Redact.kt
export class SecretRedactor {
  private static readonly MASK = '[REDACTED]';
  private static readonly GOOGLE_KEY_REGEX = /AIza[0-9A-Za-z_\-]{20,}/g;
  private static readonly BEARER_REGEX = /bearer\s+[A-Za-z0-9._~+/=\-]{8,}/gi;
  private static readonly KV_SECRET_REGEX = /(x-goog-api-key|api[_-]?key|access[_-]?token|authorization|password|secret)(\s*[:=]\s*)("?[^"\s,&]+"?)/gi;

  static redact(text: string | null | undefined): string {
    if (!text) return '';
    let out = String(text);
    out = out.replace(this.GOOGLE_KEY_REGEX, this.MASK);
    out = out.replace(this.BEARER_REGEX, `Bearer ${this.MASK}`);
    out = out.replace(this.KV_SECRET_REGEX, (_match, p1, p2) => `${p1}${p2}"${this.MASK}"`);
    return out;
  }
}

// --- Tool Catalog Definitions for UI & Policy ---
export const TOOL_DEFINITIONS: InternalToolDefinition[] = [
  {
    name: 'open_app',
    category: 'app_control',
    description: 'Launches a supported application or sends a launch Intent to the Android environment.',
    riskLevel: 'medium',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'appName', type: 'string', description: 'Application name or identifier (e.g. YouTube, Settings, Music, Camera)', required: true },
      { name: 'packageId', type: 'string', description: 'Optional explicit Android package name (e.g. com.google.android.youtube)', required: false },
    ],
  },
  {
    name: 'search_youtube',
    category: 'app_control',
    description: 'Dispatches a search intent on YouTube or opens a curated video query.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'query', type: 'string', description: 'Search keywords or video topic to search for', required: true },
      { name: 'autoPlay', type: 'boolean', description: 'Whether to open the top result automatically', required: false, default: false },
    ],
  },
  {
    name: 'open_url',
    category: 'network',
    description: 'Opens a validated HTTP/HTTPS URL in the sandbox browser or external intent.',
    riskLevel: 'medium',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'url', type: 'string', description: 'Complete web URL (must begin with http:// or https://)', required: true },
      { name: 'browser', type: 'string', description: 'Preferred browser handler (e.g. Chrome, default)', required: false },
    ],
  },
  {
    name: 'control_supported_media',
    category: 'media_control',
    description: 'Controls the Android MediaSession playback (play, pause, next, previous, volume up/down, mute).',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      {
        name: 'action',
        type: 'string',
        description: 'Playback command',
        required: true,
        enum: ['play', 'pause', 'next', 'previous', 'volume_up', 'volume_down', 'mute'],
      },
    ],
  },
  {
    name: 'get_current_time',
    category: 'system_info',
    description: 'Returns the current local device time, date, UTC timestamp, and timezone.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'timezone', type: 'string', description: 'Optional specific IANA timezone (e.g. America/New_York, Asia/Tokyo)', required: false },
    ],
  },
  {
    name: 'create_note',
    category: 'filesystem_write',
    description: 'Creates and stores a developer or user memo/note with title, body, and tags.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'title', type: 'string', description: 'Title or subject of the note', required: true },
      { name: 'content', type: 'string', description: 'Body text or code snippet for the note', required: true },
      { name: 'tags', type: 'array', description: 'Optional list of classification tags', required: false },
    ],
  },
  {
    name: 'search_conversation_history',
    category: 'read_only',
    description: 'Searches previous chat records and assistant history for relevant context.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'query', type: 'string', description: 'Search term or topic', required: true },
      { name: 'limit', type: 'integer', description: 'Maximum number of past messages to return', required: false, default: 5 },
    ],
  },
  {
    name: 'generate_code',
    category: 'read_only',
    description: 'Invokes code generation subroutines for specific language modules.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'prompt', type: 'string', description: 'Detailed instruction describing the code to generate', required: true },
      { name: 'language', type: 'string', description: 'Target programming language (e.g. kotlin, java, python, typescript)', required: true },
      { name: 'targetFile', type: 'string', description: 'Optional file destination path', required: false },
    ],
  },
  {
    name: 'read_project_files',
    category: 'read_only',
    description: 'Reads contents of specified files within the current workspace project.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'paths', type: 'array', description: 'List of file paths relative to project root', required: true },
      { name: 'filter', type: 'string', description: 'Optional regex pattern to filter lines', required: false },
    ],
  },
  {
    name: 'write_project_file',
    category: 'filesystem_write',
    description: 'Writes or updates a source file in the project. Requires confirmation when modifying existing code.',
    riskLevel: 'high',
    requiresUserConfirmation: true,
    arguments: [
      { name: 'path', type: 'string', description: 'Target relative file path (e.g. app/src/main/java/com/nonea/ai/MainActivity.kt)', required: true },
      { name: 'content', type: 'string', description: 'Exact full text content to write', required: true },
      { name: 'overwrite', type: 'boolean', description: 'Allow replacing existing file contents', required: false, default: true },
    ],
  },
  {
    name: 'inspect_build_log',
    category: 'read_only',
    description: 'Inspects and filters recent compiler logs and Gradle build diagnostics.',
    riskLevel: 'safe',
    requiresUserConfirmation: false,
    arguments: [
      { name: 'severity', type: 'string', description: 'Severity level filter', required: false, enum: ['error', 'warn', 'all'], default: 'all' },
      { name: 'maxLines', type: 'integer', description: 'Number of recent lines to retrieve', required: false, default: 50 },
    ],
  },
  {
    name: 'request_user_confirmation',
    category: 'user_confirmation',
    description: 'Explicitly pauses AI execution to request user confirmation for risky actions, shell execution, or sensitive changes.',
    riskLevel: 'high',
    requiresUserConfirmation: true,
    arguments: [
      { name: 'actionDescription', type: 'string', description: 'Clear plain-language explanation of what action is being requested', required: true },
      { name: 'riskLevel', type: 'string', description: 'Assessed risk rating', required: true, enum: ['safe', 'medium', 'high'] },
      { name: 'proposedCommandOrChange', type: 'string', description: 'The exact payload, command, or file diff to be approved', required: true },
    ],
  },
];

// --- Gemini @google/genai Function Declarations ---
export const GEMINI_FUNCTION_DECLARATIONS: FunctionDeclaration[] = [
  {
    name: 'open_app',
    description: 'Opens an installed application or dispatches a launch intent.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        appName: { type: Type.STRING, description: 'Display name or package name of the app (e.g. YouTube, Camera, Settings, Chrome)' },
        packageId: { type: Type.STRING, description: 'Optional exact Android package identifier' },
      },
      required: ['appName'],
    },
  },
  {
    name: 'search_youtube',
    description: 'Searches YouTube for videos or music.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        query: { type: Type.STRING, description: 'Search term or video query' },
        autoPlay: { type: Type.BOOLEAN, description: 'Whether to open first match directly' },
      },
      required: ['query'],
    },
  },
  {
    name: 'open_url',
    description: 'Opens a web URL in browser.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        url: { type: Type.STRING, description: 'Full URL starting with http:// or https://' },
        browser: { type: Type.STRING, description: 'Optional browser choice' },
      },
      required: ['url'],
    },
  },
  {
    name: 'control_supported_media',
    description: 'Controls music or media playback.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        action: {
          type: Type.STRING,
          description: 'Playback command: play, pause, next, previous, volume_up, volume_down, mute',
        },
      },
      required: ['action'],
    },
  },
  {
    name: 'get_current_time',
    description: 'Gets current time and date with timezone support.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        timezone: { type: Type.STRING, description: 'Optional timezone like UTC, America/New_York, Asia/Tokyo' },
      },
    },
  },
  {
    name: 'create_note',
    description: 'Saves a note, memo, or todo in the workspace.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        title: { type: Type.STRING, description: 'Note title' },
        content: { type: Type.STRING, description: 'Note text' },
        tags: {
          type: Type.ARRAY,
          items: { type: Type.STRING },
          description: 'Optional tags',
        },
      },
      required: ['title', 'content'],
    },
  },
  {
    name: 'search_conversation_history',
    description: 'Searches past conversation interactions.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        query: { type: Type.STRING, description: 'Search query' },
        limit: { type: Type.INTEGER, description: 'Max items to return' },
      },
      required: ['query'],
    },
  },
  {
    name: 'generate_code',
    description: 'Generates specialized code for the project.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        prompt: { type: Type.STRING, description: 'Generation requirements' },
        language: { type: Type.STRING, description: 'Language: kotlin, java, python, etc.' },
        targetFile: { type: Type.STRING, description: 'Destination path' },
      },
      required: ['prompt', 'language'],
    },
  },
  {
    name: 'read_project_files',
    description: 'Reads contents of specified project files.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        paths: {
          type: Type.ARRAY,
          items: { type: Type.STRING },
          description: 'Relative paths of files to read',
        },
      },
      required: ['paths'],
    },
  },
  {
    name: 'write_project_file',
    description: 'Creates or modifies a project source file. Requires confirmation for existing files.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        path: { type: Type.STRING, description: 'Relative path of file' },
        content: { type: Type.STRING, description: 'Pristine file contents' },
        overwrite: { type: Type.BOOLEAN, description: 'Overwrite if exists' },
      },
      required: ['path', 'content'],
    },
  },
  {
    name: 'inspect_build_log',
    description: 'Reads compiler logs and diagnostics from recent build.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        severity: { type: Type.STRING, description: 'Filter: error, warn, all' },
        maxLines: { type: Type.INTEGER, description: 'Max lines to inspect' },
      },
    },
  },
  {
    name: 'request_user_confirmation',
    description: 'Requests user approval before executing high-risk or external operations.',
    parameters: {
      type: Type.OBJECT,
      properties: {
        actionDescription: { type: Type.STRING, description: 'User-facing explanation' },
        riskLevel: { type: Type.STRING, description: 'safe, medium, high' },
        proposedCommandOrChange: { type: Type.STRING, description: 'The exact payload to be confirmed' },
      },
      required: ['actionDescription', 'riskLevel', 'proposedCommandOrChange'],
    },
  },
];

// --- Local Policy & Permission Evaluator ---
export class ActionPolicyEngine {
  // Sensitive/blocked Android packages from NoneaAI AutomationPolicy.kt
  private static readonly BLOCKED_PACKAGES = new Set([
    'com.android.settings',
    'com.android.permissioncontroller',
    'com.google.android.permissioncontroller',
    'com.android.packageinstaller',
    'com.android.systemui',
    'com.google.android.apps.authenticator2',
    'com.bitwarden.mobile',
    'com.paypal.android.p2pmobile',
    'com.coinbase.android',
  ]);

  static evaluatePolicy(
    toolName: string,
    args: Record<string, any>,
    userConfirmed = false
  ): { status: 'permitted' | 'needs_confirmation' | 'blocked'; reason: string; riskLevel: import('../src/types/tools').ToolRiskLevel } {
    const def = TOOL_DEFINITIONS.find((t) => t.name === toolName);
    if (!def) {
      return {
        status: 'blocked',
        reason: `Unknown tool '${toolName}'. Unregistered actions are not permitted.`,
        riskLevel: 'high',
      };
    }

    // 1. Strict security check for open_app
    if (toolName === 'open_app') {
      const pkg = (args.packageId || '').toLowerCase();
      if (this.BLOCKED_PACKAGES.has(pkg)) {
        return {
          status: 'blocked',
          reason: `Access to system security/financial package '${pkg}' is strictly prohibited by policy.`,
          riskLevel: 'high',
        };
      }
      return {
        status: 'permitted',
        reason: 'App launch request validated against package blacklist.',
        riskLevel: 'medium',
      };
    }

    // 2. Open URL check
    if (toolName === 'open_url') {
      const url = String(args.url || '').trim();
      if (!url.startsWith('http://') && !url.startsWith('https://')) {
        return {
          status: 'blocked',
          reason: 'Only standard HTTP and HTTPS protocols are permitted.',
          riskLevel: 'high',
        };
      }
      return {
        status: 'permitted',
        reason: 'URL conforms to safe network protocol requirements.',
        riskLevel: 'medium',
      };
    }

    // 3. File System Write Policy
    if (toolName === 'write_project_file') {
      const targetPath = String(args.path || '').trim();
      // Path traversal check
      if (targetPath.includes('..') || path.isAbsolute(targetPath)) {
        return {
          status: 'blocked',
          reason: 'Path traversal or absolute paths outside the project sandbox are blocked.',
          riskLevel: 'high',
        };
      }
      if (!userConfirmed) {
        return {
          status: 'needs_confirmation',
          reason: `File modification on '${targetPath}' requires user review and confirmation.`,
          riskLevel: 'high',
        };
      }
      return {
        status: 'permitted',
        reason: 'User explicitly confirmed file write action.',
        riskLevel: 'high',
      };
    }

    // 4. Request user confirmation tool
    if (toolName === 'request_user_confirmation') {
      if (!userConfirmed) {
        return {
          status: 'needs_confirmation',
          reason: args.actionDescription || 'User confirmation requested by AI.',
          riskLevel: (args.riskLevel as any) || 'high',
        };
      }
      return {
        status: 'permitted',
        reason: 'User approved requested action.',
        riskLevel: 'safe',
      };
    }

    // Default safe tools
    return {
      status: 'permitted',
      reason: 'Read-only or safe local query approved automatically.',
      riskLevel: def.riskLevel,
    };
  }
}

// --- In-Memory Notes Store ---
interface StoredNote {
  id: string;
  title: string;
  content: string;
  tags: string[];
  createdAt: string;
}
const NOTES_STORE: StoredNote[] = [
  {
    id: 'note-1',
    title: 'Jetpack Compose Architecture Goals',
    content: 'Migrate to M3 themes, use StateFlow for UI state, keep composables stateless.',
    tags: ['android', 'compose', 'architecture'],
    createdAt: new Date().toISOString(),
  },
];

// --- Local Tool Execution Handler ---
export async function executeLocalTool(
  toolName: string,
  args: Record<string, any>,
  userConfirmed = false,
  workspaceFiles: { path: string; content: string }[] = []
): Promise<ToolExecutionResult> {
  const startTime = Date.now();
  const logs: string[] = [];
  const log = (msg: string) => logs.push(`[${new Date().toLocaleTimeString()}] ${SecretRedactor.redact(msg)}`);

  log(`Received tool invocation for '${toolName}' with arguments: ${JSON.stringify(args)}`);

  // Policy verification
  const policyCheck = ActionPolicyEngine.evaluatePolicy(toolName, args, userConfirmed);
  log(`Policy evaluation: status=${policyCheck.status}, reason="${policyCheck.reason}"`);

  if (policyCheck.status === 'blocked') {
    return {
      toolCallId: Math.random().toString(),
      toolName,
      success: false,
      error: `Policy Violation: ${policyCheck.reason}`,
      executionTimeMs: Date.now() - startTime,
      timestamp: new Date().toLocaleTimeString(),
      redactedLogs: logs,
    };
  }

  if (policyCheck.status === 'needs_confirmation' && !userConfirmed) {
    return {
      toolCallId: Math.random().toString(),
      toolName,
      success: false,
      error: `Action Pending: User confirmation required (${policyCheck.reason})`,
      executionTimeMs: Date.now() - startTime,
      timestamp: new Date().toLocaleTimeString(),
      redactedLogs: logs,
    };
  }

  // Execution dispatch with 10s timeout
  try {
    let resultData: any = null;

    switch (toolName) {
      case 'get_current_time': {
        const tz = args.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone;
        const now = new Date();
        resultData = {
          iso: now.toISOString(),
          formatted: now.toLocaleString('en-US', { timeZone: tz }),
          timezone: tz,
          unix: Math.floor(now.getTime() / 1000),
        };
        log(`Time resolved for timezone: ${tz}`);
        break;
      }

      case 'open_app': {
        const appName = args.appName;
        const pkg = args.packageId || `com.app.${appName.toLowerCase().replace(/[^a-z0-9]/g, '')}`;
        resultData = {
          action: 'android.intent.action.MAIN',
          launchedApp: appName,
          packageId: pkg,
          status: 'LAUNCHED_IN_SANDBOX',
          taskAffinity: `task://${pkg}`,
        };
        log(`Simulated Android intent launch for application: ${appName} (${pkg})`);
        break;
      }

      case 'search_youtube': {
        const query = args.query;
        const cleanQuery = encodeURIComponent(query);
        resultData = {
          query,
          webUrl: `https://www.youtube.com/results?search_query=${cleanQuery}`,
          intentUri: `vnd.youtube://results?search_query=${cleanQuery}`,
          autoPlayRequested: !!args.autoPlay,
          status: 'INTENT_READY',
        };
        log(`YouTube search query prepared: ${query}`);
        break;
      }

      case 'open_url': {
        const url = args.url;
        resultData = {
          url,
          target: '_blank',
          status: 'URL_DISPATCHED',
          scheme: new URL(url).protocol,
        };
        log(`URL intent created for: ${url}`);
        break;
      }

      case 'control_supported_media': {
        const action = args.action;
        resultData = {
          mediaCommand: action,
          mediaSessionActive: true,
          status: 'COMMAND_APPLIED',
          currentVolume: action === 'volume_up' ? 80 : action === 'volume_down' ? 60 : 70,
        };
        log(`MediaSession control issued: ${action}`);
        break;
      }

      case 'create_note': {
        const newNote: StoredNote = {
          id: `note-${Date.now()}`,
          title: args.title,
          content: args.content,
          tags: Array.isArray(args.tags) ? args.tags : ['workspace'],
          createdAt: new Date().toISOString(),
        };
        NOTES_STORE.push(newNote);
        resultData = {
          noteId: newNote.id,
          title: newNote.title,
          tags: newNote.tags,
          totalNotesCount: NOTES_STORE.length,
          status: 'SAVED',
        };
        log(`Note created successfully: "${newNote.title}" with id ${newNote.id}`);
        break;
      }

      case 'search_conversation_history': {
        const query = (args.query || '').toLowerCase();
        const limit = args.limit || 5;
        // Mock conversation history search
        const mockHistory = [
          { role: 'user', text: 'How do I configure Gradle KTS for Jetpack Compose?' },
          { role: 'assistant', text: 'You need kotlinCompilerExtensionVersion in composeOptions.' },
          { role: 'user', text: 'Show me how to create a ViewModel with StateFlow.' },
        ];
        const matches = mockHistory.filter((m) => m.text.toLowerCase().includes(query)).slice(0, limit);
        resultData = {
          query,
          matchesCount: matches.length,
          results: matches.length > 0 ? matches : mockHistory.slice(0, limit),
        };
        log(`History search completed: returned ${resultData.results.length} items`);
        break;
      }

      case 'generate_code': {
        resultData = {
          language: args.language,
          targetFile: args.targetFile || 'GeneratedSnippet.kt',
          status: 'CODE_GENERATION_QUEUED',
          prompt: args.prompt,
        };
        log(`Code generation task dispatched for ${args.language}`);
        break;
      }

      case 'read_project_files': {
        const requestedPaths: string[] = args.paths || [];
        const foundFiles: { path: string; linesCount: number; preview: string }[] = [];

        for (const reqPath of requestedPaths) {
          const match = workspaceFiles.find((f) => f.path === reqPath || f.path.endsWith(reqPath));
          if (match) {
            foundFiles.push({
              path: match.path,
              linesCount: match.content.split('\n').length,
              preview: match.content.slice(0, 300) + '...',
            });
          }
        }
        resultData = {
          requestedCount: requestedPaths.length,
          foundCount: foundFiles.length,
          files: foundFiles,
        };
        log(`Read project files: resolved ${foundFiles.length} of ${requestedPaths.length}`);
        break;
      }

      case 'write_project_file': {
        resultData = {
          path: args.path,
          bytesWritten: (args.content || '').length,
          overwrite: !!args.overwrite,
          status: 'FILE_WRITE_APPROVED',
        };
        log(`File write executed in sandbox for: ${args.path} (${resultData.bytesWritten} bytes)`);
        break;
      }

      case 'inspect_build_log': {
        resultData = {
          severityFilter: args.severity || 'all',
          daemonStatus: 'ACTIVE',
          recentBuildResult: 'SUCCESS',
          lines: [
            '> Task :app:compileDebugKotlin UP-TO-DATE',
            '> Task :app:packageDebug UP-TO-DATE',
            'BUILD SUCCESSFUL in 1.8s',
          ],
        };
        log(`Build log inspected with filter: ${args.severity || 'all'}`);
        break;
      }

      case 'request_user_confirmation': {
        resultData = {
          confirmed: true,
          actionDescription: args.actionDescription,
          riskLevel: args.riskLevel,
        };
        log(`User confirmation recorded for action: ${args.actionDescription}`);
        break;
      }

      default:
        throw new Error(`Unhandled tool: ${toolName}`);
    }

    log(`Tool '${toolName}' executed successfully in ${Date.now() - startTime}ms.`);

    return {
      toolCallId: Math.random().toString(),
      toolName,
      success: true,
      data: resultData,
      executionTimeMs: Date.now() - startTime,
      timestamp: new Date().toLocaleTimeString(),
      redactedLogs: logs,
    };
  } catch (err: any) {
    log(`Execution error in tool '${toolName}': ${err.message}`);
    return {
      toolCallId: Math.random().toString(),
      toolName,
      success: false,
      error: err.message,
      executionTimeMs: Date.now() - startTime,
      timestamp: new Date().toLocaleTimeString(),
      redactedLogs: logs,
    };
  }
}
