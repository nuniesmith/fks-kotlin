import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var authViewModel = AuthViewModelWrapper()
    
    var body: some View {
        NavigationStack {
            if authViewModel.isAuthenticated {
                MainTabView()
            } else {
                LoginView(viewModel: authViewModel)
            }
        }
        .environmentObject(authViewModel)
    }
}

// MARK: - Auth ViewModel Wrapper
class AuthViewModelWrapper: ObservableObject {
    @Published var isAuthenticated = false
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private let repository = AuthRepository()
    
    func login(username: String, password: String) async {
        await MainActor.run { isLoading = true }
        
        do {
            // Use KMP shared code for authentication
            try await repository.login(username: username, password: password)
            await MainActor.run {
                isAuthenticated = true
                isLoading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
                isLoading = false
            }
        }
    }
    
    func logout() async {
        do {
            try await repository.logout()
            await MainActor.run { isAuthenticated = false }
        } catch {
            await MainActor.run { errorMessage = error.localizedDescription }
        }
    }
}

// MARK: - Login View
struct LoginView: View {
    @ObservedObject var viewModel: AuthViewModelWrapper
    @State private var username = ""
    @State private var password = ""
    
    var body: some View {
        VStack(spacing: 24) {
            // Logo
            Image(systemName: "chart.line.uptrend.xyaxis.circle.fill")
                .resizable()
                .frame(width: 100, height: 100)
                .foregroundStyle(.blue)
            
            Text("FKS Trading")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            Text("Sign in to continue")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            
            // Login Form
            VStack(spacing: 16) {
                TextField("Username", text: $username)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .textContentType(.username)
                
                SecureField("Password", text: $password)
                    .textFieldStyle(.roundedBorder)
                    .textContentType(.password)
                
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
                
                Button(action: {
                    Task {
                        await viewModel.login(username: username, password: password)
                    }
                }) {
                    if viewModel.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        Text("Sign In")
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(username.isEmpty || password.isEmpty || viewModel.isLoading)
            }
            .padding(.horizontal, 32)
            
            Spacer()
        }
        .padding(.top, 60)
    }
}

// MARK: - Main Tab View
struct MainTabView: View {
    @EnvironmentObject var authViewModel: AuthViewModelWrapper
    
    var body: some View {
        TabView {
            SignalMatrixView()
                .tabItem {
                    Label("Signals", systemImage: "waveform.badge.magnifyingglass")
                }
            
            PortfolioView()
                .tabItem {
                    Label("Portfolio", systemImage: "chart.pie.fill")
                }
            
            EvaluationView()
                .tabItem {
                    Label("Evaluation", systemImage: "checkmark.seal.fill")
                }
            
            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
        }
    }
}

// MARK: - Signal Matrix View
struct SignalMatrixView: View {
    @State private var signals: [Signal] = []
    @State private var isLoading = true
    
    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    ProgressView()
                } else {
                    List(signals, id: \.id) { signal in
                        SignalRow(signal: signal)
                    }
                    .refreshable {
                        await loadSignals()
                    }
                }
            }
            .navigationTitle("Signal Matrix")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(action: { Task { await loadSignals() } }) {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .task {
            await loadSignals()
        }
    }
    
    func loadSignals() async {
        isLoading = true
        // Load from KMP shared repository
        // let repo = SignalRepository()
        // signals = try? await repo.getSignals() ?? []
        isLoading = false
    }
}

// MARK: - Signal Row
struct SignalRow: View {
    let signal: Signal
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(signal.symbol)
                    .font(.headline)
                Spacer()
                signalBadge
            }
            
            HStack {
                Label("\(signal.strength, specifier: "%.1f")%", systemImage: "gauge")
                Spacer()
                Text(signal.timeframe)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
    
    @ViewBuilder
    var signalBadge: some View {
        let (color, text) = signalInfo
        Text(text)
            .font(.caption)
            .fontWeight(.semibold)
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color, in: Capsule())
    }
    
    var signalInfo: (Color, String) {
        switch signal.type {
        case .buy: return (.green, "BUY")
        case .sell: return (.red, "SELL")
        case .hold: return (.orange, "HOLD")
        }
    }
}

// MARK: - Portfolio View
struct PortfolioView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Portfolio Summary Card
                    VStack(spacing: 12) {
                        Text("Portfolio Value")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        Text("$125,432.50")
                            .font(.system(size: 36, weight: .bold))
                        HStack {
                            Image(systemName: "arrow.up.right")
                            Text("+2.34%")
                        }
                        .foregroundStyle(.green)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    
                    // Holdings Section
                    LazyVStack(spacing: 12) {
                        ForEach(0..<5) { _ in
                            HoldingRow()
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Portfolio")
        }
    }
}

struct HoldingRow: View {
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("AAPL")
                    .font(.headline)
                Text("Apple Inc.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing) {
                Text("$187.50")
                    .font(.headline)
                Text("+1.2%")
                    .font(.caption)
                    .foregroundStyle(.green)
            }
        }
        .padding()
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Evaluation View
struct EvaluationView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Performance Metrics
                    MetricCard(title: "Win Rate", value: "67.5%", icon: "target")
                    MetricCard(title: "Sharpe Ratio", value: "1.85", icon: "chart.bar.fill")
                    MetricCard(title: "Max Drawdown", value: "-12.3%", icon: "arrow.down.right")
                    MetricCard(title: "Profit Factor", value: "2.1", icon: "dollarsign.circle.fill")
                }
                .padding()
            }
            .navigationTitle("Evaluation")
        }
    }
}

struct MetricCard: View {
    let title: String
    let value: String
    let icon: String
    
    var body: some View {
        HStack {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(.blue)
                .frame(width: 44)
            
            VStack(alignment: .leading) {
                Text(title)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.title2)
                    .fontWeight(.semibold)
            }
            
            Spacer()
        }
        .padding()
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Settings View
struct SettingsView: View {
    @EnvironmentObject var authViewModel: AuthViewModelWrapper
    
    var body: some View {
        NavigationStack {
            List {
                Section("Account") {
                    NavigationLink(destination: Text("Profile")) {
                        Label("Profile", systemImage: "person.circle")
                    }
                    NavigationLink(destination: Text("Notifications")) {
                        Label("Notifications", systemImage: "bell")
                    }
                }
                
                Section("Preferences") {
                    NavigationLink(destination: Text("Appearance")) {
                        Label("Appearance", systemImage: "paintbrush")
                    }
                    NavigationLink(destination: Text("Data & Privacy")) {
                        Label("Data & Privacy", systemImage: "hand.raised")
                    }
                }
                
                Section {
                    Button(role: .destructive) {
                        Task { await authViewModel.logout() }
                    } label: {
                        Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("Settings")
        }
    }
}

// MARK: - Preview Models (for development)
struct Signal: Identifiable {
    let id: String
    let symbol: String
    let type: SignalType
    let strength: Double
    let timeframe: String
}

enum SignalType {
    case buy, sell, hold
}

#Preview {
    ContentView()
}
