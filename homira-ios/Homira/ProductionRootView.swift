import SwiftUI
import PhotosUI
import UIKit

private let pBackground = Color(red: 10/255, green: 14/255, blue: 19/255)
private let pSurface = Color(red: 17/255, green: 24/255, blue: 33/255)
private let pSurfaceRaised = Color(red: 24/255, green: 33/255, blue: 44/255)
private let pLine = Color(red: 36/255, green: 48/255, blue: 61/255)
private let pText = Color(red: 244/255, green: 247/255, blue: 250/255)
private let pMuted = Color(red: 149/255, green: 162/255, blue: 178/255)
private let pGreen = Color(red: 37/255, green: 212/255, blue: 122/255)
private let pBlue = Color(red: 114/255, green: 169/255, blue: 1)
private let pPink = Color(red: 1, green: 141/255, blue: 181/255)
private let pDanger = Color(red: 1, green: 95/255, blue: 109/255)

private enum PTab: Int {
    case keypad
    case recents
    case contacts
    case me
}

private struct PPerson: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let marker: String
    let accent: Color
    let number: String
}

private struct PRecentCall: Identifiable {
    let id = UUID()
    let person: PPerson
    let whenText: String
    let duration: String
    let incoming: Bool
    let video: Bool
    let missed: Bool
}

private let pMimi = PPerson(name: "MiMi", marker: "✿", accent: pPink, number: "+234 803 124 5678")
private let pHex = PPerson(name: "Hex", marker: "⚡", accent: pBlue, number: "+234 806 734 2011")
private let pAda = PPerson(name: "Ada", marker: "A", accent: pGreen, number: "+234 802 440 1812")
private let pTobi = PPerson(name: "Tobi", marker: "T", accent: Color(red: 1, green: 196/255, blue: 107/255), number: "+234 809 220 4300")
private let pPeople = [pMimi, pHex, pAda, pTobi]

private let pRecents = [
    PRecentCall(person: pMimi, whenText: "Today, 04:31", duration: "28m", incoming: true, video: false, missed: false),
    PRecentCall(person: pHex, whenText: "Yesterday, 22:18", duration: "1h 12m", incoming: false, video: true, missed: false),
    PRecentCall(person: pMimi, whenText: "Monday, 19:46", duration: "Missed", incoming: true, video: true, missed: true),
    PRecentCall(person: pAda, whenText: "Sunday, 13:03", duration: "46m", incoming: true, video: false, missed: false),
    PRecentCall(person: pTobi, whenText: "Saturday, 21:11", duration: "9m", incoming: false, video: false, missed: false)
]

struct ProductionRootView: View {
    @State private var selectedTab: PTab = .keypad
    @State private var showSettings = false
    @State private var showProfileEditor = false

    @State private var activePerson: PPerson?
    @State private var callVideo = false
    @State private var showCall = false
    @State private var callMuted = false
    @State private var callSpeaker = false
    @State private var callSharing = false
    @State private var callStartedAt = Date()

    @State private var profileName = "Dawson"
    @State private var profileUsername = "dawson"
    @State private var profileAbout = "Available after 6"
    @State private var profileEmail = "dawson@example.com"
    @State private var profilePhone = "+234 ••• ••••"
    @State private var avatarData: Data?
    @State private var callCardData: Data?

    var body: some View {
        ZStack {
            pBackground.ignoresSafeArea()

            if let person = activePerson, showCall {
                PActiveCallView(
                    person: person,
                    startedAt: callStartedAt,
                    video: $callVideo,
                    muted: $callMuted,
                    speaker: $callSpeaker,
                    sharing: $callSharing,
                    onMinimize: { showCall = false },
                    onEnd: endCall
                )
                .transition(.opacity.combined(with: .scale(scale: 0.985)))
            } else {
                TabView(selection: $selectedTab) {
                    PKeypadView(
                        onSettings: { showSettings = true },
                        onDial: dialNumber
                    )
                    .tabItem { Label("Keypad", systemImage: "circle.grid.3x3.fill") }
                    .tag(PTab.keypad)

                    PRecentsView(
                        onSettings: { showSettings = true },
                        onCall: startCall
                    )
                    .tabItem { Label("Recents", systemImage: "clock.fill") }
                    .tag(PTab.recents)

                    PContactsView(
                        onSettings: { showSettings = true },
                        onCall: startCall
                    )
                    .tabItem { Label("Contacts", systemImage: "person.crop.circle.badge.checkmark") }
                    .tag(PTab.contacts)

                    PMeView(
                        name: profileName,
                        username: profileUsername,
                        about: profileAbout,
                        email: profileEmail,
                        phone: profilePhone,
                        avatarData: avatarData,
                        callCardData: callCardData,
                        onEdit: { showProfileEditor = true },
                        onSettings: { showSettings = true }
                    )
                    .tabItem { Label("Me", systemImage: "person.crop.circle.fill") }
                    .tag(PTab.me)
                }
                .tint(pGreen)
                .overlay(alignment: .top) {
                    if let person = activePerson {
                        POngoingCallBanner(
                            person: person,
                            startedAt: callStartedAt,
                            muted: callMuted,
                            onReturn: { showCall = true },
                            onEnd: endCall
                        )
                        .padding(.horizontal, 14)
                        .padding(.top, 8)
                    }
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: showCall)
        .sheet(isPresented: $showSettings) {
            PSettingsView()
                .preferredColorScheme(.dark)
        }
        .sheet(isPresented: $showProfileEditor) {
            PProfileEditor(
                name: $profileName,
                username: $profileUsername,
                about: $profileAbout,
                email: $profileEmail,
                avatarData: $avatarData,
                callCardData: $callCardData
            )
            .preferredColorScheme(.dark)
        }
    }

    private func startCall(_ person: PPerson, video: Bool) {
        activePerson = person
        callVideo = video
        callMuted = false
        callSpeaker = video
        callSharing = false
        callStartedAt = Date()
        showCall = true
    }

    private func dialNumber(_ number: String) {
        let digits = pDigits(number)
        let matched = pPeople.first { person in
            let personDigits = pDigits(person.number)
            return digits.count >= 7 && personDigits.hasSuffix(String(digits.suffix(10)))
        }
        let person = matched ?? PPerson(
            name: pFormatNumber(number),
            marker: "#",
            accent: pGreen,
            number: number
        )
        startCall(person, video: false)
    }

    private func endCall() {
        activePerson = nil
        showCall = false
        callSharing = false
    }
}

private struct PMainHeader: View {
    let title: String
    let onSettings: () -> Void

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 34, weight: .bold, design: .rounded))
                .foregroundStyle(pText)
            Spacer()
            Menu {
                Button(action: onSettings) {
                    Label("Settings", systemImage: "gearshape")
                }
                Button {} label: {
                    Label("Blocked people", systemImage: "person.crop.circle.badge.xmark")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(pText)
                    .frame(width: 44, height: 44)
            }
        }
    }
}

private struct PKeypadView: View {
    let onSettings: () -> Void
    let onDial: (String) -> Void
    @State private var number = ""

    private let rows: [[(String, String)]] = [
        [("1", ""), ("2", "ABC"), ("3", "DEF")],
        [("4", "GHI"), ("5", "JKL"), ("6", "MNO")],
        [("7", "PQRS"), ("8", "TUV"), ("9", "WXYZ")],
        [("*", ""), ("0", "+"), ("#", "")]
    ]

    private var matchedPerson: PPerson? {
        let digits = pDigits(number)
        guard digits.count >= 7 else { return nil }
        return pPeople.first { pDigits($0.number).hasSuffix(String(digits.suffix(10))) }
    }

    var body: some View {
        VStack(spacing: 0) {
            PMainHeader(title: "Phone", onSettings: onSettings)

            Spacer()

            VStack(spacing: 9) {
                Text(number.isEmpty ? "Enter number" : pFormatNumber(number))
                    .font(.system(size: number.isEmpty ? 20 : 30, weight: number.isEmpty ? .regular : .semibold, design: .rounded))
                    .foregroundStyle(number.isEmpty ? pMuted : pText)
                    .lineLimit(1)
                    .minimumScaleFactor(0.65)

                if let matchedPerson {
                    HStack(spacing: 8) {
                        PAvatar(person: matchedPerson, size: 28)
                        Text("\(matchedPerson.name) · Homira")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(matchedPerson.accent)
                    }
                } else if pDigits(number).count >= 7 {
                    Text("Not in your Homira contacts")
                        .font(.caption)
                        .foregroundStyle(pMuted)
                }
            }

            Spacer().frame(height: 28)

            VStack(spacing: 13) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack {
                        ForEach(Array(row.enumerated()), id: \.offset) { index, key in
                            Button {
                                if number.count < 22 {
                                    number.append(key.0)
                                }
                            } label: {
                                VStack(spacing: 1) {
                                    Text(key.0)
                                        .font(.system(size: 28, weight: .medium, design: .rounded))
                                    if !key.1.isEmpty {
                                        Text(key.1)
                                            .font(.system(size: 9, weight: .semibold, design: .rounded))
                                            .tracking(1.15)
                                    }
                                }
                                .foregroundStyle(pText)
                                .frame(width: 74, height: 74)
                                .background(pSurface, in: Circle())
                            }
                            .buttonStyle(.plain)

                            if index < row.count - 1 { Spacer() }
                        }
                    }
                }
            }

            Spacer().frame(height: 24)

            HStack(spacing: 34) {
                Color.clear.frame(width: 58, height: 58)

                Button {
                    if !number.isEmpty { onDial(number) }
                } label: {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(number.isEmpty ? pMuted : Color.black)
                        .frame(width: 70, height: 70)
                        .background(number.isEmpty ? pSurfaceRaised : pGreen, in: Circle())
                }
                .disabled(number.isEmpty)
                .buttonStyle(.plain)

                Button {
                    if !number.isEmpty { number.removeLast() }
                } label: {
                    Image(systemName: "delete.left")
                        .font(.title3)
                        .foregroundStyle(number.isEmpty ? Color.clear : pText)
                        .frame(width: 58, height: 58)
                }
                .disabled(number.isEmpty)
                .buttonStyle(.plain)
            }

            Spacer()
        }
        .padding(.horizontal, 22)
        .padding(.top, 16)
        .padding(.bottom, 18)
        .background(pBackground)
    }
}

private struct PRecentsView: View {
    let onSettings: () -> Void
    let onCall: (PPerson, Bool) -> Void
    @State private var missedOnly = false

    private var shownCalls: [PRecentCall] {
        missedOnly ? pRecents.filter(\.missed) : pRecents
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                PMainHeader(title: "Recents", onSettings: onSettings)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Quick call")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(pMuted)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            ForEach(pPeople) { person in
                                PQuickCallCard(person: person, onCall: onCall)
                                    .frame(width: 168)
                            }
                        }
                    }
                }

                Picker("Calls", selection: $missedOnly) {
                    Text("All").tag(false)
                    Text("Missed").tag(true)
                }
                .pickerStyle(.segmented)

                VStack(spacing: 3) {
                    ForEach(shownCalls) { call in
                        PRecentRow(call: call) {
                            onCall(call.person, call.video)
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 28)
        }
        .background(pBackground)
    }
}

private struct PContactsView: View {
    let onSettings: () -> Void
    let onCall: (PPerson, Bool) -> Void
    @State private var query = ""

    private var filtered: [PPerson] {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return pPeople }
        return pPeople.filter { $0.name.localizedCaseInsensitiveContains(text) || $0.number.contains(text) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                PMainHeader(title: "Contacts", onSettings: onSettings)

                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(pMuted)
                    TextField("Search contacts", text: $query)
                        .textInputAutocapitalization(.never)
                        .foregroundStyle(pText)
                }
                .padding(.horizontal, 14)
                .frame(height: 48)
                .background(pSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))

                VStack(spacing: 5) {
                    ForEach(filtered) { person in
                        HStack(spacing: 13) {
                            PAvatar(person: person, size: 54)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(person.name)
                                    .font(.headline)
                                    .foregroundStyle(pText)
                                Text(person.number)
                                    .font(.caption)
                                    .foregroundStyle(pMuted)
                            }
                            Spacer()
                            PSmallCallButton(icon: "phone.fill", accent: person.accent) {
                                onCall(person, false)
                            }
                            PSmallCallButton(icon: "video.fill", accent: person.accent) {
                                onCall(person, true)
                            }
                        }
                        .padding(.vertical, 9)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 28)
        }
        .background(pBackground)
    }
}

private struct PMeView: View {
    let name: String
    let username: String
    let about: String
    let email: String
    let phone: String
    let avatarData: Data?
    let callCardData: Data?
    let onEdit: () -> Void
    let onSettings: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                PMainHeader(title: "Me", onSettings: onSettings)

                VStack(spacing: 0) {
                    ZStack(alignment: .bottomLeading) {
                        Group {
                            if let image = pImage(callCardData) {
                                Image(uiImage: image)
                                    .resizable()
                                    .scaledToFill()
                            } else {
                                ZStack {
                                    LinearGradient(
                                        colors: [pSurfaceRaised, pGreen.opacity(0.12)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                    VStack(spacing: 8) {
                                        Image(systemName: "photo.on.rectangle.angled")
                                            .font(.title2)
                                        Text("Add a call card")
                                            .font(.caption.weight(.medium))
                                    }
                                    .foregroundStyle(pMuted)
                                }
                            }
                        }
                        .frame(height: 205)
                        .frame(maxWidth: .infinity)
                        .clipped()

                        PProfileAvatar(data: avatarData, fallback: name, size: 94)
                            .overlay(Circle().stroke(pBackground, lineWidth: 4))
                            .offset(x: 18, y: 47)
                    }

                    VStack(alignment: .leading, spacing: 7) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(name)
                                    .font(.system(size: 27, weight: .bold, design: .rounded))
                                    .foregroundStyle(pText)
                                Text("@\(username)")
                                    .font(.subheadline)
                                    .foregroundStyle(pMuted)
                            }
                            Spacer()
                            Button(action: onEdit) {
                                Label("Edit", systemImage: "pencil")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(pText)
                                    .padding(.horizontal, 13)
                                    .frame(height: 38)
                                    .background(pSurfaceRaised, in: Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                        Text(about)
                            .font(.subheadline)
                            .foregroundStyle(pText.opacity(0.84))
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 58)
                    .padding(.bottom, 18)
                }
                .background(pSurface, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
                .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))

                PSectionTitle("Profile")
                PInfoRow(icon: "phone.fill", title: "Phone number", value: phone)
                PInfoRow(icon: "at", title: "Username", value: "@\(username)")
                PInfoRow(icon: "envelope.fill", title: "Email", value: email)
                PInfoRow(icon: "text.quote", title: "About", value: about)

                PSectionTitle("Account")
                PActionRow(icon: "person.badge.key.fill", title: "Account details") {}
                PActionRow(icon: "lock.shield.fill", title: "Security") {}
                PActionRow(icon: "rectangle.portrait.and.arrow.right", title: "Log out", destructive: true) {}
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 34)
        }
        .background(pBackground)
    }
}

private struct PProfileEditor: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var name: String
    @Binding var username: String
    @Binding var about: String
    @Binding var email: String
    @Binding var avatarData: Data?
    @Binding var callCardData: Data?

    @State private var draftName = ""
    @State private var draftUsername = ""
    @State private var draftAbout = ""
    @State private var draftEmail = ""
    @State private var draftAvatar: Data?
    @State private var draftCallCard: Data?
    @State private var avatarItem: PhotosPickerItem?
    @State private var callCardItem: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 18) {
                    ZStack(alignment: .bottomLeading) {
                        PhotosPicker(selection: $callCardItem, matching: .images) {
                            Group {
                                if let image = pImage(draftCallCard) {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFill()
                                } else {
                                    ZStack {
                                        pSurfaceRaised
                                        VStack(spacing: 7) {
                                            Image(systemName: "photo.badge.plus")
                                                .font(.title2)
                                            Text("Choose call card image")
                                                .font(.caption)
                                        }
                                        .foregroundStyle(pMuted)
                                    }
                                }
                            }
                            .frame(height: 190)
                            .frame(maxWidth: .infinity)
                            .clipped()
                        }
                        .buttonStyle(.plain)

                        PhotosPicker(selection: $avatarItem, matching: .images) {
                            ZStack(alignment: .bottomTrailing) {
                                PProfileAvatar(data: draftAvatar, fallback: draftName, size: 88)
                                Image(systemName: "camera.fill")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(Color.black)
                                    .frame(width: 29, height: 29)
                                    .background(pGreen, in: Circle())
                            }
                        }
                        .buttonStyle(.plain)
                        .offset(x: 18, y: 43)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))

                    VStack(spacing: 14) {
                        PEditorField(title: "Name", text: $draftName)
                        PEditorField(title: "Username", text: $draftUsername)
                        PEditorField(title: "About", text: $draftAbout, multiline: true)
                        PEditorField(title: "Email", text: $draftEmail, keyboard: .emailAddress)
                    }
                    .padding(.top, 44)
                }
                .padding(20)
            }
            .background(pBackground)
            .navigationTitle("Edit profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        name = draftName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? name : draftName
                        username = draftUsername
                            .replacingOccurrences(of: " ", with: "")
                            .lowercased()
                        about = draftAbout
                        email = draftEmail
                        avatarData = draftAvatar
                        callCardData = draftCallCard
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
            .onAppear {
                draftName = name
                draftUsername = username
                draftAbout = about
                draftEmail = email
                draftAvatar = avatarData
                draftCallCard = callCardData
            }
            .onChange(of: avatarItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self) {
                        await MainActor.run { draftAvatar = data }
                    }
                }
            }
            .onChange(of: callCardItem) { _, item in
                Task {
                    if let data = try? await item?.loadTransferable(type: Data.self) {
                        await MainActor.run { draftCallCard = data }
                    }
                }
            }
        }
    }
}

private struct PEditorField: View {
    let title: String
    @Binding var text: String
    var multiline = false
    var keyboard: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(pMuted)
            if multiline {
                TextField(title, text: $text, axis: .vertical)
                    .lineLimit(2...4)
                    .foregroundStyle(pText)
                    .padding(13)
                    .background(pSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            } else {
                TextField(title, text: $text)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(title == "Username" || title == "Email" ? .never : .words)
                    .foregroundStyle(pText)
                    .padding(13)
                    .background(pSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
    }
}

private struct PSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var lowData = false
    @State private var protectIP = false
    @State private var notifications = true
    @State private var haptics = true
    @State private var appearance = 0

    var body: some View {
        NavigationStack {
            List {
                Section("Calls") {
                    Toggle(isOn: $lowData) {
                        Label("Use less data for calls", systemImage: "gauge.with.dots.needle.33percent")
                    }
                    NavigationLink {
                        PSettingDetail(title: "Call quality", bodyText: "Automatic adapts voice and video quality to your connection. Low data will prefer lower bitrates and protect voice first.")
                    } label: {
                        Label("Call quality", systemImage: "waveform.path")
                    }
                    NavigationLink {
                        PSettingDetail(title: "Ringtone", bodyText: "Ringtone selection will be connected to Homira's incoming-call notification layer when calling is wired up.")
                    } label: {
                        Label("Ringtone", systemImage: "speaker.wave.2.fill")
                    }
                }

                Section("Privacy") {
                    Toggle(isOn: $protectIP) {
                        VStack(alignment: .leading, spacing: 3) {
                            Label("Protect IP in calls", systemImage: "lock.shield.fill")
                            Text("Force the relay path instead of exposing peer IP addresses.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    NavigationLink {
                        PSettingDetail(title: "Blocked people", bodyText: "People you block won't be able to call you through Homira.")
                    } label: {
                        Label("Blocked people", systemImage: "person.crop.circle.badge.xmark")
                    }
                    NavigationLink {
                        PSettingDetail(title: "Account security", bodyText: "PIN, recovery and session security will live here once authentication is connected.")
                    } label: {
                        Label("Account security", systemImage: "key.fill")
                    }
                }

                Section("Notifications") {
                    Toggle("Incoming and missed calls", isOn: $notifications)
                    Toggle("Call haptics", isOn: $haptics)
                }

                Section("Appearance") {
                    Picker("Appearance", selection: $appearance) {
                        Text("System").tag(0)
                        Text("Dark").tag(1)
                    }
                }

                Section {
                    HStack {
                        Spacer()
                        Text("Homira · Native call client")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(pBackground)
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct PSettingDetail: View {
    let title: String
    let bodyText: String

    var body: some View {
        ScrollView {
            Text(bodyText)
                .foregroundStyle(pText)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
        }
        .background(pBackground)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct PActiveCallView: View {
    let person: PPerson
    let startedAt: Date
    @Binding var video: Bool
    @Binding var muted: Bool
    @Binding var speaker: Bool
    @Binding var sharing: Bool
    let onMinimize: () -> Void
    let onEnd: () -> Void

    @State private var showMore = false
    @State private var controlsVisible = true
    @State private var frontCamera = true

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            ZStack {
                (video ? Color.black : pBackground).ignoresSafeArea()

                if video {
                    ZStack {
                        person.accent.opacity(0.10).ignoresSafeArea()
                        VStack(spacing: 16) {
                            PAvatar(person: person, size: 160)
                            Text("Video preview")
                                .font(.caption)
                                .foregroundStyle(pMuted)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture { withAnimation { controlsVisible.toggle() } }
                }

                VStack(spacing: 0) {
                    if controlsVisible || !video {
                        HStack {
                            Button(action: onMinimize) {
                                Image(systemName: "chevron.down")
                                    .font(.title3.weight(.semibold))
                                    .foregroundStyle(pText)
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
                                Button {} label: {
                                    Label("Call info", systemImage: "info.circle")
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .foregroundStyle(pText)
                                    .frame(width: 44, height: 44)
                            }
                        }
                        .transition(.opacity)
                    }

                    if !video {
                        VStack(spacing: 7) {
                            Text(person.name)
                                .font(.system(size: 30, weight: .bold, design: .rounded))
                                .foregroundStyle(pText)
                            Text(pDuration(from: startedAt, to: context.date))
                                .foregroundStyle(pMuted)
                            Spacer().frame(height: 36)
                            PAvatar(person: person, size: 184)
                        }
                        .padding(.top, 4)
                    } else {
                        Spacer()
                        if controlsVisible {
                            VStack(spacing: 4) {
                                Text(person.name)
                                    .font(.title3.bold())
                                Text(pDuration(from: startedAt, to: context.date))
                                    .font(.caption)
                            }
                            .foregroundStyle(pText)
                            .shadow(radius: 8)
                        }
                    }

                    Spacer()

                    if muted && (controlsVisible || !video) {
                        Label("You're muted", systemImage: "mic.slash.fill")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(pText)
                            .padding(.horizontal, 13)
                            .frame(height: 34)
                            .background(pSurfaceRaised.opacity(video ? 0.88 : 1), in: Capsule())
                            .padding(.bottom, 12)
                    }

                    if sharing && (controlsVisible || !video) {
                        Label("Sharing your screen", systemImage: "rectangle.on.rectangle")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(pGreen)
                            .padding(.bottom, 12)
                    }

                    if controlsVisible || !video {
                        VStack(spacing: 25) {
                            HStack {
                                PCallToggle(icon: "mic.slash.fill", label: "Mute", active: $muted)
                                Spacer()
                                PCallToggle(icon: "speaker.wave.2.fill", label: "Speaker", active: $speaker)
                                Spacer()
                                PCallToggle(icon: "video.fill", label: "Video", active: $video)
                                Spacer()
                                Button {
                                    frontCamera.toggle()
                                } label: {
                                    VStack(spacing: 7) {
                                        Image(systemName: "arrow.triangle.2.circlepath.camera.fill")
                                            .font(.system(size: 20, weight: .semibold))
                                            .foregroundStyle(pText)
                                            .frame(width: 55, height: 55)
                                            .background(pSurfaceRaised.opacity(video ? 0.88 : 1), in: Circle())
                                        Text("Flip")
                                            .font(.caption2)
                                            .foregroundStyle(pMuted)
                                    }
                                }
                                .buttonStyle(.plain)
                            }

                            Button(action: onEnd) {
                                Image(systemName: "phone.down.fill")
                                    .font(.system(size: 27, weight: .bold))
                                    .foregroundStyle(.white)
                                    .frame(width: 70, height: 70)
                                    .background(pDanger, in: Circle())
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.bottom, 24)
                        .transition(.opacity)
                    }
                }
                .padding(.horizontal, 20)
            }
            .onChange(of: video) { _, newValue in
                if newValue {
                    controlsVisible = true
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(3))
                        if video { withAnimation { controlsVisible = false } }
                    }
                } else {
                    controlsVisible = true
                }
            }
            .onAppear {
                if video {
                    Task { @MainActor in
                        try? await Task.sleep(for: .seconds(3))
                        if video { withAnimation { controlsVisible = false } }
                    }
                }
            }
        }
    }
}

private struct PCallToggle: View {
    let icon: String
    let label: String
    @Binding var active: Bool

    var body: some View {
        Button { active.toggle() } label: {
            VStack(spacing: 7) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(active ? pBackground : pText)
                    .frame(width: 55, height: 55)
                    .background(active ? pText : pSurfaceRaised, in: Circle())
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(pMuted)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct POngoingCallBanner: View {
    let person: PPerson
    let startedAt: Date
    let muted: Bool
    let onReturn: () -> Void
    let onEnd: () -> Void

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            HStack(spacing: 11) {
                Button(action: onReturn) {
                    HStack(spacing: 11) {
                        PAvatar(person: person, size: 38)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(person.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(pText)
                            HStack(spacing: 5) {
                                if muted { Image(systemName: "mic.slash.fill") }
                                Text(pDuration(from: startedAt, to: context.date))
                            }
                            .font(.caption2)
                            .foregroundStyle(pMuted)
                        }
                    }
                }
                .buttonStyle(.plain)
                Spacer()
                Button(action: onEnd) {
                    Image(systemName: "phone.down.fill")
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(pDanger, in: Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(10)
            .background(pSurfaceRaised.opacity(0.98), in: RoundedRectangle(cornerRadius: 19, style: .continuous))
        }
    }
}

private struct PQuickCallCard: View {
    let person: PPerson
    let onCall: (PPerson, Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 13) {
            PAvatar(person: person, size: 62)
            Text(person.name)
                .font(.headline)
                .foregroundStyle(pText)
            Text("Homira")
                .font(.caption)
                .foregroundStyle(pMuted)
            HStack(spacing: 9) {
                PSmallCallButton(icon: "phone.fill", accent: person.accent) { onCall(person, false) }
                PSmallCallButton(icon: "video.fill", accent: person.accent) { onCall(person, true) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(15)
        .background(pSurface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct PRecentRow: View {
    let call: PRecentCall
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                PAvatar(person: call.person, size: 50)
                VStack(alignment: .leading, spacing: 4) {
                    Text(call.person.name)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(call.missed ? pDanger : pText)
                    HStack(spacing: 5) {
                        Image(systemName: call.incoming ? "arrow.down.left" : "arrow.up.right")
                        Text("\(call.whenText) · \(call.duration)")
                    }
                    .font(.caption)
                    .foregroundStyle(call.missed ? pDanger : pMuted)
                }
                Spacer()
                Image(systemName: call.video ? "video.fill" : "phone.fill")
                    .foregroundStyle(pMuted)
            }
            .padding(.vertical, 9)
        }
        .buttonStyle(.plain)
    }
}

private struct PAvatar: View {
    let person: PPerson
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

private struct PProfileAvatar: View {
    let data: Data?
    let fallback: String
    let size: CGFloat

    var body: some View {
        Group {
            if let image = pImage(data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    Circle().fill(pGreen.opacity(0.14))
                    Text(String(fallback.prefix(1)).uppercased())
                        .font(.system(size: size * 0.38, weight: .bold, design: .rounded))
                        .foregroundStyle(pGreen)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
    }
}

private struct PSmallCallButton: View {
    let icon: String
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(accent)
                .frame(width: 42, height: 42)
                .background(accent.opacity(0.13), in: Circle())
        }
        .buttonStyle(.plain)
    }
}

private struct PSectionTitle: View {
    let title: String
    init(_ title: String) { self.title = title }

    var body: some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(pMuted)
            .textCase(.uppercase)
            .tracking(0.7)
            .padding(.top, 2)
    }
}

private struct PInfoRow: View {
    let icon: String
    let title: String
    let value: String

    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: icon)
                .foregroundStyle(pMuted)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(pText)
                Text(value)
                    .font(.caption)
                    .foregroundStyle(pMuted)
                    .lineLimit(2)
            }
            Spacer()
        }
        .padding(.vertical, 9)
    }
}

private struct PActionRow: View {
    let icon: String
    let title: String
    var destructive = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                Image(systemName: icon)
                    .frame(width: 22)
                Text(title)
                    .font(.subheadline.weight(.medium))
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(pMuted)
            }
            .foregroundStyle(destructive ? pDanger : pText)
            .padding(.vertical, 11)
        }
        .buttonStyle(.plain)
    }
}

private func pImage(_ data: Data?) -> UIImage? {
    guard let data else { return nil }
    return UIImage(data: data)
}

private func pDigits(_ value: String) -> String {
    value.filter(\.isNumber)
}

private func pFormatNumber(_ value: String) -> String {
    let digits = pDigits(value)
    switch digits.count {
    case 0...4:
        return digits
    case 5...7:
        return "\(digits.prefix(4)) \(digits.dropFirst(4))"
    case 8...11:
        return "\(digits.prefix(4)) \(digits.dropFirst(4).prefix(3)) \(digits.dropFirst(7))"
    default:
        return value
    }
}

private func pDuration(from start: Date, to end: Date) -> String {
    let total = max(0, Int(end.timeIntervalSince(start)))
    return String(format: "%02d:%02d", total / 60, total % 60)
}
