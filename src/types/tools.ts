export type ToolRiskLevel = 'safe' | 'medium' | 'high' | 'blocked';

export type ToolPermissionCategory =
  | 'system_info'
  | 'read_only'
  | 'app_control'
  | 'media_control'
  | 'filesystem_write'
  | 'network'
  | 'user_confirmation';

export interface ToolArgumentSchema {
  name: string;
  type: string;
  description: string;
  required: boolean;
  enum?: string[];
  default?: any;
}

export interface InternalToolDefinition {
  name: string;
  category: ToolPermissionCategory;
  description: string;
  riskLevel: ToolRiskLevel;
  requiresUserConfirmation: boolean;
  arguments: ToolArgumentSchema[];
}

export interface ProposedToolCall {
  id: string;
  toolName: string;
  arguments: Record<string, any>;
  riskLevel: ToolRiskLevel;
  requiresConfirmation: boolean;
  policyStatus: 'permitted' | 'needs_confirmation' | 'blocked';
  policyReason: string;
}

export interface ToolExecutionResult {
  toolCallId: string;
  toolName: string;
  success: boolean;
  data?: any;
  error?: string;
  executionTimeMs: number;
  timestamp: string;
  redactedLogs: string[];
}

export interface ToolAuditLogEntry {
  id: string;
  timestamp: string;
  toolName: string;
  level: 'info' | 'policy' | 'approval' | 'execution' | 'error';
  message: string;
  dataPreview?: string;
}

export interface SecurityPolicyConfig {
  requireConfirmationForWrites: boolean;
  requireConfirmationForAppLaunch: boolean;
  requireConfirmationForNetwork: boolean;
  autoApproveReadOnly: boolean;
  strictPathSandbox: boolean;
  redactSecretsInLogs: boolean;
  executionTimeoutSeconds: number;
}
