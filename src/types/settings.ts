export interface AiSettingsConfig {
  provider: 'gemini' | 'local_sandbox' | 'custom_proxy';
  model: string;
  temperature: number;
  topP: number;
  maxOutputTokens: number;
  streamingEnabled: boolean;
  thinkingLevel: 'LOW' | 'MEDIUM' | 'HIGH';
}

export interface VoiceSettingsConfig {
  language: string;
  selectedVoice: string; // e.g. "Kore (Female - Natural)", "Zephyr", "Puck"
  speechRate: number; // 0.5 to 2.0
  pitch: number; // 0.5 to 1.5
  autoSpeakResponses: boolean;
  wakeWordEnabled: boolean;
  wakeWord: string; // "Hey Nexus", "Hey Nonea", "Computer"
  wakeWordSensitivity: number; // 1 to 10
}

export interface AutomationSettingsConfig {
  accessibilityGranted: boolean;
  microphoneGranted: boolean;
  notificationGranted: boolean;
  backgroundOperationSupported: boolean;
  appLaunchPreference: 'direct' | 'confirm_first' | 'browser_internal';
  confirmFileWrites: boolean;
  confirmAppLaunches: boolean;
  confirmNetworkCalls: boolean;
  emergencyStopArmed: boolean;
}

export interface PersonalizationConfig {
  assistantName: string;
  personalityStyle: 'precise' | 'conversational' | 'architect' | 'creative';
  preferredLanguage: string;
  theme: 'dark' | 'amoled' | 'nord' | 'monokai';
  memorySpanCount: number;
  contextMemoryEnabled: boolean;
}

export type MemoryCategory = 'architecture' | 'code_snippet' | 'user_fact' | 'preference' | 'general';

export interface RoomMemoryEntity {
  id: number;
  text: string;
  category: MemoryCategory;
  timestamp: number;
  tags?: string[];
  pinned?: boolean;
}

export interface RoomMessageEntity {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  timestamp: number;
  convId: number;
  voice: boolean;
}

export interface RoomConversationEntity {
  id: number;
  title: string;
  timestamp: number;
  messageCount: number;
}
