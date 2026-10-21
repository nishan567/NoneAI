import { SupportedLanguage } from '../types';

export function getLanguageFromPath(filePath: string): SupportedLanguage {
  const lower = filePath.toLowerCase();
  if (lower.endsWith('.kt') || lower.endsWith('.kts')) return 'kotlin';
  if (lower.endsWith('.java')) return 'java';
  if (lower.endsWith('.py')) return 'python';
  if (lower.endsWith('.ts') || lower.endsWith('.tsx')) return 'typescript';
  if (lower.endsWith('.js') || lower.endsWith('.jsx') || lower.endsWith('.mjs')) return 'javascript';
  if (lower.endsWith('.xml') || lower.endsWith('.plist')) return 'xml';
  if (lower.endsWith('.json')) return 'json';
  if (lower.endsWith('.html') || lower.endsWith('.htm')) return 'html';
  if (lower.endsWith('.css') || lower.endsWith('.scss')) return 'css';
  if (lower.endsWith('.gradle') || lower.endsWith('.gradle.kts')) return 'gradle';
  if (lower.endsWith('.sh') || lower.endsWith('.bash')) return 'bash';
  if (lower.endsWith('.sql')) return 'sql';
  if (lower.endsWith('.md') || lower.endsWith('.markdown')) return 'markdown';
  return 'plaintext';
}

export function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

const KEYWORDS: Record<SupportedLanguage, string[]> = {
  kotlin: [
    'package', 'import', 'class', 'interface', 'object', 'fun', 'val', 'var', 'if', 'else', 'when',
    'for', 'while', 'do', 'return', 'break', 'continue', 'this', 'super', 'null', 'true', 'false',
    'try', 'catch', 'finally', 'throw', 'private', 'protected', 'public', 'internal', 'data',
    'sealed', 'enum', 'open', 'abstract', 'override', 'companion', 'suspend', 'inline', 'tailrec',
    'operator', 'infix', 'reified', 'typealias', 'as', 'is', 'in', 'by', 'constructor', 'init'
  ],
  java: [
    'package', 'import', 'class', 'interface', 'enum', 'extends', 'implements', 'public', 'private',
    'protected', 'static', 'final', 'abstract', 'native', 'synchronized', 'transient', 'volatile',
    'void', 'boolean', 'byte', 'char', 'short', 'int', 'long', 'float', 'double', 'if', 'else',
    'switch', 'case', 'default', 'while', 'do', 'for', 'break', 'continue', 'return', 'throw',
    'throws', 'try', 'catch', 'finally', 'new', 'this', 'super', 'instanceof', 'null', 'true', 'false'
  ],
  python: [
    'def', 'class', 'import', 'from', 'as', 'return', 'if', 'elif', 'else', 'while', 'for', 'in',
    'try', 'except', 'finally', 'raise', 'with', 'lambda', 'yield', 'pass', 'break', 'continue',
    'global', 'nonlocal', 'assert', 'async', 'await', 'True', 'False', 'None', 'is', 'not', 'and', 'or'
  ],
  typescript: [
    'const', 'let', 'var', 'function', 'class', 'interface', 'type', 'enum', 'import', 'from', 'export',
    'default', 'return', 'if', 'else', 'switch', 'case', 'default', 'while', 'for', 'of', 'in', 'try',
    'catch', 'finally', 'throw', 'async', 'await', 'yield', 'this', 'super', 'new', 'typeof',
    'instanceof', 'void', 'null', 'undefined', 'true', 'false', 'extends', 'implements', 'as', 'is',
    'keyof', 'readonly', 'public', 'private', 'protected', 'abstract', 'static'
  ],
  javascript: [
    'const', 'let', 'var', 'function', 'class', 'import', 'from', 'export', 'default', 'return', 'if',
    'else', 'switch', 'case', 'default', 'while', 'for', 'of', 'in', 'try', 'catch', 'finally',
    'throw', 'async', 'await', 'yield', 'this', 'super', 'new', 'typeof', 'instanceof', 'void', 'null',
    'undefined', 'true', 'false'
  ],
  gradle: [
    'plugins', 'android', 'dependencies', 'implementation', 'testImplementation', 'androidTestImplementation',
    'defaultConfig', 'buildTypes', 'compileOptions', 'kotlinOptions', 'repositories', 'google', 'mavenCentral',
    'val', 'var', 'apply', 'project', 'group', 'version', 'sourceCompatibility', 'targetCompatibility'
  ],
  bash: [
    'echo', 'exit', 'if', 'then', 'else', 'elif', 'fi', 'for', 'in', 'do', 'done', 'while', 'until',
    'case', 'esac', 'function', 'return', 'export', 'local', 'source', 'alias', 'cd', 'ls', 'mkdir', 'rm'
  ],
  sql: [
    'SELECT', 'FROM', 'WHERE', 'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE', 'CREATE', 'TABLE',
    'DROP', 'ALTER', 'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'GROUP', 'BY', 'ORDER', 'ASC',
    'DESC', 'HAVING', 'LIMIT', 'OFFSET', 'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES', 'NOT', 'NULL', 'AND', 'OR'
  ],
  xml: [],
  json: ['true', 'false', 'null'],
  html: [],
  css: [],
  markdown: [],
  plaintext: [],
};

export function highlightCode(code: string, language: SupportedLanguage): string {
  if (!code) return '';
  const escaped = escapeHtml(code);
  const lines = escaped.split('\n');

  const langKeywords = KEYWORDS[language] || [];
  const keywordSet = new Set(langKeywords);

  return lines.map((line) => {
    // 1. Comments
    if (language === 'python' || language === 'bash') {
      const hashIndex = line.indexOf('#');
      if (hashIndex !== -1) {
        const before = line.slice(0, hashIndex);
        const comment = line.slice(hashIndex);
        return highlightTokens(before, keywordSet, language) + `<span class="text-zinc-500 italic">${comment}</span>`;
      }
    } else if (language !== 'html' && language !== 'xml' && language !== 'css') {
      const slashIndex = line.indexOf('//');
      if (slashIndex !== -1) {
        const before = line.slice(0, slashIndex);
        const comment = line.slice(slashIndex);
        return highlightTokens(before, keywordSet, language) + `<span class="text-zinc-500 italic">${comment}</span>`;
      }
    }

    if (language === 'xml' || language === 'html') {
      // Tags highlighting
      return line.replace(/(&lt;\/?)([a-zA-Z0-9_\-:]+)([^&]*)(&gt;)/g, (_m, p1, p2, p3, p4) => {
        const coloredAttrs = p3.replace(/([a-zA-Z0-9_\-:]+)=(&quot;.*?&quot;|&#039;.*?&#039;)/g, 
          '<span class="text-amber-300">$1</span>=<span class="text-emerald-300">$2</span>'
        );
        return `<span class="text-zinc-500">${p1}</span><span class="text-blue-400 font-medium">${p2}</span>${coloredAttrs}<span class="text-zinc-500">${p4}</span>`;
      });
    }

    return highlightTokens(line, keywordSet, language);
  }).join('\n');
}

function highlightTokens(line: string, keywords: Set<string>, language: SupportedLanguage): string {
  // Strings
  let result = line.replace(/(&quot;.*?&quot;|&#039;.*?&#039;|`.*?`)/g, '<span class="text-emerald-400">$1</span>');

  // Numbers
  result = result.replace(/\b(\d+(\.\d+)?([fFL]?))\b/g, '<span class="text-orange-300">$1</span>');

  // Kotlin/Java Annotations e.g. @Composable, @Override, @Entity, @Dao
  if (language === 'kotlin' || language === 'java' || language === 'python' || language === 'typescript') {
    result = result.replace(/(@[a-zA-Z0-9_]+)/g, '<span class="text-yellow-400 font-medium">$1</span>');
  }

  // Keywords and Types
  result = result.replace(/\b([a-zA-Z_][a-zA-Z0-9_]*)\b/g, (match) => {
    if (keywords.has(match)) {
      return `<span class="text-purple-400 font-semibold">${match}</span>`;
    }
    // Type recognition: PascalCase (e.g. String, StateFlow, Modifier, UserEntity)
    if (/^[A-Z][a-zA-Z0-9_]*$/.test(match) && match !== match.toUpperCase()) {
      return `<span class="text-cyan-300 font-medium">${match}</span>`;
    }
    return match;
  });

  return result;
}

// Tree view builder
export interface FileTreeNode {
  name: string;
  path: string;
  isDirectory: boolean;
  children?: FileTreeNode[];
  language?: SupportedLanguage;
}

export function buildFileTree(files: { path: string; language: SupportedLanguage }[]): FileTreeNode {
  const root: FileTreeNode = {
    name: 'root',
    path: '',
    isDirectory: true,
    children: [],
  };

  for (const file of files) {
    const parts = file.path.split('/').filter(Boolean);
    let current = root;

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      const isFile = i === parts.length - 1;
      const currentPath = parts.slice(0, i + 1).join('/');

      if (!current.children) {
        current.children = [];
      }

      let existing = current.children.find((c) => c.name === part);
      if (!existing) {
        existing = {
          name: part,
          path: currentPath,
          isDirectory: !isFile,
          children: isFile ? undefined : [],
          language: isFile ? file.language : undefined,
        };
        current.children.push(existing);
      }
      current = existing;
    }
  }

  // Sort directories first, then alphabetical
  function sortNodes(node: FileTreeNode) {
    if (node.children) {
      node.children.sort((a, b) => {
        if (a.isDirectory && !b.isDirectory) return -1;
        if (!a.isDirectory && b.isDirectory) return 1;
        return a.name.localeCompare(b.name);
      });
      node.children.forEach(sortNodes);
    }
  }

  sortNodes(root);
  return root;
}
