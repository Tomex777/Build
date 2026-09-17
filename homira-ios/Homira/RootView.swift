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
    let subtitle: String
}

private let mimi = PersonCard(name: "MiMi", marker: "✿", accent: homiraPink, subtitle: "Available")
private let hex = PersonCard(name: "Hex", marker: "⚡", accent: homiraBlue, subtitle: "Available")
private let quickPeople = [mimi, hex]

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
    @State private var selectedTab = 1
    @State private var activePerson: PersonCard?
    @State private var showCall = false
    @State private var callMuted = false
    @State private var callSpeaker = false
    @State private var callVideo = false
    @State private var callSharing = false
    @State private var callStartedAt = Date()
    @State private var displayName = "You"
    @State private var signedIn = true

    var body: some View {
        ZStack {
            homiraBackground.ignoresSafeArea()

            if let person = activePerson, showCall {
                ActiveCallView(
                    person: person,
                    startedAt: callStartedAt,
                    muted: $callMuted,
                    speaker: $callSpeaker,
                    video: $callVideo,
                    sharing: $callSharing,
                    onMinimize: { showCall = false },
                    onEnd: endCall
                )
                .transition(.opacity.combined(with: .scale(scale: 0.985)))
            } else {
                TabView(selection: $selectedTab) {
                    KeypadView(onDial: dialNumber)
                        .tabItem { Label("Keypad", systemImage: "circle.grid.3x3.fill") }
                        .tag(0)

                    CallsView(onCall: startCall)
                        .tabItem { Label("Recents", systemImage: "clock.fill") }
                        .tag(1)

                    PeopleView(onCall: startCall)
                        .tabItem { Label("People", systemImage: "person.2.fill") }
                        .tag(2)

                    YouView(displayName: $displayName, signedIn: $signedIn)
                        .tabItem { Label("You", systemImage: "person.crop.circle.fill") }
                        .tag(3)
                }
                .tint(homiraGreen)
                .overlay(alignment: .top) {
                    if let person = activePerson {
                        OngoingCallBanner(
                            person: person,
                            startedAt: callStartedAt,
                            muted: callMuted,
                            action: { showCall = true }
                        )
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                    }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showCall)
    }

    private func startCall(_ person: PersonCard, video: Bool) {
        activePerson = person
        callMuted = false
        callSpeaker = video
        callVideo = video
        callSharing = false
        callStartedAt = Date()
        showCall = true
    }

    private func dialNumber(_ number: String) {
        let person = PersonCard(name: number, marker: "•", accent: homiraGreen, subtitle: "Phone number")
        startCall(person, video: false)
    }

    private func endCall() {
        activePerson = nil
        showCall = false
        callSharing = false
    }
}

private struct CallsView: View {
    let onCall: (PersonCard, Bool) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Calls")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(homiraText)

                Text("Quick call")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(homiraMuted)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(quickPeople) { person in
                            QuickCard(person: person, onCall: onCall)
                                .frame(width: 176)
                        }
                    }
                }
                .contentMargins(.horizontal, 0, for: .scrollContent)

                Text("Recent")
                    .font(.title3.bold())
                    .foregroundStyle(homiraText)
                    .padding(.top, 2)

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

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            MascotMarker(person: person, size: 66)
            VStack(alignment: .leading, spacing: 3) {
                Text(person.name)
                    .font(.title3.bold())
                    .foregroundStyle(homiraText)
                Text(person.subtitle)
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
        .background(homiraSurface, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    }
}

private struct KeypadView: View {
    let onDial: (String) -> Void
    @State private var number = ""

    private let rows = [
        ["1", "2", "3"],
        ["4", "5", "6"],
        ["7", "8", "9"],
        ["*", "0", "#"]
    ]

    var body: some View {
        VStack(spacing: 0) {
            Text("Keypad")
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(homiraText)
                .frame(maxWidth: .infinity, alignment: .leading)

            Spacer()

            HStack(spacing: 8) {
                Text(number.isEmpty ? "Enter a number" : number)
                    .font(.system(size: number.isEmpty ? 20 : 29, weight: .semibold, design: .rounded))
                    .foregroundStyle(number.isEmpty ? homiraMuted : homiraText)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Button {
                    if !number.isEmpty { number.removeLast() }
                } label: {
                    Image(systemName: "delete.left")
                        .foregroundStyle(number.isEmpty ? homiraSurfaceRaised : homiraMuted)
                        .frame(width: 44, height: 44)
                }
                .disabled(number.isEmpty)
                .buttonStyle(.plain)
            }

            Spacer().frame(height: 28)

            ForEach(rows, id: \.self) { row in
                HStack {
                    ForEach(row, id: \.self) { digit in
                        Button {
                            if number.count < 22 { number.append(digit) }
                        } label: {
                            Text(digit)
                                .font(.system(size: 28, weight: .medium, design: .rounded))
                                .foregroundStyle(homiraText)
                                .frame(width: 72, height: 72)
                                .background(homiraSurface, in: Circle())
                        }
                        .buttonStyle(.plain)
                        if digit != row.last { Spacer() }
                    }
                }
                Spacer().frame(height: 14)
            }

            Spacer().frame(height: 8)

            Button {
                if !number.isEmpty { onDial(number) }
            } label: {
                Image(systemName: "phone.fill")
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(number.isEmpty ? homiraMuted : Color.black)
                    .frame(width: 72, height: 72)
                    .background(number.isEmpty ? homiraSurfaceRaised : homiraGreen, in: Circle())
            }
            .disabled(number.isEmpty)
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.horizontal, 24)
        .padding(.top, 18)
        .padding(.bottom, 22)
        .background(homiraBackground)
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
            .padding(.vertical, 9)
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

                ForEach(quickPeople) { person in
                    HStack(spacing: 14) {
                        MascotMarker(person: person, size: 56)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(person.name)
                                .font(.headline)
                                .foregroundStyle(homiraText)
                            Text(person.subtitle)
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

private struct YouView: View {
    @Binding var displayName: String
    @Binding var signedIn: Bool
    @State private var showEdit = false
    @State private var showLogout = false
    @State private var detailTitle: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("You")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(homiraText)

                HStack(spacing: 14) {
                    Image(systemName: "person.fill")
                        .font(.system(size: 27, weight: .semibold))
                        .foregroundStyle(homiraGreen)
                        .frame(width: 64, height: 64)
                        .background(homiraGreen.opacity(0.13), in: Circle())
                    VStack(alignment: .leading, spacing: 4) {
                        Text(displayName)
                            .font(.title3.bold())
                            .foregroundStyle(homiraText)
                        Text(signedIn ? "Signed in" : "Not signed in")
                            .font(.caption)
                            .foregroundStyle(homiraMuted)
                    }
                    Spacer()
                    if signedIn {
                        Button { showEdit = true } label: {
                            Image(systemName: "pencil")
                                .foregroundStyle(homiraMuted)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(18)
                .background(homiraSurface, in: RoundedRectangle(cornerRadius: 26, style: .continuous))

                VStack(spacing: 0) {
                    SettingsRow(icon: "person.fill", title: "Profile") { showEdit = true }
                    divider
                    SettingsRow(icon: "lock.fill", title: "Account") { detailTitle = "Account" }
                    divider
                    SettingsRow(icon: "hand.raised.fill", title: "Privacy") { detailTitle = "Privacy" }
                    divider
                    SettingsRow(icon: "bell.fill", title: "Notifications") { detailTitle = "Notifications" }
                    divider
                    SettingsRow(icon: "laptopcomputer.and.iphone", title: "Linked devices") { detailTitle = "Linked devices" }
                    divider
                    SettingsRow(icon: "phone.fill", title: "Call settings") { detailTitle = "Call settings" }
                }
                .background(homiraSurface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))

                Button {
                    if signedIn { showLogout = true } else { signedIn = true }
                } label: {
                    Label(signedIn ? "Log out" : "Log in", systemImage: signedIn ? "rectangle.portrait.and.arrow.right" : "person.crop.circle.badge.checkmark")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(signedIn ? homiraDanger : homiraGreen)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
        .background(homiraBackground)
        .sheet(isPresented: $showEdit) {
            EditProfileSheet(name: $displayName)
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
        .alert(detailTitle ?? "", isPresented: Binding(
            get: { detailTitle != nil },
            set: { if !$0 { detailTitle = nil } }
        )) {
            Button("Done", role: .cancel) { detailTitle = nil }
        } message: {
            Text("This section is ready for its real account and settings wiring.")
        }
        .confirmationDialog("Log out?", isPresented: $showLogout, titleVisibility: .visible) {
            Button("Log out", role: .destructive) { signedIn = false }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("You can sign back in from this screen.")
        }
    }

    private var divider: some View {
        Divider().overlay(homiraSurfaceRaised).padding(.leading, 50)
    }
}

private struct EditProfileSheet: View {
    @Binding var name: String
    @Environment(\.dismiss) private var dismiss
    @State private var draft = ""

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 18) {
                Text("Display name")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(homiraMuted)
                TextField("Display name", text: $draft)
                    .textFieldStyle(.roundedBorder)
                Spacer()
            }
            .padding(20)
            .navigationTitle("Edit profile")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !trimmed.isEmpty { name = String(trimmed.prefix(40)) }
                        dismiss()
                    }
                }
            }
            .onAppear { draft = name }
        }
    }
}

private struct SettingsRow: View {
    let icon: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                Image(systemName: icon)
                    .foregroundStyle(homiraMuted)
                    .frame(width: 21)
                Text(title)
                    .foregroundStyle(homiraText)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(homiraMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 15)
        }
        .buttonStyle(.plain)
    }
}

private struct OngoingCallBanner: View {
    let person: PersonCard
    let startedAt: Date
    let muted: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            TimelineView(.periodic(from: .now, by: 1)) { context in
                HStack(spacing: 10) {
                    MascotMarker(person: person, size: 38)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(person.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(homiraText)
                        Text(formatDuration(elapsed(at: context.date)))
                            .font(.caption2)
                            .foregroundStyle(homiraMuted)
                    }
                    Spacer()
                    if muted {
                        Image(systemName: "mic.slash.fill")
                            .foregroundStyle(homiraDanger)
                    }
                    Text("Return")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(homiraGreen)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(homiraSurfaceRaised, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
        }
        .buttonStyle(.plain)
    }

    private func elapsed(at date: Date) -> Int {
        max(0, Int(date.timeIntervalSince(startedAt)))
    }
}

private struct ActiveCallView: View {
    let person: PersonCard
    let startedAt: Date
    @Binding var muted: Bool
    @Binding var speaker: Bool
    @Binding var video: Bool
    @Binding var sharing: Bool
    let onMinimize: () -> Void
    let onEnd: () -> Void

    @State private var controlsVisible = true
    @State private var showInfo = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let elapsed = max(0, Int(context.date.timeIntervalSince(startedAt)))
            let connected = elapsed >= 1

            VStack(spacing: 0) {
                if !video || controlsVisible {
                    HStack {
                        Button(action: onMinimize) {
                            Image(systemName: "chevron.down")
                                .font(.title3.weight(.semibold))
                                .foregroundStyle(homiraText)
                                .frame(width: 44, height: 44)
                        }
                        .buttonStyle(.plain)

                        Spacer()

                        Menu {
                            Button {
                                sharing.toggle()
                            } label: {
                                Label(sharing ? "Stop sharing screen" : "Share screen", systemImage: "rectangle.on.rectangle")
                            }
                            Button {
                                showInfo = true
                            } label: {
                                Label("Call info", systemImage: "info.circle")
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .foregroundStyle(homiraText)
                                .frame(width: 44, height: 44)
                        }
                    }
                    .transition(.opacity)
                }

                if !video || controlsVisible {
                    VStack(spacing: 4) {
                        Text(person.name)
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                            .foregroundStyle(homiraText)
                        Text(connected ? formatDuration(elapsed) : "Connecting…")
                            .foregroundStyle(connected ? homiraMuted : person.accent)
                    }
                    .transition(.opacity)
                }

                VStack(spacing: 10) {
                    if muted {
                        Label("You’re muted", systemImage: "mic.slash.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(homiraDanger)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(homiraDanger.opacity(0.14), in: Capsule())
                    }
                    if sharing {
                        Label("Sharing your screen", systemImage: "rectangle.on.rectangle")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(homiraGreen)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(homiraGreen.opacity(0.12), in: Capsule())
                    }
                }
                .padding(.top, 10)

                Spacer()

                ZStack {
                    Circle()
                        .fill(person.accent.opacity(video ? 0.18 : 0.12))
                        .frame(width: video ? 250 : 205, height: video ? 250 : 205)
                    VStack(spacing: 4) {
                        Text(person.marker)
                            .font(.system(size: 68, weight: .bold, design: .rounded))
                            .foregroundStyle(person.accent)
                        Text("\(person.name) artwork slot")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(person.accent.opacity(0.9))
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    if video {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            controlsVisible.toggle()
                        }
                    }
                }

                Spacer()

                if !video || controlsVisible {
                    VStack(spacing: 26) {
                        HStack {
                            CallControl(
                                icon: "mic.slash.fill",
                                label: muted ? "Unmute" : "Mute",
                                active: muted,
                                action: { muted.toggle() }
                            )
                            Spacer()
                            CallControl(
                                icon: "speaker.wave.2.fill",
                                label: "Speaker",
                                active: speaker,
                                action: { speaker.toggle() }
                            )
                            Spacer()
                            CallControl(
                                icon: "video.fill",
                                label: video ? "Video off" : "Video",
                                active: video,
                                action: {
                                    video.toggle()
                                    controlsVisible = true
                                    scheduleVideoControlsHide()
                                }
                            )
                        }

                        Button(action: onEnd) {
                            Image(systemName: "phone.down.fill")
                                .font(.system(size: 27, weight: .bold))
                                .foregroundStyle(.white)
                                .frame(width: 70, height: 70)
                                .background(homiraDanger, in: Circle())
                        }
                        .buttonStyle(.plain)
                    }
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
                    .padding(.bottom, 18)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .background((video ? Color.black : homiraBackground).ignoresSafeArea())
        }
        .onAppear {
            if video { scheduleVideoControlsHide() }
        }
        .alert("Call info", isPresented: $showInfo) {
            Button("Done", role: .cancel) { }
        } message: {
            Text("\(person.name)\n\(video ? "Video call" : "Voice call")\n\(muted ? "Microphone muted\n" : "")\(sharing ? "Screen sharing on" : "")")
        }
    }

    private func scheduleVideoControlsHide() {
        guard video else { return }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(3.5))
            guard video else { return }
            withAnimation(.easeInOut(duration: 0.18)) {
                controlsVisible = false
            }
        }
    }
}

private struct CallControl: View {
    let icon: String
    let label: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(active ? homiraBackground : homiraText)
                    .frame(width: 58, height: 58)
                    .background(active ? homiraText : homiraSurfaceRaised, in: Circle())
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(homiraMuted)
            }
        }
        .buttonStyle(.plain)
    }
}

private func formatDuration(_ seconds: Int) -> String {
    String(format: "%02d:%02d", seconds / 60, seconds % 60)
}
