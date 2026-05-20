<template>
  <div ref="editorContainer" class="editor-container"></div>
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

const props = defineProps({
  content: { type: String, default: '' },
  language: { type: String, default: 'java' },
  readOnly: { type: Boolean, default: true },
  issues: { type: Array, default: () => [] },
  theme: { type: String, default: 'vs' }
})

const emit = defineEmits(['scrollToLine', 'ready'])

const editorContainer = ref(null)
let editor = null
let decorations = []
let contentWidgets = []

onMounted(async () => {
  await nextTick()
  if (!editorContainer.value) return

  editor = monaco.editor.create(editorContainer.value, {
    value: props.content,
    language: props.language,
    readOnly: props.readOnly,
    theme: props.theme,
    fontSize: 13,
    lineNumbers: 'on',
    minimap: { enabled: true },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    wordWrap: 'on',
    glyphMargin: true,
    renderLineHighlight: 'all',
    folding: true,
    foldingStrategy: 'indentation'
  })

  // 渲染问题标记
  renderIssueMarkers()

  emit('ready', editor)
})

watch(() => props.content, (val) => {
  if (editor && val !== editor.getValue()) {
    editor.setValue(val || '')
  }
})

watch(() => props.issues, () => {
  renderIssueMarkers()
}, { deep: true })

/**
 * 渲染问题标记 - 红点标记 + 高亮行
 */
function renderIssueMarkers() {
  if (!editor || !props.issues || props.issues.length === 0) {
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

    const color = topSeverity === 'critical' ? '#F56C6C'
      : topSeverity === 'major' ? '#E6A23C'
      : topSeverity === 'minor' ? '#409EFF' : '#909399'

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

// 滚动到指定行
function scrollToLine(line) {
  if (editor) {
    editor.revealLineInCenter(line)
    editor.setPosition({ lineNumber: line, column: 1 })
    editor.focus()
  }
}

// 获取编辑器实例
function getEditor() {
  return editor
}

defineExpose({ scrollToLine, getEditor })

onUnmounted(() => {
  if (editor) {
    editor.dispose()
    editor = null
  }
})
</script>

<style>
/* 行高亮样式 */
.issue-highlight-critical {
  background-color: rgba(245, 108, 108, 0.15);
  border-left: 3px solid #F56C6C;
}
.issue-highlight-major {
  background-color: rgba(230, 162, 60, 0.15);
  border-left: 3px solid #E6A23C;
}
.issue-highlight-minor {
  background-color: rgba(64, 158, 255, 0.15);
  border-left: 3px solid #409EFF;
}
.issue-highlight-info {
  background-color: rgba(144, 147, 153, 0.1);
  border-left: 3px solid #909399;
}

/* 装订线标记需要 inline style 注入 */
</style>
