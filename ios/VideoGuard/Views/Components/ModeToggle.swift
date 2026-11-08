import SwiftUI

struct ModeToggle: View {
    let currentMode: AppMode
    let onModeChanged: (AppMode) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(AppMode.allCases) { mode in
                let isSelected = mode == currentMode
                Button(action: {
                    onModeChanged(mode)
                }) {
                    Text(mode.rawValue)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(isSelected ? AppTheme.youTubeChipSelectedText : AppTheme.youTubeTextSecondary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 18)
                                .fill(isSelected ? AppTheme.youTubeChipSelectedBg : AppTheme.youTubeChipBg)
                        )
                }
            }
        }
        .padding(3)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(AppTheme.youTubeChipBg)
        )
    }
}

