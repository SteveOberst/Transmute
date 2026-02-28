'use client'

import type { MediaDomain } from '@/lib/types'

interface Props {
  src: string
  domain: MediaDomain
  className?: string
}

/**
 * Renders an appropriate media element for the given domain.
 */
export default function MediaPreview({ src, domain, className = '' }: Props) {
  const base = `w-full h-full object-contain ${className}`

  if (domain === 'IMAGE') {
    return (
      <img src={src} alt="Preview" className={base} />
    )
  }

  if (domain === 'VIDEO') {
    return (
      <video
        src={src}
        controls
        className={`${base} rounded-xl`}
        playsInline
      />
    )
  }

  // AUDIO
  return (
    <div className={`flex items-center justify-center ${className}`}>
      <audio src={src} controls className="w-full max-w-lg" />
    </div>
  )
}
