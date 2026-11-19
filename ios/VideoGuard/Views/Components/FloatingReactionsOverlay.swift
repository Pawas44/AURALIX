import SwiftUI

struct FloatingReactionsOverlay: View {
    @ObservedObject var jamEngine = ListenTogetherEngine.shared

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .bottomLeading) {
                ForEach(jamEngine.floatingReactions) { reaction in
                    SingleFloatingReactionView(
                        reaction: reaction,
                        containerSize: geo.size,
                        onFinished: {
                            jamEngine.removeFloatingReaction(id: reaction.id)
                        }
                    )
                }
            }
        }
        .allowsHitTesting(false)
    }
}

private struct SingleFloatingReactionView: View {
    let reaction: FloatingReactionItem
    let containerSize: CGSize
    let onFinished: () -> Void

    @State private var progress: CGFloat = 0.0

    var body: some View {
        let startX = containerSize.width * reaction.startXFraction
        let wobble = sin(progress * .pi * 3.0) * 40.0
        let xPos = startX + wobble
        let yOffset = -progress * max(containerSize.height * 0.65, 550)

        let scale: CGFloat = {
            if progress < 0.15 {
                return 0.4 + (progress / 0.15) * 0.9
            } else if progress < 0.7 {
                return 1.3 - ((progress - 0.15) / 0.55) * 0.2
            } else {
                return 1.1 - ((progress - 0.7) / 0.3) * 0.3
            }
        }()

        let opacity: Double = {
            if progress < 0.65 {
                return 1.0
            } else {
                return Double(max(0, 1.0 - (progress - 0.65) / 0.35))
            }
        }()

        Text(reaction.emoji)
            .font(.system(size: 32))
            .scaleEffect(scale)
            .opacity(opacity)
            .position(x: xPos, y: containerSize.height + yOffset)
            .onAppear {
                withAnimation(.easeOut(duration: 2.2)) {
                    progress = 1.0
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.25) {
                    onFinished()
                }
            }
    }
}
