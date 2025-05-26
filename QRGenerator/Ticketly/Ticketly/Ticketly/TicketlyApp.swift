//
//  TicketlyApp.swift
//  Ticketly
//
//  Created by Alumno on 21/05/25.
//

import SwiftUI
import FirebaseCore

@main
struct TicketlyApp: App {
    
    init() {
        FirebaseApp.configure()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
