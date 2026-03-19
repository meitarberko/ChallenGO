# ChallenGo - Positive Daily Challenge App for Teens
A complete Android application built with Kotlin, following MVVM architecture, that encourages teenagers to complete positive daily challenges and share their achievements within a supportive and safe community.

---

## 🎯 Project Overview
ChallenGo is a social challenge-based Android application designed as a safe alternative to harmful viral trends.

Users receive a randomized daily challenge, complete it within 24 hours, and upload proof in the form of a post. Upon successful completion, they earn points and level up. The app promotes positive habits, accountability, and a supportive digital environment.

### Core Concept
- One active challenge at a time
- 24-hour completion window
- Points awarded only after proof (post) upload
- Levels increase based on accumulated points
- Hashtags collected based on challenge categories (no duplicates)
- Fully synchronized social features (likes, comments, notifications)

---

## 🏗️ Architecture
- Language: Kotlin
- UI: XML + Fragments (View Binding)
- Architecture Pattern: MVVM + Repository Pattern
- Min SDK: 24
- Target SDK: 34

### Architecture Principles
- Clear separation between UI, ViewModel, and Repository layers
- Shared post state for global like synchronization
- Firestore as the single source of truth
- Optimistic UI updates for instant feedback
- WorkManager for scheduled reminders
- Local image handling (no Firebase Storage dependency)

---

## 🛠️ Technology Stack
### Backend & Data
- Firebase Authentication - User registration and login
- Cloud Firestore - Database for:
  - Users
  - Posts
  - Comments
  - Likes
  - Notifications
  - Challenges
- Room Database - Offline-first caching
- WorkManager - Scheduled challenge reminders
- Picasso - Image loading and caching (local URI-based)

### UI / UX
- Material Design - Google Material guidelines
- Navigation Component - Fragment-based navigation
- SafeArgs - Type-safe navigation
- RecyclerView - Posts and comments rendering
- View Binding - Safer UI handling

### Async & State Management
- Kotlin Coroutines
- Flow - Reactive streams
- LiveData - UI state observation
- Optimistic updates for Like system

---

## 📱 Features
### 🔐 Authentication
- User registration (email & password)
- Login with animated loader
- Auto-login on restart
- Logout (compact top-right button in profile)
- Optional profile image (default avatar fallback)

### 🎡 Daily Challenge System
- Animated spinning wheel before challenge roll
- Wheel stops after challenge selection
- 24-hour countdown timer
- Post upload locked until challenge is rolled
- Only one challenge active at a time
- Only one proof post per challenge

### Points & Levels
- Each challenge = 10 points
- Level formula:

```text
level = (totalPoints / 50) + 1
```

- Level progression:
  - 0–49 points -> Level 1
  - 50–99 points -> Level 2
  - 100–149 -> Level 3
  - And so on

Points are awarded only after successful post upload within 24 hours.

If completed:

Challenge done! Wait until the timer ends for your next challenge.

### 📝 Posts
- Create post with image + text + hashtag
- Community Home feed
- Personal post grid in profile
- PostDetails screen with back navigation
- Edit own posts
- Delete own posts

### ❤️ Like System (Fully Global & Synchronized)
#### Firestore Model
- `posts/{postId}/likes/{uid}` -> existence = user liked
- `posts/{postId}.likeCount` -> numeric counter

#### Rules
- Gray heart -> Not liked
- Red heart -> Liked
- Like = +1 instantly
- Unlike = -1 instantly
- Optimistic UI update
- Transactional Firestore update
- Never shows invalid state (e.g., red heart with 0 likes)

Fully synchronized across:
- Home
- Profile
- PostDetails

### 💬 Comments
- Add comments to posts
- Comment includes:
  - Username
  - Content
  - Timestamp
- Updates instantly in UI

### 🔔 Notifications System
Types of notifications:
- New comment
- New like
- Challenge reminder (12h remaining)
- Challenge reminder (3h remaining)
- 24h no-roll reminder

Features:
- Bell icon with unread badge counter
- Reset badge when entering notifications
- Clear all button
- Deep linking to:
  - PostDetails
  - Challenge roll screen
- Stored in:
  - `users/{uid}/notifications/{notificationId}`

### 👤 User Profile
- View level, points, challenges completed
- Edit username
- Edit or remove profile image
- Hashtags collected (no duplicates)
- View own posts
- View other users’ profiles

---

## 🎨 UI/UX Improvements
- Clean Material loader (replaced red arrow)
- Symmetric post layout spacing
- Improved typography
- Heart icon instead of star
- Comment bubble icon
- Back button in PostDetails
- Compact logout button
- Consistent spacing and alignment

---

## 📂 Project Structure
```text
app/
 ├── data/
 │   ├── local/
 │   ├── model/
 │   ├── remote/
 │   └── repository/
 ├── ui/
 │   ├── adapter/
 │   ├── fragment/
 │   └── viewmodel/
 ├── di/
 ├── navigation/
 └── res/
```

---

## 🔥 Firestore Structure
- `users/{uid}`
- `posts/{postId}`
- `posts/{postId}/likes/{uid}`
- `posts/{postId}/comments/{commentId}`
- `users/{uid}/notifications/{notificationId}`

Firestore serves as the single source of truth.

---

## 🧪 Testing Checklist
- User registration
- Login
- Logout
- Auto-login
- Challenge roll
- Timer expiration
- Challenge completion
- Points calculation
- Level progression
- Unique hashtag storage
- Post creation
- Edit/Delete post
- Like/unlike global sync
- Comment functionality
- Notification badge behavior
- Reminder triggers
- Offline behavior

---

## 📄 License
This project was created for educational purposes as part of an Android development course.

---

## 👥 Authors
- Meitar Berko
- Hadar Orbach
