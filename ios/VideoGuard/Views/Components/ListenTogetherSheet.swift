import SwiftUI

struct ListenTogetherSheet: View {
    var showDismissButton: Bool = true
    @ObservedObject var jamEngine = ListenTogetherEngine.shared
    @ObservedObject var playbackService = MediaPlaybackService.shared
    @Environment(\.presentationMode) var presentationMode

    @State private var usernameInput: String = ""
    @State private var roomCodeInput: String = ""
    @State private var chatInput: String = ""
    @State private var activeTab: Int = 0 // 0: Friends, 1: Chat
    @State private var copiedAlert: Bool = false

    private let jamPurple = Color(red: 139/255, green: 92/255, blue: 246/255)
    private let jamCyan = Color(red: 6/255, green: 182/255, blue: 212/255)
    private let jamPink = Color(red: 236/255, green: 72/255, blue: 153/255)
    private let cardBg = Color(red: 28/255, green: 28/255, blue: 36/255)

    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 16/255, green: 16/255, blue: 22/255)
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 18) {
                        // Header Bar
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(LinearGradient(colors: [jamPurple, jamPink], startPoint: .topLeading, endPoint: .bottomTrailing))
                                    .frame(width: 40, height: 40)
                                Image(systemName: "dot.radiowaves.left.and.right")
                                    .foregroundColor(.white)
                                    .font(.system(size: 18, weight: .bold))
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 8) {
                                    Text("Listen Together")
                                        .font(.system(size: 20, weight: .bold))
                                        .foregroundColor(.white)

                                    if jamEngine.isInRoom {
                                        Text("LIVE")
                                            .font(.system(size: 10, weight: .black))
                                            .foregroundColor(jamCyan)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(jamCyan.opacity(0.2))
                                            .cornerRadius(6)
                                    }
                                }

                                Text(jamEngine.isInRoom ? "Syncing playback with friends in real-time" : "Jam with friends anywhere in the world")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.6))
                            }

                            Spacer()

                            if showDismissButton {
                                Button(action: { presentationMode.wrappedValue.dismiss() }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.white.opacity(0.5))
                                        .font(.system(size: 24))
                                }
                            }
                        }
                        .padding(.top, 10)

                        if !jamEngine.isInRoom {
                            // SETUP & HOST / JOIN VIEWS

                            // Display Name Card
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Your Display Name")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.8))

                                TextField("Enter your nickname", text: $usernameInput, onCommit: {
                                    jamEngine.setUsername(usernameInput)
                                })
                                .textFieldStyle(PlainTextFieldStyle())
                                .padding(12)
                                .background(Color.black.opacity(0.35))
                                .cornerRadius(12)
                                .foregroundColor(.white)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                                )
                            }
                            .padding(16)
                            .background(cardBg)
                            .cornerRadius(18)

                            // Start Jam (Host) Card
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(spacing: 8) {
                                    Image(systemName: "play.circle.fill")
                                        .foregroundColor(jamPurple)
                                        .font(.system(size: 20))
                                    Text("Start a Jam Session")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                }

                                Text("Create a room and invite your friends. Everyone hears the music at the exact same second.")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.6))
                                    .lineSpacing(2)

                                Button(action: {
                                    jamEngine.createRoom(username: usernameInput)
                                }) {
                                    HStack {
                                        if jamEngine.connectionState == .connecting {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        } else {
                                            Image(systemName: "waveform")
                                            Text("Create Jam Session")
                                                .fontWeight(.bold)
                                        }
                                    }
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .background(jamPurple)
                                    .cornerRadius(12)
                                }
                                .disabled(jamEngine.connectionState == .connecting)
                            }
                            .padding(16)
                            .background(cardBg)
                            .cornerRadius(18)

                            // Join Jam Card
                            VStack(alignment: .leading, spacing: 10) {
                                HStack(spacing: 8) {
                                    Image(systemName: "person.crop.circle.badge.plus")
                                        .foregroundColor(jamCyan)
                                        .font(.system(size: 20))
                                    Text("Join a Friend's Jam")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                }

                                Text("Enter the 8-character Room Code shared by your friend.")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.6))

                                HStack(spacing: 10) {
                                    TextField("e.g. 6Z9SY3LU", text: $roomCodeInput)
                                        .textFieldStyle(PlainTextFieldStyle())
                                        .padding(12)
                                        .background(Color.black.opacity(0.35))
                                        .cornerRadius(12)
                                        .foregroundColor(.white)
                                        .textCase(.uppercase)
                                        .font(.system(.body, design: .monospaced))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(jamCyan.opacity(0.3), lineWidth: 1)
                                        )

                                    Button(action: {
                                        jamEngine.joinRoom(code: roomCodeInput, username: usernameInput)
                                    }) {
                                        Text("Join")
                                            .fontWeight(.bold)
                                            .foregroundColor(.black)
                                            .padding(.horizontal, 20)
                                            .frame(height: 44)
                                            .background(jamCyan)
                                            .cornerRadius(12)
                                    }
                                    .disabled(roomCodeInput.trimmingCharacters(in: .whitespaces).count < 4 || jamEngine.connectionState == .connecting)
                                }
                            }
                            .padding(16)
                            .background(cardBg)
                            .cornerRadius(18)

                        } else {
                            // ACTIVE JAM ROOM UI

                            // Room Code Banner
                            VStack(spacing: 8) {
                                Text(jamEngine.isHost ? "👑 YOU ARE THE HOST" : "🎧 SYNCED WITH \(jamEngine.hostUsername ?? "HOST")")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(jamEngine.isHost ? jamPurple : jamCyan)
                                    .tracking(1.2)

                                HStack(spacing: 12) {
                                    Text(jamEngine.roomCode ?? "------")
                                        .font(.system(size: 30, weight: .heavy, design: .monospaced))
                                        .foregroundColor(.white)
                                        .tracking(3)

                                    Button(action: {
                                        if let code = jamEngine.roomCode {
                                            UIPasteboard.general.string = code
                                            copiedAlert = true
                                            DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                                copiedAlert = false
                                            }
                                        }
                                    }) {
                                        Image(systemName: copiedAlert ? "checkmark" : "doc.on.doc")
                                            .foregroundColor(.white)
                                            .padding(10)
                                            .background(Color.white.opacity(0.12))
                                            .clipShape(Circle())
                                    }

                                    Button(action: {
                                        if let code = jamEngine.roomCode {
                                            let text = "Join my AURALIX Listen Together Jam session! Room Code: \(code)\nListen together in real-time on AURALIX: https://auralix-bice.vercel.app"
                                            let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
                                            if let root = UIApplication.shared.windows.first?.rootViewController {
                                                root.present(av, animated: true)
                                            }
                                        }
                                    }) {
                                        Image(systemName: "square.and.arrow.up")
                                            .foregroundColor(jamPurple)
                                            .padding(10)
                                            .background(jamPurple.opacity(0.2))
                                            .clipShape(Circle())
                                    }
                                }

                                if let track = playbackService.currentTrack {
                                    Text("Playing: \(track.title) • \(track.channelName)")
                                        .font(.system(size: 12))
                                        .foregroundColor(.white.opacity(0.7))
                                        .lineLimit(1)
                                }
                            }
                            .padding(18)
                            .frame(maxWidth: .infinity)
                            .background(cardBg)
                            .cornerRadius(18)

                            // Tabs: Friends vs Live Chat
                            HStack(spacing: 0) {
                                Button(action: { activeTab = 0 }) {
                                    Text("Friends (\(jamEngine.participants.count))")
                                        .font(.system(size: 13, weight: activeTab == 0 ? .bold : .medium))
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(activeTab == 0 ? jamPurple : Color.clear)
                                        .cornerRadius(10)
                                }

                                Button(action: { activeTab = 1 }) {
                                    Text("Jam Chat (\(jamEngine.chatMessages.count))")
                                        .font(.system(size: 13, weight: activeTab == 1 ? .bold : .medium))
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(activeTab == 1 ? jamPurple : Color.clear)
                                        .cornerRadius(10)
                                }
                            }
                            .padding(4)
                            .background(Color.black.opacity(0.3))
                            .cornerRadius(12)

                            if activeTab == 0 {
                                // Participants List
                                VStack(spacing: 8) {
                                    ForEach(jamEngine.participants) { p in
                                        HStack {
                                            ZStack {
                                                Circle()
                                                    .fill(p.isHost ? jamPurple : jamCyan.opacity(0.5))
                                                    .frame(width: 34, height: 34)
                                                Text(String(p.username.prefix(1)).uppercased())
                                                    .font(.system(size: 15, weight: .bold))
                                                    .foregroundColor(.white)
                                            }

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(p.username)
                                                    .font(.system(size: 14, weight: .medium))
                                                    .foregroundColor(.white)
                                                Text(p.isHost ? "Session Host" : "Listener")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.white.opacity(0.5))
                                            }

                                            Spacer()

                                            if p.isHost {
                                                Text("HOST")
                                                    .font(.system(size: 10, weight: .bold))
                                                    .foregroundColor(jamPurple)
                                                    .padding(.horizontal, 8)
                                                    .padding(.vertical, 3)
                                                    .background(jamPurple.opacity(0.2))
                                                    .cornerRadius(6)
                                            } else if jamEngine.isHost {
                                                Button(action: {
                                                    jamEngine.kickUser(userId: p.userId)
                                                }) {
                                                    Image(systemName: "person.fill.xmark")
                                                        .foregroundColor(.red.opacity(0.7))
                                                        .font(.system(size: 14))
                                                }
                                            }
                                        }
                                        .padding(.vertical, 4)
                                    }
                                }
                                .padding(12)
                                .background(cardBg)
                                .cornerRadius(14)
                            } else {
                                // Live Jam Chat Stream
                                VStack(spacing: 8) {
                                    ScrollViewReader { proxy in
                                        ScrollView {
                                            VStack(spacing: 6) {
                                                ForEach(jamEngine.chatMessages) { msg in
                                                    let isMe = msg.username == jamEngine.myUsername
                                                    HStack {
                                                        if isMe { Spacer() }
                                                        VStack(alignment: isMe ? .trailing : .leading, spacing: 2) {
                                                            if !isMe {
                                                                Text(msg.username)
                                                                    .font(.system(size: 10, weight: .bold))
                                                                    .foregroundColor(jamCyan)
                                                            }
                                                            Text(msg.message)
                                                                .font(.system(size: 13))
                                                                .foregroundColor(.white)
                                                        }
                                                        .padding(.horizontal, 12)
                                                        .padding(.vertical, 6)
                                                        .background(isMe ? jamPurple.opacity(0.6) : Color.white.opacity(0.1))
                                                        .cornerRadius(12)
                                                        if !isMe { Spacer() }
                                                    }
                                                    .id(msg.id)
                                                }
                                            }
                                        }
                                        .frame(height: 140)
                                        .onChange(of: jamEngine.chatMessages.count) { _ in
                                            if let last = jamEngine.chatMessages.last {
                                                proxy.scrollTo(last.id, anchor: .bottom)
                                            }
                                        }
                                    }

                                    // Chat input
                                    HStack(spacing: 8) {
                                        TextField("Say something...", text: $chatInput, onCommit: {
                                            if !chatInput.trimmingCharacters(in: .whitespaces).isEmpty {
                                                jamEngine.sendChatMessage(chatInput)
                                                chatInput = ""
                                            }
                                        })
                                        .textFieldStyle(PlainTextFieldStyle())
                                        .padding(10)
                                        .background(Color.black.opacity(0.3))
                                        .cornerRadius(10)
                                        .foregroundColor(.white)

                                        Button(action: {
                                            if !chatInput.trimmingCharacters(in: .whitespaces).isEmpty {
                                                jamEngine.sendChatMessage(chatInput)
                                                chatInput = ""
                                            }
                                        }) {
                                            Image(systemName: "paperplane.fill")
                                                .foregroundColor(.white)
                                                .padding(10)
                                                .background(jamPurple)
                                                .clipShape(Circle())
                                        }
                                    }
                                }
                                .padding(12)
                                .background(cardBg)
                                .cornerRadius(14)
                            }

                            // Quick Emoji Reactions
                            HStack {
                                ForEach(["🔥", "❤️", "🎵", "🚀", "👏", "⚡"], id: \.self) { emoji in
                                    Button(action: {
                                        jamEngine.sendChatMessage(emoji)
                                    }) {
                                        Text(emoji)
                                            .font(.system(size: 18))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(Color.white.opacity(0.08))
                                            .clipShape(Capsule())
                                    }
                                }
                            }

                            // Leave / End Jam Button
                            Button(action: {
                                jamEngine.leaveRoom()
                            }) {
                                HStack {
                                    Image(systemName: "rectangle.portrait.and.arrow.right")
                                    Text(jamEngine.isHost ? "End Jam Session" : "Leave Jam Session")
                                        .fontWeight(.bold)
                                }
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity)
                                .frame(height: 44)
                                .background(Color.red.opacity(0.15))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.red.opacity(0.35), lineWidth: 1)
                                )
                                .cornerRadius(12)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, showDismissButton ? 30 : 120)
                }
            }
            .navigationBarHidden(true)
        }
        .onAppear {
            usernameInput = jamEngine.myUsername
        }
    }
}
