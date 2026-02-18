# SITA Card Master - Project Documentation

## 📋 Overview

**SITA Card Master** is a Kotlin Multiplatform mobile application for managing NFC-based membership cards. The app enables administrators to issue, verify, and manage SITA membership cards using NFC technology on Android and iOS devices.

## 🎯 Key Features

### 1. **Authentication System**
- Admin login with role-based access control
- Token-based authentication
- "Remember Me" functionality
- Session management
- Multi-source login support (App/Web)

### 2. **NFC Card Reading (Dashboard)**
- Real-time NFC card scanning
- Member verification and validation
- Card expiry checking
- Display member details:
  - Company name and member ID
  - Contact information (phone, email, address)
  - Card validity dates
  - Membership status
- Quick actions:
  - Call member directly
  - Send email
  - Open address in maps
  - Logout

### 3. **NFC Card Writing (Issue Card)**
- Write member data to NFC cards
- Auto-complete member search
- Card clearing functionality
- Real-time NFC tag detection
- Member verification before card issuance
- Fields written to card:
  - Member ID
  - Company Name
  - Card Manufacturing ID (MFID)
  - Card Validity Date

### 4. **Logo Management**
- Dynamic logo loading from server
- Black background removal tool
- Transparent logo support
- Logo caching and backup

## 🏗️ Architecture

### Platform Support
- **Android**: Native Android activities with Material Design
- **iOS**: Compose Multiplatform screens
- **Shared Code**: Common business logic, network layer, and data models

### Tech Stack

#### Frontend
- **Kotlin Multiplatform** - Cross-platform development
- **Jetpack Compose** - Modern UI toolkit
- **Material Components** - Material Design 3
- **Android Views** - XML layouts for Android activities

#### Backend Communication
- **Ktor Client** - HTTP networking
- **Kotlinx Serialization** - JSON parsing
- **Coroutines** - Asynchronous programming

#### NFC
- **Android NFC API** - Native NFC tag reading/writing
- **NDEF (NFC Data Exchange Format)** - Standard NFC data format

#### Storage
- **SharedPreferences** - Local data persistence
- **SQLite** (DatabaseHelper) - Local database

## 📁 Project Structure

```
sita_card_master/
├── composeApp/
│   ├── src/
│   │   ├── androidMain/          # Android-specific code
│   │   │   ├── kotlin/
│   │   │   │   └── com/example/sitacardmaster/
│   │   │   │       ├── LoginActivity.kt           # Login screen
│   │   │   │       ├── DashboardActivity.kt       # NFC card reading
│   │   │   │       ├── IssueCardActivity.kt       # NFC card writing
│   │   │   │       ├── AndroidNfcManager.kt       # NFC operations
│   │   │   │       ├── DatabaseHelper.kt          # SQLite helper
│   │   │   │       └── SettingsStorage.android.kt # Local storage
│   │   │   └── res/
│   │   │       ├── layout/                        # XML layouts
│   │   │       ├── drawable/                      # Images & logos
│   │   │       └── values/                        # Strings, colors, themes
│   │   ├── commonMain/           # Shared code
│   │   │   └── kotlin/
│   │   │       └── com/example/sitacardmaster/
│   │   │           ├── network/
│   │   │           │   ├── AuthApiClient.kt       # Authentication API
│   │   │           │   ├── MemberApiClient.kt     # Member API
│   │   │           │   └── models/
│   │   │           │       ├── AuthModels.kt      # Auth data models
│   │   │           │       └── MemberModels.kt    # Member data models
│   │   │           ├── screens/                   # Compose screens
│   │   │           ├── NfcManager.kt              # NFC interface
│   │   │           └── AppState.kt                # App state management
│   │   └── iosMain/              # iOS-specific code
│   └── build.gradle.kts
├── iosApp/                       # iOS application
├── remove_black_background.py    # Logo processing script
└── README.md
```

## 🌐 API Integration

### Base URLs
- **Auth API**: `https://apisita.shanti-pos.com/api/auth`
- **Member API**: `https://apisita.shanti-pos.com/api`

### Authentication Endpoints

#### 1. Login
```
POST /api/auth/login
Body: {
  "username": "string",
  "password": "string",
  "source": "App" | "Web"
}
Response: {
  "token": "string",
  "username": "string",
  "role": "string",
  "logo": "string (URL)"
}
```

#### 2. Register Admin
```
POST /api/auth/register
Headers: { "Authorization": "Bearer <token>" }
Body: {
  "username": "string",
  "password": "string",
  "role": "string",
  "allowedSource": "string"
}
```

#### 3. Get Admins
```
GET /api/auth/admins
Headers: { "Authorization": "Bearer <token>" }
```

#### 4. Get Current User
```
GET /api/auth/me
Headers: { "Authorization": "Bearer <token>" }
```

### Member Endpoints

#### 1. Get Approved Members
```
GET /api/members?search=<query>
Response: {
  "members": [
    {
      "memberId": "string",
      "companyName": "string",
      "status": 1,  // 1 = Approved
      ...
    }
  ]
}
```

#### 2. Verify Member
```
POST /api/members/verify
Body: {
  "memberId": "string",
  "companyName": "string",
  "card_mfid": "string",
  "cardValidity": "string"
}
Response: {
  "verified": boolean,
  "expired": boolean,
  "message": "string",
  "companyName": "string",
  "phoneNumber": "string",
  "email": "string",
  "address": "string",
  ...
}
```

## 🔧 NFC Functionality

### Card Reading Process
1. User taps "Tap logo to scan card" on Dashboard
2. NFC reader activates (foreground dispatch)
3. User taps NFC card to device
4. App reads NDEF message from card
5. Extracts member ID and company name
6. Calls API to verify member
7. Displays member details or error

### Card Writing Process
1. User searches for member in IssueCard screen
2. Selects member from autocomplete
3. Taps "Write Card" button
4. NFC writer activates
5. User taps blank NFC card to device
6. App writes NDEF message with:
   - Member ID
   - Company Name
   - Card MFID
   - Validity Date
7. Confirms successful write

### NFC Data Format
The app uses **NDEF (NFC Data Exchange Format)** with text records:
```
Record 1: Member ID
Record 2: Company Name
Record 3: Card MFID (optional)
Record 4: Card Validity (optional)
```

## 🎨 Logo Processing

### Black Background Removal Script

**File**: [`remove_black_background.py`](file:///Users/shah/StudioProjects/sita_card_master/remove_black_background.py)

**Purpose**: Removes black backgrounds from logo images and replaces them with transparency.

**Usage**:
```bash
# Process all logos in the project
python3 remove_black_background.py

# Process a specific file with custom threshold
python3 remove_black_background.py /path/to/logo.png 50
```

**Features**:
- Detects black/near-black pixels (RGB ≤ threshold)
- Replaces with transparency
- Creates automatic backups (*_with_black.png)
- Processes multiple directories

**Directories Processed**:
- `composeApp/src/androidMain/res/drawable/`
- `composeApp/src/commonMain/composeResources/drawable/`

## 🚀 Setup & Installation

### Prerequisites
- **Android Studio** (latest version)
- **JDK 11+**
- **Kotlin 1.9+**
- **Gradle 8.0+**
- **Python 3** (for logo processing)
- **NFC-enabled Android device** (for testing)

### Installation Steps

1. **Clone the repository**
   ```bash
   cd /Users/shah/StudioProjects/sita_card_master
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the project directory

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - Wait for dependencies to download

4. **Configure NFC Permissions**
   - Ensure `AndroidManifest.xml` has NFC permissions (already configured)

5. **Build the Project**
   ```bash
   ./gradlew :composeApp:assembleDebug
   ```

6. **Run on Device**
   - Connect NFC-enabled Android device
   - Click Run ▶️ in Android Studio
   - Select your device

### Python Dependencies (for logo processing)
```bash
pip3 install Pillow
```

## 🔐 Permissions

### Android Manifest Permissions
```xml
<uses-permission android:name="android.permission.NFC" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

## 📱 User Flows

### Admin Login Flow
1. Launch app → LoginActivity
2. Enter admin ID and password
3. Optional: Check "Remember Me"
4. Tap "LOGIN"
5. API authentication
6. Navigate to Dashboard

### Card Verification Flow
1. Dashboard → Tap logo
2. NFC reader activates
3. Tap card to device
4. Read card data
5. API verification
6. Display member details

### Card Issuance Flow
1. Dashboard → Tap "Issue Card"
2. Search member by company name
3. Select from autocomplete
4. Tap "Write Card"
5. Tap blank card to device
6. Write data to card
7. Confirmation message

## 🛠️ Development

### Build Commands

**Android Debug Build**:
```bash
./gradlew :composeApp:assembleDebug
```

**Android Release Build**:
```bash
./gradlew :composeApp:assembleRelease
```

**iOS Build**:
```bash
# Open iosApp directory in Xcode
open iosApp/iosApp.xcodeproj
```

**Clean Build**:
```bash
./gradlew clean
```

### Testing NFC

**Requirements**:
- Physical NFC-enabled Android device
- NFC tags (NTAG213, NTAG215, or compatible)

**Testing Steps**:
1. Enable NFC in device settings
2. Run app on device
3. Navigate to Dashboard or IssueCard
4. Tap NFC tag to back of device
5. Verify read/write operations

## 📊 Data Models

### LoginResponse
```kotlin
{
  token: String
  username: String
  role: String
  logo: String  // URL to organization logo
}
```

### VerifyMemberResponse
```kotlin
{
  verified: Boolean
  expired: Boolean
  memberId: String
  companyName: String
  phoneNumber: String
  email: String
  address: String
  cardValidity: String
  status: Int  // 1 = Approved
  message: String?
}
```

## 🐛 Troubleshooting

### NFC Not Working
- Ensure NFC is enabled in device settings
- Check device has NFC hardware
- Verify app has NFC permissions
- Try different NFC tag types

### Login Fails
- Check internet connection
- Verify API server is accessible
- Confirm credentials are correct
- Check server logs for errors

### Logo Not Displaying
- Verify logo URL in API response
- Check internet connection
- Run `remove_black_background.py` if logo has black background
- Clear app cache and rebuild

### Build Errors
- Clean and rebuild: `./gradlew clean build`
- Invalidate caches: Android Studio → File → Invalidate Caches
- Update Gradle and dependencies
- Check Java version (requires JDK 11+)

## 📝 Scripts

### Logo Processing Scripts

1. **[remove_black_background.py](file:///Users/shah/StudioProjects/sita_card_master/remove_black_background.py)**
   - Removes black backgrounds from logos
   - Creates transparent PNGs
   - Automatic backup creation

## 🔄 Version Control

**Git Repository**: Local repository at `/Users/shah/StudioProjects/sita_card_master`

**Important Files to Track**:
- Source code (`.kt`, `.xml`)
- Build configurations (`.gradle.kts`)
- Resources (drawables, layouts)
- Documentation (`.md`)

**Files to Ignore** (already in `.gitignore`):
- Build outputs (`/build`, `/composeApp/build`)
- IDE files (`.idea`, `.gradle`)
- Local properties (`local.properties`)

## 📞 Support & Contact

For issues or questions about the SITA Card Master application, contact the development team or refer to the API documentation at the server endpoint.

---

**Last Updated**: February 7, 2026  
**Version**: 1.0  
**Platform**: Kotlin Multiplatform (Android/iOS)
