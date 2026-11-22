import { app, BrowserWindow, ipcMain, dialog, shell } from 'electron'
import { join } from 'path'
import { initDiscordRPC, updateDiscordPresence, destroyDiscordRPC } from './discord'

let mainWindow: BrowserWindow | null = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 940,
    minHeight: 640,
    frame: false, // Clean modern frameless window with custom title bar
    titleBarStyle: 'hidden',
    backgroundColor: '#0B0C10',
    show: false,
    webPreferences: {
      preload: join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false // Enable seamless streaming without CORS blocks
    }
  })

  // Smooth window reveal once content is ready
  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  // Prevent external link navigation inside app window, open in user's browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })

  const devServerUrl = process.env.VITE_DEV_SERVER_URL
  if (devServerUrl) {
    mainWindow.loadURL(devServerUrl)
  } else {
    mainWindow.loadFile(join(__dirname, '../dist/index.html'))
  }

  // Window Controls IPC
  ipcMain.on('window-minimize', () => mainWindow?.minimize())
  ipcMain.on('window-maximize', () => {
    if (mainWindow?.isMaximized()) {
      mainWindow.unmaximize()
    } else {
      mainWindow?.maximize()
    }
  })
  ipcMain.on('window-close', () => mainWindow?.close())
  ipcMain.handle('window-is-maximized', () => mainWindow?.isMaximized() ?? false)

  // Discord RPC IPC
  ipcMain.on('discord-update', (_, song) => {
    updateDiscordPresence(song)
  })

  // File Picker IPC
  ipcMain.handle('open-audio-files', async () => {
    if (!mainWindow) return []
    const result = await dialog.showOpenDialog(mainWindow, {
      title: 'Select Audio Files',
      properties: ['openFile', 'multiSelections'],
      filters: [
        { name: 'Audio Files', extensions: ['mp3', 'm4a', 'flac', 'wav', 'ogg', 'opus', 'aac'] }
      ]
    })
    return result.canceled ? [] : result.filePaths
  })

  // Initialize Discord RPC automatically
  try {
    initDiscordRPC()
  } catch (e) {
    console.warn('Discord RPC failed to start:', e)
  }
}

app.whenReady().then(() => {
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  destroyDiscordRPC()
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
