import SwiftUI

@main
struct HomiraApp: App {
    var body: some Scene {
        WindowGroup {
            ProductionRootView()
                .preferredColorScheme(.dark)
        }
    }
}
