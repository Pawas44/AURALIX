import { Song } from '../types/music'

// Resilient public Piped & Invidious instances for audio extraction & search
const INVIDIOUS_INSTANCES = [
  'https://inv.nadeko.net',
  'https://invidious.nerdvpn.de',
  'https://invidious.jing.rocks',
  'https://yt.artemislena.eu'
]

function formatSeconds(sec: number): string {
  const m = Math.floor(sec / 60)
  const s = Math.floor(sec % 60)
  return `${m}:${s < 10 ? '0' : ''}${s}`
}

export async function searchYouTube(query: string): Promise<Song[]> {
  if (!query.trim()) return []

  // Try InnerTube / Invidious search
  for (const instance of INVIDIOUS_INSTANCES) {
    try {
      const res = await fetch(`${instance}/api/v1/search?q=${encodeURIComponent(query)}&type=video`, {
        signal: AbortSignal.timeout(4000)
      })
      if (!res.ok) continue
      const data = await res.json()

      if (Array.isArray(data) && data.length > 0) {
        return data.slice(0, 25).map((item: any) => ({
          id: item.videoId,
          title: item.title || 'Unknown Title',
          artist: item.author || 'Unknown Artist',
          duration: item.lengthSeconds || 180,
          durationFormatted: formatSeconds(item.lengthSeconds || 180),
          artworkUrl: item.videoThumbnails?.find((t: any) => t.quality === 'high')?.url ||
            `https://img.youtube.com/vi/${item.videoId}/hqdefault.jpg`,
          views: item.viewCount ? `${(item.viewCount / 1000000).toFixed(1)}M plays` : undefined
        }))
      }
    } catch {}
  }

  // Resilient fallback via DuckDuckGo / YouTube search endpoint
  try {
    const fallbackRes = await fetch(`https://pipedapi.kavin.rocks/search?q=${encodeURIComponent(query)}&filter=music_songs`, {
      signal: AbortSignal.timeout(4000)
    })
    if (fallbackRes.ok) {
      const data = await fallbackRes.json()
      if (data.items && Array.isArray(data.items)) {
        return data.items.slice(0, 25).map((item: any) => ({
          id: item.url.replace('/watch?v=', ''),
          title: item.title,
          artist: item.uploaderName || 'Artist',
          duration: item.duration || 180,
          durationFormatted: formatSeconds(item.duration || 180),
          artworkUrl: item.thumbnail || `https://img.youtube.com/vi/${item.url.replace('/watch?v=', '')}/hqdefault.jpg`,
        }))
      }
    }
  } catch {}

  return []
}

export async function getTrendingMusic(): Promise<Song[]> {
  // Curated initial charts / trending hits
  const trendingQueries = ['top hits 2026', 'trending english songs', 'trending lofi beats', 'global top 50']
  const randomQuery = trendingQueries[Math.floor(Math.random() * trendingQueries.length)]
  const results = await searchYouTube(randomQuery)
  if (results.length > 0) return results

  // Default fallback starter tracks if offline
  return [
    {
      id: 'kJQP7kiw5Fk',
      title: 'Despacito',
      artist: 'Luis Fonsi ft. Daddy Yankee',
      duration: 228,
      durationFormatted: '3:48',
      artworkUrl: 'https://img.youtube.com/vi/kJQP7kiw5Fk/hqdefault.jpg',
      views: '8.2B plays'
    },
    {
      id: 'JGwWNGJdvx8',
      title: 'Shape of You',
      artist: 'Ed Sheeran',
      duration: 233,
      durationFormatted: '3:53',
      artworkUrl: 'https://img.youtube.com/vi/JGwWNGJdvx8/hqdefault.jpg',
      views: '6.1B plays'
    },
    {
      id: 'RgKAFK5djSk',
      title: 'See You Again',
      artist: 'Wiz Khalifa ft. Charlie Puth',
      duration: 237,
      durationFormatted: '3:57',
      artworkUrl: 'https://img.youtube.com/vi/RgKAFK5djSk/hqdefault.jpg',
      views: '5.9B plays'
    },
    {
      id: '09R8_2nJtjg',
      title: 'Sugar',
      artist: 'Maroon 5',
      duration: 235,
      durationFormatted: '3:55',
      artworkUrl: 'https://img.youtube.com/vi/09R8_2nJtjg/hqdefault.jpg',
      views: '3.9B plays'
    }
  ]
}

export async function resolveAudioStreamUrl(videoId: string): Promise<string> {
  // Query Invidious stream API to get direct audio URL (m4a / opus)
  for (const instance of INVIDIOUS_INSTANCES) {
    try {
      const res = await fetch(`${instance}/api/v1/videos/${videoId}`, {
        signal: AbortSignal.timeout(4500)
      })
      if (!res.ok) continue
      const data = await res.json()

      const audioFormats = data.adaptiveFormats?.filter((f: any) =>
        f.type?.startsWith('audio/') || f.container === 'm4a' || f.container === 'webm'
      )

      if (audioFormats && audioFormats.length > 0) {
        // Pick best audio stream
        audioFormats.sort((a: any, b: any) => (parseInt(b.bitrate) || 0) - (parseInt(a.bitrate) || 0))
        const best = audioFormats[0]
        if (best.url) {
          return best.url
        }
      }
    } catch {}
  }

  // Backup proxy audio stream
  return `https://invidious.nerdvpn.de/latest_version?id=${videoId}&itag=140`
}
