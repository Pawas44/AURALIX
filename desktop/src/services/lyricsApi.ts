import { LyricLine } from '../types/music'

export async function fetchSyncedLyrics(title: string, artist: string, durationSec: number): Promise<LyricLine[]> {
  const cleanTitle = title.replace(/\(.*?\)|\[.*?\]|official|video|audio|lyrics/gi, '').trim()
  const cleanArtist = artist.split(',')[0].replace(/ft\..*|feat\..*/gi, '').trim()

  try {
    const url = `https://lrclib.net/api/get?track_name=${encodeURIComponent(cleanTitle)}&artist_name=${encodeURIComponent(cleanArtist)}&duration=${Math.round(durationSec)}`
    const res = await fetch(url, { signal: AbortSignal.timeout(4000) })

    if (res.ok) {
      const data = await res.json()
      if (data.syncedLyrics) {
        return parseLrc(data.syncedLyrics)
      } else if (data.plainLyrics) {
        // Plain lyrics fallback with equal spacing
        const lines = data.plainLyrics.split('\n').filter((l: string) => l.trim().length > 0)
        const step = durationSec / Math.max(lines.length, 1)
        return lines.map((text: string, i: number) => ({
          time: i * step,
          text
        }))
      }
    }
  } catch (err) {
    console.log('[Lyrics] LRCLIB search error:', err)
  }

  return []
}

function parseLrc(lrcText: string): LyricLine[] {
  const lines = lrcText.split('\n')
  const result: LyricLine[] = []
  const timeRegex = /\[(\d{2}):(\d{2})\.?(\d{2,3})?\]/

  for (const rawLine of lines) {
    const match = timeRegex.exec(rawLine)
    if (match) {
      const min = parseInt(match[1], 10)
      const sec = parseInt(match[2], 10)
      const ms = match[3] ? parseInt(match[3].padEnd(3, '0').slice(0, 3), 10) : 0
      const totalSeconds = min * 60 + sec + ms / 1000
      const text = rawLine.replace(/\[\d{2}:\d{2}\.?\d{2,3}?\]/g, '').trim()

      if (text.length > 0) {
        result.push({ time: totalSeconds, text })
      }
    }
  }

  return result.sort((a, b) => a.time - b.time)
}
