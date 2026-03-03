'use client'

import { motion } from 'framer-motion'

interface Props {
  code: string
}

export default function CodePreview({ code }: Props) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      className="relative group"
    >
      <div className="
        flex items-center justify-between px-4 py-2
        bg-[var(--surface-2)] border border-b-0 border-[var(--surface-3)]
        rounded-t-xl
      ">
        <div className="flex items-center gap-2">
          <div className="flex gap-1.5">
            <span className="w-2.5 h-2.5 rounded-full bg-[#ff5f57]" />
            <span className="w-2.5 h-2.5 rounded-full bg-[#febc2e]" />
            <span className="w-2.5 h-2.5 rounded-full bg-[#28c840]" />
          </div>
          <span className="text-xs text-[#555566] font-mono ml-2">
            Generated Kotlin
          </span>
        </div>
        <button
          onClick={() => navigator.clipboard.writeText(code)}
          className="
            text-xs text-[#666680] hover:text-[var(--accent)]
            transition-colors font-mono opacity-0 group-hover:opacity-100
          "
        >
          Copy
        </button>
      </div>
      <pre className="
        code-block rounded-t-none border-t-0
        max-h-80 overflow-auto
      ">
        {highlightKotlin(code)}
      </pre>
    </motion.div>
  )
}

/* -- Minimal Kotlin syntax colouring -------------------------------- */

function highlightKotlin(code: string): React.ReactNode[] {
  const lines = code.split('\n')
  return lines.map((line, i) => {
    const processed = line
      // Keywords
      .replace(
        /\b(val|var|fun|class|object|import|package|return|if|else|when|for|while|is|as|in|by|data|sealed|suspend|override|private|internal|public|companion)\b/g,
        '<kw>$1</kw>',
      )
      // Strings
      .replace(/"([^"]*)"/g, '<str>"$1"</str>')
      // Numbers
      .replace(/\b(\d+\.?\d*[fFL]?)\b/g, '<num>$1</num>')
      // Comments
      .replace(/(\/\/.*)$/, '<cmt>$1</cmt>')

    return (
      <span key={i} dangerouslySetInnerHTML={{
        __html: processed
          .replace(/<kw>/g, '<span style="color:#c792ea">')
          .replace(/<\/kw>/g, '</span>')
          .replace(/<str>/g, '<span style="color:#c3e88d">')
          .replace(/<\/str>/g, '</span>')
          .replace(/<num>/g, '<span style="color:#f78c6c">')
          .replace(/<\/num>/g, '</span>')
          .replace(/<cmt>/g, '<span style="color:#546e7a">')
          .replace(/<\/cmt>/g, '</span>'),
      }} />
    )
  }).reduce<React.ReactNode[]>((acc, el, i) => {
    if (i > 0) acc.push('\n')
    acc.push(el)
    return acc
  }, [])
}
