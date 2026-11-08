import SwiftUI

struct GoogleCastButton: View {
    var tint: Color = .white

    var body: some View {
        Button(action: {
            // AirPlay / Cast Action
        }) {
            Image(systemName: "airplayvideo")
                .font(.system(size: 20))
                .foregroundColor(tint)
        }
    }
}

