# WhatsApp Clone - Android Application

A feature-rich WhatsApp clone built with **Jetpack Compose** and **Kotlin**, featuring real-time messaging, AI chat integration, phone authentication, and a modern Material Design 3 UI. This project demonstrates modern Android development practices including MVVM architecture, dependency injection, and reactive programming.

## 📱 Project Overview

This is a fully functional WhatsApp clone application that replicates the core features of WhatsApp with additional AI-powered chat capabilities. The app provides a seamless messaging experience with Firebase backend integration, phone number authentication, and an integrated AI assistant powered by Groq's fast language models.

## ✨ Key Features

### 🔐 Authentication & User Management
- **Phone Number Authentication**: Secure OTP-based phone number verification using Firebase Authentication
- **User Registration**: Complete user profile setup with name and profile picture
- **Session Management**: Persistent login sessions with automatic authentication state handling
- **User Search**: Search and add contacts by phone number

### 💬 Messaging Features
- **Real-time Chat**: Instant messaging with Firebase Realtime Database
- **Message History**: Persistent message storage with automatic synchronization
- **Chat List**: Dynamic chat list with last message preview and timestamps
- **Message Bubbles**: WhatsApp-style message bubbles with sent/received indicators
- **Message Deletion**: Long-press to delete messages with confirmation dialog
- **Emoji Support**: Built-in emoji picker for expressive messaging
- **Typing Indicators**: Visual feedback for message status
- **Message Timestamps**: Relative time display (Just now, 5m ago, 2h ago, etc.)

### 🤖 AI Chat Integration
- **Meta AI Assistant**: Integrated AI chat powered by Groq's fast language models
- **Conversational AI**: Context-aware conversations with conversation history
- **Typing Indicators**: Real-time typing indicators during AI responses
- **Welcome Messages**: Friendly greeting messages for new AI conversations
- **In-Memory Storage**: AI chat messages stored in memory (resets on navigation)
- **Fast Responses**: Leverages Groq's ultra-fast inference for instant AI responses

### 📞 Call Management
- **Call History**: View recent calls with call type indicators
- **Favorites Section**: Quick access to favorite contacts
- **Call Actions**: Start new calls functionality (UI ready for implementation)

### 📢 Updates/Status
- **Status Updates**: View and manage status updates
- **Channel Items**: Display status channels and updates
- **Status Management**: Create and view status updates

### 👥 Communities
- **Community Management**: Create and manage communities
- **Community Items**: Display community information and members

### ⚙️ Settings & Customization
- **Comprehensive Settings**: Full settings menu with multiple categories
  - Account Settings
  - Privacy Settings
  - Chat Settings
  - Notification Settings
  - Storage Settings
  - Help & Support
  - Invite Friends
  - Account Center
- **Profile Management**: Edit user profile with name and profile picture
- **Theme Support**: Material Design 3 theming system

### 🎨 UI/UX Features
- **Material Design 3**: Modern Material Design 3 components and theming
- **WhatsApp-like UI**: Authentic WhatsApp design with mint green color scheme
- **Smooth Animations**: Fluid transitions and animations throughout the app
- **Responsive Layout**: Adaptive layouts for different screen sizes
- **Bottom Navigation**: Easy navigation between main sections
- **Search Functionality**: Real-time search across chats and contacts

## 🏗️ Architecture

### MVVM (Model-View-ViewModel) Pattern
The app follows the **MVVM architecture pattern** for clean separation of concerns:

- **Model**: Data classes and business logic (`models/`, `data/`)
- **View**: Jetpack Compose UI components (`presentation/`)
- **ViewModel**: State management and business logic (`viewmodels/`)

### Dependency Injection
- **Hilt (Dagger)**: Used for dependency injection throughout the app
- **Modular Structure**: Clean separation with dependency injection modules

### State Management
- **StateFlow**: Reactive state management for UI updates
- **LiveData**: Used where appropriate for lifecycle-aware data
- **Compose State**: Local UI state management with `remember` and `mutableStateOf`

## 🛠️ Technologies & Libraries

### Core Technologies
- **Kotlin**: 100% Kotlin codebase
- **Jetpack Compose**: Modern declarative UI framework
- **Material Design 3**: Latest Material Design components
- **Android SDK**: Minimum SDK 28, Target SDK 34, Compile SDK 36

### Architecture Components
- **Navigation Compose**: Type-safe navigation between screens
- **ViewModel**: Lifecycle-aware state management
- **LiveData & StateFlow**: Reactive data streams
- **Hilt**: Dependency injection framework

### Backend & Services
- **Firebase Authentication**: Phone number OTP authentication
- **Firebase Realtime Database**: Real-time messaging and data synchronization
- **Groq API**: Fast AI language model integration for AI chat

### Networking
- **OkHttp**: HTTP client for API calls
- **Kotlinx Serialization**: JSON serialization/deserialization
- **Coroutines**: Asynchronous programming and API calls

### Image Loading
- **Coil**: Modern image loading library for profile pictures and images

### Additional Libraries
- **Material Icons Extended**: Comprehensive icon set
- **Kotlinx Coroutines**: Concurrency and asynchronous operations

## 📁 Project Structure

```
app/src/main/java/com/example/whatsapp/
│
├── data/
│   └── ai/
│       └── GroqApiService.kt          # Groq API integration service
│
├── di/
│   ├── AppModule.kt                    # Hilt dependency injection modules
│   └── FirebaseModule.kt               # Firebase configuration
│
├── models/
│   ├── Message.kt                     # Message data model
│   └── PhoneAuthUser.kt                # User authentication model
│
├── presentation/
│   ├── ai/
│   │   ├── AiChatScreen.kt            # AI chat screen UI
│   │   └── AiChatViewModel.kt         # AI chat state management
│   │
│   ├── bottomnavigation/
│   │   └── BottomNavigation.kt        # Bottom navigation bar
│   │
│   ├── callscreen/
│   │   ├── CallScreen.kt              # Calls screen
│   │   ├── CallItemsDesign.kt         # Call item UI components
│   │   └── FavoritesSection.kt        # Favorites section
│   │
│   ├── chat_box/
│   │   ├── ChatListBox.kt             # Chat list item component
│   │   └── ChatListModel.kt           # Chat list data model
│   │
│   ├── chatscreen/
│   │   └── ChatScreen.kt              # Individual chat screen
│   │
│   ├── communitiesscreen/
│   │   ├── CommunitiesScreen.kt       # Communities screen
│   │   └── CommunitiesItemsDesign.kt  # Community item components
│   │
│   ├── homescreen/
│   │   ├── HomeScreen.kt              # Main home/chats screen
│   │   ├── ChatDesign.kt              # Chat design components
│   │   └── ChatDesignModel.kt         # Chat design models
│   │
│   ├── navigation/
│   │   ├── Routes.kt                  # Navigation routes definition
│   │   └── WhatsAppNavigtionSystem.kt # Navigation setup
│   │
│   ├── profile/
│   │   └── UserProfileSetScreen.kt    # User profile setup/edit
│   │
│   ├── settings/
│   │   ├── SettingsScreen.kt          # Main settings screen
│   │   └── SubSettingsScreen.kt       # Sub-settings screens
│   │
│   ├── splashscreen/
│   │   └── splashscreen.kt            # Splash screen
│   │
│   ├── updatescreen/
│   │   ├── UpdateScreen.kt            # Status/Updates screen
│   │   ├── StatusItem.kt              # Status item components
│   │   └── ChannelItemDesign.kt       # Channel design
│   │
│   ├── userregistrationscreen/
│   │   └── UserRegistrationScreen.kt  # User registration/OTP
│   │
│   ├── viewmodels/
│   │   ├── BaseViewModel.kt            # Main view model for chats
│   │   ├── PhoneAuthViewModel.kt      # Phone authentication logic
│   │   └── ThemeViewModel.kt          # Theme management
│   │
│   └── welcomescreen/
│       └── WelcomeScreen.kt           # Welcome/landing screen
│
├── ui/
│   └── theme/
│       ├── Color.kt                   # App color definitions
│       ├── Theme.kt                   # Material theme setup
│       └── Type.kt                     # Typography definitions
│
├── MainActivity.kt                     # Main activity entry point
└── WhatsAppCloneApplication.kt        # Application class with Hilt
```

## 🚀 Setup & Installation

### Prerequisites
- **Android Studio**: Hedgehog (2023.1.1) or later
- **JDK**: 11 or higher
- **Android SDK**: API 28 (Android 9.0) or higher
- **Gradle**: 8.13.1 or compatible version

### Step 1: Clone the Repository
```bash
git clone https://github.com/Muhammad-bilal-503/WhatsApp-Clone.git
cd whatsapp-clone
```

### Step 2: Firebase Setup
1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Enable **Firebase Authentication** with **Phone Number** provider
3. Enable **Firebase Realtime Database**
4. Download `google-services.json` and place it in `app/` directory
5. Update Firebase database rules (see Firebase Configuration section)

### Step 3: Groq API Setup (for AI Chat)
1. Sign up at [Groq Console](https://console.groq.com/)
2. Create an API key from the API Keys section
3. Open `app/src/main/java/com/example/whatsapp/data/ai/GroqApiService.kt`
4. Replace `YOUR_GROQ_API_KEY_HERE` with your actual API key:
```kotlin
private const val GROQ_API_KEY = "your-actual-api-key-here"
```

### Step 4: Build and Run
1. Open the project in Android Studio
2. Sync Gradle files
3. Connect an Android device or start an emulator (API 28+)
4. Click **Run** or press `Shift + F10`

## ⚙️ Configuration

### Firebase Database Rules
Configure your Firebase Realtime Database with these rules:

```json
{
  "rules": {
    "users": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid",
        "chats": {
          ".read": "$userId === auth.uid",
          ".write": "$userId === auth.uid"
        }
      }
    },
    "user_messages": {
      "$userId": {
        ".read": "$userId === auth.uid",
        ".write": "$userId === auth.uid"
      }
    },
    "chats": {
      ".read": "auth != null",
      ".write": "auth != null"
    }
  }
}
```

### Groq API Models
The app uses `llama-3.1-8b-instant` by default. You can change the model in `GroqApiService.kt`:

```kotlin
private const val MODEL = "llama-3.1-8b-instant"  // Fast, free
// Other options:
// "llama-3.1-70b-versatile"  // More capable
// "mixtral-8x7b-32768"        // Excellent quality
```

## 📱 Screens & Navigation

### Navigation Flow
```
SplashScreen
    ↓
WelcomeScreen
    ↓
UserRegistrationScreen (OTP Verification)
    ↓
UserProfileSetScreen (Profile Setup)
    ↓
HomeScreen (Main App)
    ├── ChatScreen (Individual Chat)
    ├── AiChatScreen (AI Assistant)
    ├── UpdateScreen (Status/Updates)
    ├── CommunitiesScreen
    ├── CallScreen
    └── SettingsScreen
        ├── AccountSettingsScreen
        ├── PrivacySettingsScreen
        ├── ChatsSettingsScreen
        ├── NotificationsSettingsScreen
        ├── StorageSettingsScreen
        ├── HelpSettingsScreen
        ├── InviteSettingsScreen
        └── AccountCenterSettingsScreen
```

### Main Screens

#### 1. **Splash Screen**
- Initial loading screen
- Checks authentication state
- Routes to Welcome or Home screen

#### 2. **Welcome Screen**
- Landing page with app branding
- "Get Started" button
- Routes to registration

#### 3. **User Registration Screen**
- Phone number input
- OTP verification via Firebase
- Phone number authentication flow

#### 4. **User Profile Setup Screen**
- Name input
- Profile picture selection
- Initial profile configuration

#### 5. **Home Screen (Chats)**
- Main chat list interface
- Search functionality
- Floating Action Button for adding contacts
- AI Chat FAB (Meta AI button)
- Bottom navigation bar
- Menu with options (New Group, Settings, Profile, Logout)

#### 6. **Chat Screen**
- Individual conversation interface
- Message bubbles (sent/received)
- Message input with emoji picker
- Attachment and camera buttons
- Message deletion (long-press)
- Security banner
- Auto-scroll to latest message

#### 7. **AI Chat Screen**
- Meta AI assistant interface
- Welcome message
- Typing indicators
- Conversation history (in-memory)
- Same UI as regular chat
- Resets on navigation

#### 8. **Update Screen (Status)**
- Status updates display
- Channel items
- Status management

#### 9. **Communities Screen**
- Community list
- Community management

#### 10. **Call Screen**
- Recent calls list
- Favorites section
- Call history

#### 11. **Settings Screen**
- Comprehensive settings menu
- Multiple setting categories
- Profile editing
- App configuration

## 🔑 Key Components

### ViewModels

#### BaseViewModel
- Manages chat list and messages
- Handles Firebase database operations
- User search functionality
- Message sending and receiving
- Chat management

#### PhoneAuthViewModel
- Phone number authentication
- OTP verification
- Session management

#### AiChatViewModel
- AI chat state management
- Message history (in-memory)
- Typing indicators
- Groq API integration

### Services

#### GroqApiService
- Groq API integration
- HTTP request handling
- JSON serialization
- Error handling
- Conversation context management

### Models

#### Message
```kotlin
data class Message(
    val senderPhoneNumber: String,
    val message: String,
    val timestamp: Long
)
```

#### ChatListModel
```kotlin
data class ChatListModel(
    val name: String?,
    val phoneNumber: String?,
    val userId: String?,
    val time: String?,
    val message: String?,
    val profileImage: String?,
    val image: Bitmap?
)
```

## 🔄 Data Flow

### Messaging Flow
1. User sends message → `ChatScreen`
2. Message added to local state
3. `BaseViewModel.sendMessage()` called
4. Message saved to Firebase Realtime Database
5. Firebase listener updates UI in real-time
6. Message appears in chat for both users

### AI Chat Flow
1. User sends message → `AiChatScreen`
2. Message added to in-memory state
3. `AiChatViewModel.sendMessage()` called
4. Request sent to Groq API via `GroqApiService`
5. Typing indicator shown
6. AI response received and displayed
7. Conversation history maintained in memory

### Authentication Flow
1. User enters phone number
2. OTP sent via Firebase Auth
3. User verifies OTP
4. Firebase creates/authenticates user
5. User profile setup
6. Navigate to Home screen

## 🎨 UI/UX Design

### Color Scheme
- **Primary Green**: `#25D366` (WhatsApp green)
- **Light Green**: `#DCF8C6` (Message bubbles)
- **Mint Green**: `#ECE5DD` (Background)
- **Material Design 3**: Full Material 3 theming

### Components
- **Message Bubbles**: Rounded corners, proper alignment
- **Input Bar**: WhatsApp-style input with emoji, attachment, camera, mic buttons
- **Top App Bar**: Consistent header with back navigation
- **Bottom Navigation**: 4-tab navigation (Chats, Updates, Communities, Calls)
- **Floating Action Buttons**: Add contact and AI chat buttons

## 🔒 Security & Privacy

- **Firebase Authentication**: Secure phone number verification
- **End-to-End Encryption Notice**: Security banner in chats
- **User Data Isolation**: Users can only access their own data
- **Secure API Keys**: API keys stored in code (consider using BuildConfig for production)

## 🧪 Testing

### Manual Testing Checklist
- [ ] Phone number authentication
- [ ] OTP verification
- [ ] User registration
- [ ] Chat creation
- [ ] Message sending/receiving
- [ ] Message deletion
- [ ] AI chat functionality
- [ ] Navigation between screens
- [ ] Settings access
- [ ] Profile editing
- [ ] Logout functionality

## 🚧 Future Enhancements

### Planned Features
- [ ] Voice message recording
- [ ] Image/media sharing
- [ ] Group chat functionality
- [ ] Video/voice calling
- [ ] Push notifications
- [ ] Message read receipts
- [ ] Online/offline status
- [ ] Message search
- [ ] Dark mode support
- [ ] Multi-language support
- [ ] Message forwarding
- [ ] Starred messages
- [ ] Backup and restore

### Technical Improvements
- [ ] Unit tests
- [ ] UI tests
- [ ] Code coverage
- [ ] Performance optimization
- [ ] Offline support
- [ ] API key security (BuildConfig/local.properties)
- [ ] Error handling improvements
- [ ] Loading states
- [ ] Retry mechanisms

## 📝 Code Quality

### Best Practices Implemented
- ✅ MVVM Architecture
- ✅ Dependency Injection (Hilt)
- ✅ Kotlin Coroutines for async operations
- ✅ StateFlow for reactive state
- ✅ Type-safe navigation
- ✅ Material Design 3
- ✅ Clean code structure
- ✅ Separation of concerns
- ✅ Error handling
- ✅ Logging for debugging

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Contribution Guidelines
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Test your changes thoroughly
- Update documentation if needed

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👨‍💻 Author

**Muhammad Bilal**
- GitHub: [@Muhammad-bilal-503](https://github.com/Muhammad-bilal-503)
- Email: mughalbillal0012345@gmail.com

## 🙏 Acknowledgments

- **WhatsApp** - For the design inspiration
- **Firebase** - For backend services
- **Groq** - For fast AI inference
- **Jetpack Compose** - For modern UI framework
- **Material Design** - For design guidelines

## 📞 Support

If you encounter any issues or have questions:
1. Check existing [Issues](https://github.com/Muhammad-bilal-503/WhatsApp-Clone/issues)
2. Create a new issue with detailed description
3. Include device information and error logs

## 📊 Project Statistics

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 34 (Android 14)
- **Lines of Code**: ~5000+
- **Screens**: 15+
- **Features**: 20+

---

**Note**: This is an educational project created for learning purposes. It is not affiliated with WhatsApp or Meta. Use responsibly and in accordance with applicable terms of service.
