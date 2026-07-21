# Memora - Offline-First Diary & Memory App 📖

Memora is a modern Android application built entirely with **Kotlin** and **Jetpack Compose**. It is designed to capture personal memories using text, images, and audio recordings, utilizing an **Offline-First Architecture** with a local Room Database and Firebase Firestore synchronization.

## ✨ Key Features

* **Rich Media Entries:** Log memories with text, attach images, and record audio directly within the app.
* **Offline-First Synchronization:** Creates and edits are instantly saved locally using **Room**. A background worker seamlessly syncs data to **Firebase Firestore** when an internet connection is available.
* **Smart Audio Player:** Built-in MediaPlayer with a dynamic seek bar, real-time progress tracking, and a custom animated waveform UI.
* **Text-to-Speech (TTS):** Accessibility feature that reads diary entries aloud using Android's native TTS engine.
* **Cost-Optimized Cloud Strategy:** Text and metadata sync to Firestore, while heavy media files (images/audio) remain locally stored on the device to optimize performance and prevent free-tier cloud limits.
* **State-Driven UI:** Fully reactive user interface built with Jetpack Compose, responding instantly to database changes via Kotlin `Flow`.

## 🛠️ Tech Stack & Architecture

This project follows **Clean Architecture** principles and the **MVVM (Model-View-ViewModel)** design pattern to ensure separation of concerns.

* **UI:** Jetpack Compose (Declarative UI)
* **Language:** Kotlin
* **Architecture:** MVVM + Repository Pattern
* **Local Database:** Room (SQLite) with `@Insert(OnConflictStrategy.REPLACE)` for efficient Upserts.
* **Backend / Cloud:** Firebase Firestore (NoSQL) & Firebase Authentication.
* **Asynchronous Programming:** Kotlin Coroutines & Flow.
* **Dependency Injection:** Dagger Hilt (or manual DI based on your setup).
* **Media:** Coil (Async Image Loading), Android `MediaRecorder` & `MediaPlayer`.

## 📂 Architecture Overview

The app is divided into distinct layers to maintain scalability:
1. **Presentation Layer:** Contains Jetpack Compose UI screens and ViewModels. It only observes UI state and has no knowledge of data sources.
2. **Data Layer (Local):** Contains the Room Database, Entities, and DAOs to act as the single source of truth.
3. **Data Layer (Remote):** Contains Firebase Firestore upload/sync logic.
4. **Repository:** The mediator that writes to the local database first, checks network state, and handles the cloud sync logic (`isSynced` flag).

## 🚀 How to Run This Project

Because this is a public repository, the Firebase API keys have been securely removed. To run this project locally, you will need to connect it to your own Firebase project.

1. Clone this repository:
   ```bash
   git clone [https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git](https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git)
