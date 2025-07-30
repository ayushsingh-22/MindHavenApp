
# 🌿 MindHavenApp

**MindHaven** is a modern Android application that helps users relax, meditate, and sleep better with a carefully curated collection of soothing sounds and guided meditations. Built with **Kotlin**, **Jetpack Compose**, and **ExoPlayer (Media3)**, the app offers a smooth, intuitive, and immersive experience enhanced by calming visuals.

---

## ✨ Features

- 🎵 **Sleep & Meditation Library**: Browse and play a variety of relaxing nature, instrumental, and ambient sounds.
- ⏱️ **Custom Sleep Timers**: Set a timer to automatically stop playback after a chosen duration.
- 🔊 **Background Playback**: Keep audio playing even when the app is minimized.
- 🧘 **Media Notification Controls**: Easily manage playback from your notification tray.
- 🎨 **Modern UI**: Built entirely with **Jetpack Compose** for a reactive and fluid interface.
- 💫 **Lottie Animations**: Delightful animations for a calming visual experience.

---

## 📸 Screenshots

<div align="center">

  <!-- Row 1: 3 Images -->
  <img src="https://github.com/user-attachments/assets/2d2bc668-6239-4561-93e5-0d38185f990b" width="30%" />
  <img src="https://github.com/user-attachments/assets/3b9051c8-d7ef-4488-8df5-f83c4237d569" width="30%" />
  <img src="https://github.com/user-attachments/assets/37e466e0-bf71-49b0-a871-afc0fe339b37" width="30%" />

  <br><br>

  <!-- Row 2: 2 Images -->
  <img src="https://github.com/user-attachments/assets/90d35747-907c-4a64-9079-6e12178d6723" width="45%" />
  <img src="https://github.com/user-attachments/assets/c82f75cb-731c-4975-b5b6-9549b1200518" width="45%" />

</div>

## 🚀 Getting Started

### ✅ Prerequisites

- Android Studio **Giraffe (2022.3.1)** or newer
- Android device or emulator (API level 23+)
- Internet access (for online Lottie animations, if applicable)

### 📦 Installation

```bash
git clone https://github.com/ayushsingh-22/MindHavenApp.git
cd MindHavenApp
````

1. Open the project in Android Studio
2. Let Gradle sync and download dependencies
3. Run the app on an emulator or connected device

---

## 🧠 App Architecture

**MindHaven** follows the **MVVM architecture**, ensuring clean separation of UI and logic, ease of testing, and better state management.

```
app/
 └── src/
     └── main/
         ├── java/com/example/mindhaven/
         │   ├── service/         # MusicService, MediaSession, NotificationManager
         │   ├── ui/theme/        # Composable UI components and themes
         │   └── viewmodel/       # ViewModels and state holders
         └── res/
             └── raw/             # Audio files
             └── drawable/        # Lottie & images
```

### 🔍 Key Components

| Component                   | Description                                                         |
| --------------------------- | ------------------------------------------------------------------- |
| `MusicService`              | Handles background playback with ExoPlayer and `MediaSessionCompat` |
| `NotificationHelper`        | Builds interactive media notifications                              |
| `SleepScreen`               | Main UI screen for exploring and selecting calming sounds           |
| `SoundCard` & `MusicPlayer` | Custom Composables to visualize sound library and control playback  |
| `SleepViewModel`            | Manages playback state, timers, and user interaction logic          |

---

## 🎧 Sound Categories

You can easily extend the sound collection in your app. Some sound ideas include:

* 🌊 Ocean Waves
* 🌧 Rain & Thunder
* 🌲 Forest Ambience
* 🛎 Tibetan Singing Bowls
* 🎹 Soft Piano
* 🌀 White / Pink / Brown Noise
* 🧘‍♂️ Om Chanting & Binaural Beats

---

## 🛠️ Built With

* [Kotlin](https://kotlinlang.org/)
* [Jetpack Compose](https://developer.android.com/jetpack/compose)
* [ExoPlayer / Media3](https://developer.android.com/jetpack/androidx/releases/media3)
* [Lottie for Android](https://github.com/airbnb/lottie-android)
* [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)

---

## 🤝 Contributing

Contributions are welcome! 💚

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make changes and commit: `git commit -m "Added XYZ feature"`
4. Push the branch: `git push origin feature/your-feature-name`
5. Open a pull request 🚀

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 📬 Contact

Created with 💙 by [Ayush Kumar](https://github.com/ayushsingh-22)
Have feedback or suggestions? Feel free to [open an issue](https://github.com/ayushsingh-22/MindHavenApp/issues).

---

> *“Peace comes from within. Do not seek it without.” — Buddha*
> *Happy relaxing and meditating with **MindHaven**!* 🧘
