<template>
  <div class="editor-wrapper">
    <div ref="editorContainer" class="editor-container"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'

// 配置Monaco Editor Web Workers
self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === 'json') return new jsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker()
    if (label === 'typescript' || label === 'javascript') return new tsWorker()
    return new editorWorker()
  }
}

// 全局强制深色主题
monaco.editor.setTheme('vs-dark')

const props = defineProps({
  content: { type: String, default: '' },
  oldContent: { type: String, default: '' },
  language: { type: String, default: 'java' },
  readOnly: { type: Boolean, default: true },
  issues: { type: Array, default: () => [] },
  theme: { type: String, default: 'vs-dark' }
})

const emit = defineEmits(['scrollToLine', 'ready'])

const editorContainer = ref(null)
let diffEditor = null
let decorations = []

onMounted(async () => {
  await nextTick()
  if (!editorContainer.value) return

  // 创建双栏 Diff 编辑器
  diffEditor = monaco.editor.createDiffEditor(editorContainer.value, {
    theme: 'vs-dark',
    fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
    fontLigatures: true,
    smoothScrolling: true,
    cursorSmoothCaretAnimation: 'on',
    minimap: { enabled: true, scale: 0.75 },
    readOnly: props.readOnly,
    fontSize: 13,
    lineNumbers: 'on',
    automaticLayout: true,
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    glyphMargin: true,
    renderLineHighlight: 'all',
    folding: true,
    foldingStrategy: 'indentation',
    // Diff 专属配置
    renderSideBySide: true,
    ignoreTrimWhitespace: false,
    renderIndicators: true,
    originalEditable: false,
    diffWordWrap: 'on'
  })

  // 左侧 original = 旧代码，右侧 modified = 新代码
  const originalModel = monaco.editor.createModel(props.oldContent || '', props.language)
  const modifiedModel = monaco.editor.createModel(props.content || '', props.language)
  diffEditor.setModel({ original: originalModel, modified: modifiedModel })

  // 问题标记渲染在右侧（新代码）编辑器上
  renderIssueMarkers()

  emit('ready', diffEditor)
})

watch(() => props.content, (val) => {
  if (!diffEditor) return
  const modified = diffEditor.getModifiedEditor()
  if (modified && val !== modified.getValue()) {
    modified.setValue(val || '')
  }
})

watch(() => props.oldContent, (val) => {
  if (!diffEditor) return
  const original = diffEditor.getOriginalEditor()
  if (original && val !== original.getValue()) {
    original.setValue(val || '')
  }
})

watch(() => props.language, (val) => {
  if (!diffEditor) return
  const model = diffEditor.getModel()
  if (model) {
    monaco.editor.setModelLanguage(model.original, val)
    monaco.editor.setModelLanguage(model.modified, val)
  }
})

watch(() => props.issues, () => {
  renderIssueMarkers()
}, { deep: true })

/**
 * 渲染问题标记 - 红点标记 + 高亮行（挂在右侧 modified 编辑器）
 */
function renderIssueMarkers() {
  if (!diffEditor) return
  const editor = diffEditor.getModifiedEditor()
  if (!editor) return

  if (!props.issues || props.issues.length === 0) {
    decorations = editor.deltaDecorations(decorations, [])
    return
  }

  const newDecorations = []
  const issueLines = new Map() // line -> issues

  props.issues.forEach(issue => {
    const line = issue.lineStart
    if (!issueLines.has(line)) issueLines.set(line, [])
    issueLines.get(line).push(issue)
  })

  issueLines.forEach((lineIssues, line) => {
    // 最高严重程度
    const severities = lineIssues.map(i => i.severity)
    const topSeverity = severities.includes('CRITICAL') ? 'critical'
      : severities.includes('MAJOR') ? 'major'
      : severities.includes('MINOR') ? 'minor' : 'info'

    // GitHub Dark 调色板
    const color = topSeverity === 'critical' ? '#f85149'
      : topSeverity === 'major' ? '#d29922'
      : topSeverity === 'minor' ? '#388bfd' : '#8b949e'

    // Glyph边距红点标记
    newDecorations.push({
      range: new monaco.Range(line, 1, line, 1),
      options: {
        glyphMarginClassName: `issue-gutter-marker ${topSeverity}`,
        glyphMarginHoverMessage: {
          value: lineIssues.map(i =>
            `**[${i.severity}] ${i.category}**  \n${i.title}  \n${i.description || ''}`
          ).join('\n\n---\n\n')
        },
        isWholeLine: true,
        className: `issue-highlight-${topSeverity}`,
        overviewRuler: {
          color: color,
          position: monaco.editor.OverviewRulerLane.Right
        }
      }
    })

    // 多行高亮（如果有endLine）
    lineIssues.forEach(issue => {
      if (issue.lineEnd && issue.lineEnd > issue.lineStart) {
        newDecorations.push({
          range: new monaco.Range(issue.lineStart, 1, issue.lineEnd, 1),
          options: {
            isWholeLine: true,
            className: `issue-highlight-${topSeverity}`,
            hoverMessage: { value: `**${issue.title}**  \n${issue.description || ''}` }
          }
        })
      }
    })
  })

  decorations = editor.deltaDecorations(decorations, newDecorations)
}

// 滚动到指定行（右侧 modified 编辑器）
function scrollToLine(line) {
  if (diffEditor) {
    const modified = diffEditor.getModifiedEditor()
    modified.revealLineInCenter(line)
    modified.setPosition({ lineNumber: line, column: 1 })
    modified.focus()
  }
}

// 获取编辑器实例（返回右侧 modified 编辑器，兼容原有交互）
function getEditor() {
  return diffEditor ? diffEditor.getModifiedEditor() : null
}

// 获取 Diff 编辑器根实例
function getDiffEditor() {
  return diffEditor
}

defineExpose({ scrollToLine, getEditor, getDiffEditor })

onUnmounted(() => {
  if (diffEditor) {
    const model = diffEditor.getModel()
    if (model) {
      model.original.dispose()
      model.modified.dispose()
    }
    diffEditor.dispose()
    diffEditor = null
  }
})
</script>

<style>
/* 外层容器：圆角 + 深色边框 + 微光投影 */
.editor-wrapper {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  border: 1px solid #30363d;
  overflow: hidden;
  background: #0d1117;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.3),
    0 4px 12px rgba(0, 0, 0, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.editor-container {
  width: 100%;
  height: 100%;
}

/* Diff 编辑器内部背景对齐深色基调 */
.editor-container :deep(.monaco-editor),
.editor-container :deep(.monaco-diff-editor) {
  --vscode-editor-background: #0d1117;
}

.editor-container :deep(.monaco-editor .margin),
.editor-container :deep(.monaco-editor .monaco-editor-background) {
  background-color: #0d1117 !important;
}

.editor-container :deep(.monaco-diff-editor .diffOverview) {
  background: #161b22 !important;
}

.editor-container :deep(.monaco-editor .line-numbers) {
  color: #6e7681 !important;
}

/* Diff 分隔条样式 */
.editor-container :deep(.monaco-diff-editor .diffViewport) {
  background: rgba(56, 139, 253, 0.1) !important;
}

/* 行高亮样式（GitHub Dark 调色板，半透明毛玻璃感） */
.issue-highlight-critical {
  background-color: rgba(248, 81, 73, 0.12);
  border-left: 3px solid #f85149;
}
.issue-highlight-major {
  background-color: rgba(210, 153, 34, 0.12);
  border-left: 3px solid #d29922;
}
.issue-highlight-minor {
  background-color: rgba(56, 139, 253, 0.12);
  border-left: 3px solid #388bfd;
}
.issue-highlight-info {
  background-color: rgba(139, 148, 158, 0.08);
  border-left: 3px solid #8b949e;
}

/* 装订线标记圆点 */
.issue-gutter-marker {
  position: relative;
}
.issue-gutter-marker::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.issue-gutter-marker.critical::after { background: #f85149; box-shadow: 0 0 6px rgba(248, 81, 73, 0.6); }
.issue-gutter-marker.major::after { background: #d29922; box-shadow: 0 0 6px rgba(210, 153, 34, 0.6); }
.issue-gutter-marker.minor::after { background: #388bfd; box-shadow: 0 0 6px rgba(56, 139, 253, 0.6); }
.issue-gutter-marker.info::after { background: #8b949e; box-shadow: 0 0 4px rgba(139, 148, 158, 0.4); }
</style>
