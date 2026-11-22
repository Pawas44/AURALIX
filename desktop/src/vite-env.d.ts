/// <reference types="vite/client" />

interface Window {
  auralixAPI?: {
    minimizeWindow: () => void
    maximizeWindow: () => void
    closeWindow: () => void
    isMaximized: () => Promise<boolean>
    updateDiscordPresence: (song: any) => void
    openAudioFiles: () => Promise<string[]>
    readAudioFile: (path: string) => Promise<ArrayBuffer>
    platform: string
  }
}
