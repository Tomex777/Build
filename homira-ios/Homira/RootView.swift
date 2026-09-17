import SwiftUI

private let homiraBackground = Color(red: 10/255, green: 14/255, blue: 19/255)
private let homiraSurface = Color(red: 17/255, green: 24/255, blue: 33/255)
private let homiraSurfaceRaised = Color(red: 24/255, green: 33/255, blue: 44/255)
private let homiraText = Color(red: 244/255, green: 247/255, blue: 250/255)
private let homiraMuted = Color(red: 149/255, green: 162/255, blue: 178/255)
private let homiraGreen = Color(red: 37/255, green: 212/255, blue: 122/255)
private let homiraBlue = Color(red: 114/255, green: 169/255, blue: 1)
private let homiraPink = Color(red: 1, green: 141/255, blue: 181/255)
private let homiraDanger = Color(red: 1, green: 95/255, blue: 109/255)

private struct PersonCard: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let marker: String
    let accent: Color
}

private let mimi = PersonCard(name: "MiMi", marker: "✿", accent: homiraPink)
private let hex = PersonCard(name: "Hex", marker: "⚡", accent: homiraBlue)

private struct RecentCall: Identifiable {
    let id = UUID()
    let person: PersonCard
    let whenText: String
    let duration: String
    let incoming: Bool
    let video: Bool
    let missed: Bool
}

private let recentCalls = [
    RecentCall(person: mimi, whenText: "Today, 04:31", duration: "28m", incoming: true, video: false, missed: false),
    RecentCall(person: hex, whenText: "Yesterday, 22:18", duration: "1h 12m", incoming: false, video: true, missed: false),
    RecentCall(person: mimi, whenText: "Monday, 19:46", duration: "Missed", incoming: true, video: true, missed: true),
    RecentCall(person: hex, whenText: "Sunday, 13:03", duration: "46m", incoming: true, video: false, missed: false)
]

struct RootView: View {
    @State private var selectedTab = 0
    @State private var activePerson: PersonCard?
    @State private var activeVideo = false

    var body: some View {
        ZStack {
            homiraBackground.ignoresSafeArea()

            if let person = activePerson {
                ActiveCallView(
                    person: person,
                    startsWithVideo: activeVideo,
                    onEnd: { activePerson = nil }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.985)))
            } else {
                TabView(selection: $selectedTab) {
                    CallsView(onCall: startCall)
                        .tabItem { Label("Calls", systemImage: "phone.fill") }
                        .tag(0)

                    PeopleView(onCall: startCall)
                        .tabItem { Label("People", systemImage: "person.2.fill") }
                        .tag(1)

                    PrivacyView()
                        .tabItem { Label("You", systemImage: "person.crop.circle.fill") }
                        .tag(2)
                }
                .tint(homiraGreen)
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.22), value: activePerson)
    }

    private func startCall(_ person: PersonCard, video: Bool) {
        activeVideo = video
        activePerson = person
    }
}

private struct CallsView: View {
    let onCall: (PersonCard, Bool) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Homira")
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                            .foregroundStyle(homiraText)
                        Text("Closer, without the noise.")
                            .font(.subheadline)
                            .foregroundStyle(homiraMuted)
                    }
                    Spacer()
                    HStack(spacing: 6) {
                        Image(systemName: "lock.fill")
                        Text("Private")
                            .font(.caption.weight(.semibold))
                    }
                    .foregroundStyle(homiraGreen)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(homiraGreen.opacity(0.10), in: Capsule())
                }

                Text("Quick call")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(homiraMuted)

                HStack(spacing: 14) {
                    QuickCard(person: mimi, onCall: onCall)
                    QuickCard(person: hex, onCall: onCall)
                }

                HStack {
                    Text("Recent")
                        .font(.title3.bold())
                        .foregroundStyle(homiraText)
                    Spacer()
                    Button("See all") { }
                        .foregroundStyle(homiraMuted)
                }

                VStack(spacing: 4) {
                    ForEach(recentCalls) { call in
                        RecentCallRow(call: call) {
                            onCall(call.person, call.video)
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)
            .padding(.bottom, 28)
        }
        .background(homiraBackground)
    }
}

private struct QuickCard: View {
    let person: PersonCard
    let onCall: (PersonCard, Bool) -> Void
    @State private var pulse = false

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            MascotMarker(person: person, size: 72)
            VStack(alignment: .leading, spacing: 3) {
                Text(person.name)
                    .font(.title3.bold())
                    .foregroundStyle(homiraText)
                Text("Available")
                    .font(.caption)
                    .foregroundStyle(homiraMuted)
            }
            HStack(spacing: 10) {
                SmallCallButton(icon: "phone.fill", accent: person.accent) {
                    onCall(person, false)
                }
                SmallCallButton(icon: "video.fill", accent: person.accent) {
                    onCall(person, true)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(homiraSurface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .scaleEffect(pulse ? 1.012 : 0.988)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.9).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
    }
}

private struct MascotMarker: View {
    let person: PersonCard
    let size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(person.accent.opacity(0.12))
            Text(person.marker)
                .font(.system(size: size * 0.42, weight: .bold, design: .rounded))
                .foregroundStyle(person.accent)
        }
        .frame(width: size, height: size)
    }
}

private struct SmallCallButton: View {
    let icon: String
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 43, height: 43)
                .background(accent.opacity(0.13), in: Circle())
        }
        .buttonStyle(.plain)
    }
}

private struct RecentCallRow: View {
    let call: RecentCall
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                MascotMarker(person: call.person, size: 50)
                VStack(alignment: .leading, spacing: 4) {
                    Text(call.person.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(call.missed ? homiraDanger : homiraText)
                    HStack(spacing: 5) {
                        Image(systemName: call.incoming ? "arrow.down.left" : "arrow.up.right")
                        Text("\(call.whenText)  •  \(call.duration)")
                    }
                    .font(.caption)
                    .foregroundStyle(call.missed ? homiraDanger : homiraMuted)
                }
                Spacer()
                Image(systemName: call.video ? "video.fill" : "phone.fill")
                    .foregroundStyle(homiraMuted)
            }
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }
}

private struct PeopleView: View {
    let onCall: (PersonCard, Bool) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("People")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(homiraText)
                Text("Your small circle.")
                    .foregroundStyle(homiraMuted)

                ForEach([mimi, hex]) { person in
                    HStack(spacing: 14) {
                        MascotMarker(person: person, size: 56)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(person.name)
                                .font(.headline)
                                .foregroundStyle(homiraText)
                            Text("Available for a call")
                                .font(.caption)
                                .foregroundStyle(homiraMuted)
                        }
                        Spacer()
                        SmallCallButton(icon: "phone.fill", accent: person.accent) {
                            onCall(person, false)
                        }
                        SmallCallButton(icon: "video.fill", accent: person.accent) {
                            onCall(person, true)
                        }
                    }
                    .padding(16)
                    .background(homiraSurface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                }
            }
            .padding(20)
        }
        .background(homiraBackground)
    }
}

private struct PrivacyView: View {
    private let rows: [(String, String, String)] = [
        ("shield.fill", "Call content stays private", "Voice, video and screen sharing are not designed to be stored in Supabase."),
        ("dot.radiowaves.left.and.right", "Direct when possible", "1-to-1 calls will prefer a direct WebRTC path. TURN is only the fallback."),
        ("clock.arrow.circlepath", "Local call history", "Detailed call history is planned to live on your device."),
        ("lock.shield.fill", "Minimal metadata", "The backend will keep only what the call setup actually needs.")
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("You")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(homiraText)
                Text("Privacy first, by default.")
                    .foregroundStyle(homiraMuted)

                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(alignment: .top, spacing: 13) {
                        Image(systemName: row.0)
                            .foregroundStyle(homiraGreen)
                            .frame(width: 42, height: 42)
                            .background(homiraGreen.opacity(0.10), in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Text(row.1)
                                .font(.body.weight(.semibold))
                                .foregroundStyle(homiraText)
                            Text(row.2)
                                .font(.caption)
                                .foregroundStyle(homiraMuted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(16)
                    .background(homiraSurface, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                }
            }
            .padding(20)
        }
        .background(homiraBackground)
    }
}

private struct ActiveCallView: View {
    let person: PersonCard
    let startsWithVideo: Bool
    let onEnd: () -> Void

    @State private var muted = false
    @State private var speaker = false
    @State private var video = false
    @State private var sharing = false
    @State private var connected = false
    @State private var seconds = 0
    @State private var pulse = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onEnd) {
                    Image(systemName: "chevron.left")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(homiraText)
                        .frame(width: 44, height: 44)
                }
                Spacer()
                Button(action: {}) {
                    Image(systemName: "ellipsis")
                        .foregroundStyle(homiraMuted)
                        .frame(width: 44, height: 44)
                }
            }

            VStack(spacing: 4) {
                Text(person.name)
                    .font(.system(size: 30, weight: .bold, design: .rounded))
                    .foregroundStyle(homiraText)
                Text(connected ? formatDuration(seconds) : "Connecting…")
                    .foregroundStyle(connected ? homiraMuted : person.accent)
            }

            Spacer()

            VStack(spacing: 24) {
                ZStack {
                    Circle()
                        .fill(person.accent.opacity(0.16))
                        .frame(width: 210, height: 210)
                        .scaleEffect(pulse ? 1.14 : 1.0)
                        .opacity(connected ? 0.28 : 0.14)
                    Circle()
                        .fill(person.accent.opacity(0.12))
                        .frame(width: 184, height: 184)
                    VStack(spacing: 4) {
                        Text(person.marker)
                            .font(.system(size: 64, weight: .bold, design: .rounded))
                            .foregroundStyle(person.accent)
                        Text("\(person.name) artwork slot")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(person.accent.opacity(0.9))
                    }
                }

                VoiceBars(accent: person.accent)
            }

            Spacer()

            if sharing {
                Label("Screen sharing preview", systemImage: "rectangle.on.rectangle")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(homiraText)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(homiraSurfaceRaised, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .transition(.opacity.combined(with: .scale(scale: 0.96)))
                    .padding(.bottom, 20)
            }

            HStack {
                CallControl(icon: "mic.slash.fill", label: "Mute", active: $muted)
                Spacer()
                CallControl(icon: "speaker.wave.2.fill", label: "Speaker", active: $speaker)
                Spacer()
                CallControl(icon: "video.fill", label: "Video", active: $video)
                Spacer()
                CallControl(icon: "rectangle.on.rectangle", label: "Share", active: $sharing)
            }

            Button(action: onEnd) {
                Image(systemName: "phone.down.fill")
                    .font(.system(size: 27, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 70, height: 70)
                    .background(homiraDanger, in: Circle())
            }
            .buttonStyle(.plain)
            .padding(.top, 28)
            .padding(.bottom, 24)
        }
        .padding(.horizontal, 20)
        .background(homiraBackground.ignoresSafeArea())
        .onAppear {
            video = startsWithVideo
            withAnimation(.easeInOut(duration: 1.35).repeatForever(autoreverses: true)) {
                pulse = true
            }
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(650))
                connected = true
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(1))
                    seconds += 1
                }
            }
        }
        .animation(.easeInOut(duration: 0.18), value: sharing)
    }

    private func formatDuration(_ seconds: Int) -> String {
        String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}

private struct VoiceBars: View {
    let accent: Color
    @State private var animate = false

    var body: some View {
        HStack(alignment: .center, spacing: 5) {
            ForEach(0..<5, id: \.self) { index in
                Capsule()
                    .fill(accent)
                    .frame(width: 5, height: animate ? heightsA[index] : heightsB[index])
            }
        }
        .frame(height: 36)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.65).repeatForever(autoreverses: true)) {
                animate = true
            }
        }
    }

    private let heightsA: [CGFloat] = [30, 14, 24, 12, 30]
    private let heightsB: [CGFloat] = [12, 31, 18, 30, 13]
}

private struct CallControl: View {
    let icon: String
    let label: String
    @Binding var active: Bool

    var body: some View {
        Button {
            active.toggle()
        } label: {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(active ? homiraBackground : homiraText)
                    .frame(width: 55, height: 55)
                    .background(active ? homiraText : homiraSurfaceRaised, in: Circle())
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(homiraMuted)
            }
        }
        .buttonStyle(.plain)
    }
}
