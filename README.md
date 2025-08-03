
# 🌿 MindHavenApp

**MindHaven** is a modern Android application that helps users relax, meditate, and sleep better with a carefully curated collection of soothing sounds and guided meditations. Built with **Kotlin**, **Jetpack Compose**, and **ExoPlayer (Media3)**, the app offers a smooth, intuitive, and immersive experience enhanced by calming visuals.

---

## ✨ Features

- 🎵 **Curated Audio Library** — Explore ambient nature sounds, white noise, instrumental tones, and more
- ⏱️ **Sleep Timer** — Set a duration after which the audio stops automatically
- 🔊 **Background Playback** — Audio continues to play even when the app is minimized
- 🧘 **Media Controls in Notifications** — Pause, play, or skip from the system tray
- 🎨 **Built with Jetpack Compose** — Enjoy a modern, responsive UI
- 💫 **Lottie Animations** — Calming visuals to enhance focus and relaxation
- ☁️ **Cloud-based Streaming** — Sounds are streamed from the backend using Appwrite

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

- Android Studio Giraffe or later
- API 23+ Emulator or Physical Device
- Internet Connection (for streaming and Lottie)

### 🧩 Installation

```bash
git clone https://github.com/ayushsingh-22/MindHavenApp.git
cd MindHavenApp
````

1. Open in Android Studio
2. Let Gradle sync
3. Run the app on your emulator or device

---

## 🧠 App Architecture

MindHaven follows the **MVVM architecture** for scalability and separation of concerns.

```
com.example.mindhaven
├── model                  # Data classes like SoundItem
├── service                # ExoPlayer, MusicService, NotificationManager
├── ui.theme
│   ├── components         # SoundCard, LottieAnimation, MusicPlayer UI
│   ├── navigation         # Navigation routes
│   ├── screens            # Themed screen styling
│   └── viewmodel          # ViewModels for Meditation and Sleep
└── MainActivity.kt        # Entry point
```

### 🔍 Key Classes

| File                  | Role                                                  |
| --------------------- | ----------------------------------------------------- |
| `SoundItem`           | Data model for sound metadata                         |
| `MusicService`        | Streams audio using ExoPlayer + MediaSessionService   |
| `NotificationHelper`  | Displays media playback controls in notification tray |
| `SoundCard`           | Composable UI card for each audio track               |
| `MeditationViewModel` | Manages playback state, streaming, timer, animation   |
| `fetchSounds()`       | Fetches sound data from Appwrite backend              |

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
> *Happy relaxing and meditating with **MindHaven**!* 🧘
