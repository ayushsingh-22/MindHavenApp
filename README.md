# 🌿 MindHaven App

**MindHaven** is a modern Android wellness application that helps users **relax, meditate, and sleep better** with a carefully curated collection of soothing sounds, guided meditations, and immersive animations. Built with **Kotlin**, **Jetpack Compose**, and **ExoPlayer (Media3)**, the app provides a smooth, intuitive, and personalized experience designed for mental wellbeing.

---

## ✨ What's New in v3.0.0

* **Profile & Personalization**: Track your meditation streaks, total hours, badges, and edit your profile picture or display name.
* **Daily Mindfulness Tips**: Get personalized tips with animations to improve your mental wellness.
* **Interactive UI**: User-friendly topbars, floating action buttons, and responsive cards for better engagement.
* **Enhanced Audio Experience**: Optimized meditation and sleep playback with smooth transitions.
* **Improved Navigation**: Intuitive flow between Meditation, Sleep, and Profile sections.
* **Lavender/Purple Soothing Theme**: Elegant, calming UI palette to enhance focus and relaxation.

---

## 🌟 Key Features

### **Authentication**

* Google Sign-In and Email Login/Signup for seamless and secure access.
* Persistent session management ensures users remain logged in until they explicitly log out.

### **Meditation Section**

* Curated library of meditation sounds.
* Customizable session lengths and ambient options.
* Interactive player with play/pause, skip, and visual feedback.

### **Sleep Section**

* Collection of calming sleep sounds.
* Timer settings with fade-in/fade-out for smooth transitions.
* Relaxing animations to aid sleep.

### **Profile Section**

* View and edit profile details.
* Track meditation streaks, total hours, and badges.
* Receive daily mindfulness tips with Lottie animations.
* Quick access FAB for starting meditation sessions instantly.

### **User Experience & Interface**

* Lavender and purple color palette for calming visuals.
* Interactive buttons, cards, and floating action buttons.
* Smooth animations throughout the app.

---

## 📸 Screenshots

<div align="center">

<img src="https://github.com/user-attachments/assets/2d2bc668-6239-4561-93e5-0d38185f990b" width="30%" />
<img src="https://github.com/user-attachments/assets/3b9051c8-d7ef-4488-8df5-f83c4237d569" width="30%" />
<img src="https://github.com/user-attachments/assets/37e466e0-bf71-49b0-a871-afc0fe339b37" width="30%" />

<br><br>

<img src="https://github.com/user-attachments/assets/90d35747-907c-4a64-9079-6e12178d6723" width="45%" />
<img src="https://github.com/user-attachments/assets/c82f75cb-731c-4975-b5b6-9549b1200518" width="45%" />

</div>

---

## 🚀 Getting Started

### ✅ Requirements

* Android Studio Giraffe or later
* API 23+ Emulator or Physical Device
* Internet Connection (for streaming and Lottie animations)

### 🧩 Installation

```bash
git clone https://github.com/ayushsingh-22/MindHavenApp.git
cd MindHavenApp
```

1. Open in Android Studio
2. Let Gradle sync
3. Run the app on your emulator or device

---

## 🧠 App Architecture

MindHaven follows the **MVVM architecture** for scalability and separation of concerns.

```
com.example.mindhaven
├── model                  # Data classes like SoundItem
├── service                # ExoPlayer, MusicService, NotificationHelper
├── ui.theme
│   ├── components         # SoundCard, LottieAnimation, MusicPlayer UI, Profile Cards
│   ├── navigation         # Navigation routes
│   ├── screens            # Meditation, Sleep, Profile, Registration, Welcome screens
│   └── viewmodel          # ViewModels for Meditation, Sleep, Authentication
└── MainActivity.kt        # Entry point
```

### 🔍 Key Classes

| File                  | Role                                                   |
| --------------------- | ------------------------------------------------------ |
| `SoundItem`           | Data model for sound metadata                          |
| `MusicService`        | Streams audio using ExoPlayer + MediaSessionService    |
| `NotificationHelper`  | Displays media playback controls in notification tray  |
| `SoundCard`           | Composable UI card for each audio track                |
| `MeditationViewModel` | Manages playback state, streaming, timer, animation    |
| `AuthViewModel`       | Handles authentication, Google/Email login, logout     |
| `profileScreen`       | Shows user stats, badges, daily tips, and profile info |

---

## 🛠️ Built With

* [Kotlin](https://kotlinlang.org/)
* [Jetpack Compose](https://developer.android.com/jetpack/compose)
* [ExoPlayer (Media3)](https://developer.android.com/jetpack/androidx/releases/media3)
* [Lottie for Android](https://github.com/airbnb/lottie-android)
* [Appwrite](https://appwrite.io/)
* [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
* [Gradle](https://gradle.org/)

---

## 🧘 Sound Types (Examples)

* 🌊 Ocean Waves
* 🌧️ Rain & Thunder
* 🔥 Campfire
* 🎹 Soft Piano
* 🧘 Om Mantras
* 🌀 White / Brown Noise

---

## 🤝 Contributing

1. Fork the repository
2. Create a new branch: `git checkout -b feature/feature-name`
3. Commit your changes: `git commit -m "Add feature"`
4. Push your branch: `git push origin feature/feature-name`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the [MIT License](LICENSE)

---

## 📬 Contact

Developed by [Ayush Kumar](https://github.com/ayushsingh-22)
Questions? Suggestions? [Open an issue](https://github.com/ayushsingh-22/MindHavenApp/issues)

---

> *“Peace comes from within. Do not seek it without.” — **Buddha***
>
> *Enjoy mindfulness, meditation, and restful sleep with **MindHaven**!* 🧘

---
