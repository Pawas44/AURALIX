import DiscordRPC from 'discord-rpc'

const CLIENT_ID = '1358372087017767073'
const TELEGRAM_URL = 'https://t.me/auralix_music'

let rpcClient: DiscordRPC.Client | null = null
let isConnected = false
let reconnectTimeout: NodeJS.Timeout | null = null

export function initDiscordRPC() {
  if (rpcClient) return

  try {
    rpcClient = new DiscordRPC.Client({ transport: 'ipc' })

    rpcClient.on('ready', () => {
      isConnected = true
      console.log('[Discord RPC] Connected as', rpcClient?.user?.username)
    })

    rpcClient.on('disconnected', () => {
      isConnected = false
      scheduleReconnect()
    })

    rpcClient.login({ clientId: CLIENT_ID }).catch((err) => {
      console.log('[Discord RPC] Not running or failed to connect:', err?.message)
      scheduleReconnect()
    })
  } catch (err: any) {
    console.log('[Discord RPC] Init error:', err?.message)
    scheduleReconnect()
  }
}

function scheduleReconnect() {
  isConnected = false
  if (reconnectTimeout) clearTimeout(reconnectTimeout)
  reconnectTimeout = setTimeout(() => {
    try {
      if (rpcClient) {
        rpcClient.login({ clientId: CLIENT_ID }).catch(() => {})
      }
    } catch {}
  }, 15000)
}

export interface SongPresence {
  title: string
  artist: string
  artwork?: string
  duration?: number
  currentSeconds?: number
  isPlaying: boolean
}

export function updateDiscordPresence(song: SongPresence | null) {
  if (!rpcClient || !isConnected) return

  if (!song || !song.title) {
    rpcClient.clearActivity().catch(() => {})
    return
  }

  const startTimestamp = song.isPlaying
    ? Math.floor(Date.now() - (song.currentSeconds || 0) * 1000)
    : undefined
  const endTimestamp = song.isPlaying && song.duration && song.duration > 0
    ? Math.floor(startTimestamp! + song.duration * 1000)
    : undefined

  const activity: DiscordRPC.Presence = {
    details: song.title.length > 128 ? song.title.substring(0, 125) + '...' : song.title,
    state: song.artist.length > 128 ? song.artist.substring(0, 125) + '...' : song.artist,
    startTimestamp,
    endTimestamp,
    largeImageKey: song.artwork && song.artwork.startsWith('http') ? song.artwork : 'auralix_logo',
    largeImageText: 'Listening on Auralix',
    smallImageKey: song.isPlaying ? 'play' : 'pause',
    smallImageText: song.isPlaying ? 'Playing' : 'Paused',
    instance: false,
    buttons: [
      {
        label: 'Join Auralix Music',
        url: TELEGRAM_URL
      }
    ]
  }

  rpcClient.setActivity(activity).catch((err) => {
    console.warn('[Discord RPC] Failed to set activity:', err?.message)
  })
}

export function destroyDiscordRPC() {
  if (reconnectTimeout) clearTimeout(reconnectTimeout)
  if (rpcClient) {
    try {
      rpcClient.clearActivity()
      rpcClient.destroy()
    } catch {}
    rpcClient = null
    isConnected = false
  }
}
