import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ZStack {
            // Status bar background color (#2D2F91)
            Color(red: 45/255, green: 47/255, blue: 145/255)
                .ignoresSafeArea(.all, edges: .top)

            ComposeView()
                .ignoresSafeArea(.all, edges: .bottom) // Keep it filling the bottom
        }
        // Force dark color scheme for this view to make system icons (clock, battery) white
        .preferredColorScheme(.dark)
    }
}



