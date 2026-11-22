import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('auralixAPI', {
  // Window control actions
  minimizeWindow: () => ipcRenderer.send('window-minimize'),
  maximizeWindow: () => ipcRenderer.send('window-maximize'),
  closeWindow: () => ipcRenderer.send('window-close'),
  isMaximized: () => ipcRenderer.invoke('window-is-maximized'),

  // Discord Rich Presence
  updateDiscordPresence: (song: any) => ipcRenderer.send('discord-update', song),

  // Local File playback & folder selection
  openAudioFiles: () => ipcRenderer.invoke('open-audio-files'),
  readAudioFile: (path: string) => ipcRenderer.invoke('read-audio-file', path),

  // Platform info
  platform: process.platform
})
