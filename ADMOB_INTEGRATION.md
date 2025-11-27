# 📱 AdMob Integration Guide - Smart Recorder

**Ngày tạo:** 2025-01-27  
**Status:** ⏸️ DEFERRED (Triển khai sau khi app stable)

---

## 📋 Thông Tin AdMob

### App Information
- **App Name:** Smart Recorder & Transcripts
- **AdMob App ID:** `ca-app-pub-7030881794489733~3017072817`

---

## 🎯 Ad Units

### 1. Banner Ad
- **Ad Unit ID:** `ca-app-pub-7030881794489733/5332955408`
- **Ad Unit Name:** `banner_smartrecoder`
- **Type:** Banner
- **Placement:** 
  - Bottom of main screens (RecordScreen, LibraryScreen, StudyScreen)
  - Hoặc top của màn hình (tùy UX design)

### 2. Interstitial Ad (Full Screen)
- **Ad Unit ID:** `ca-app-pub-7030881794489733/8544731663`
- **Ad Unit Name:** `fullads_smartrecorder`
- **Type:** Interstitial (Full Screen)
- **Placement:**
  - App open (sau onboarding nếu có)
  - Sau khi complete recording (optional)
  - Sau khi complete transcription (optional)

---

## 🛠️ Implementation Steps

### Step 1: Add Dependencies

**File:** `app/build.gradle.kts`

```kotlin
dependencies {
    // AdMob
    implementation("com.google.android.gms:play-services-ads:22.6.0")
}
```

### Step 2: Add App ID to Manifest

**File:** `app/src/main/AndroidManifest.xml`

```xml
<application>
    <!-- AdMob App ID -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-7030881794489733~3017072817"/>
    
    <!-- ... other application config -->
</application>
```

### Step 3: Create AdMob Banner Composable

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/ads/AdMobBanner.kt`

```kotlin
@Composable
fun AdMobBanner(
    adUnitId: String = "ca-app-pub-7030881794489733/5332955408",
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier
    )
}
```

### Step 4: Create AdMob Interstitial Manager

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/ads/AdMobInterstitial.kt`

```kotlin
class AdMobInterstitialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interstitialAd: InterstitialAd? = null
    
    fun loadInterstitial() {
        interstitialAd = InterstitialAd(context).apply {
            adUnitId = "ca-app-pub-7030881794489733/8544731663"
            loadAd(AdRequest.Builder().build())
        }
    }
    
    fun showInterstitial(activity: Activity) {
        interstitialAd?.let { ad ->
            if (ad.isLoaded) {
                ad.show(activity)
            }
        }
    }
}
```

### Step 5: Integrate Banner Ads

**Files:**
- `app/src/main/java/com/yourname/smartrecorder/ui/screens/RecordScreen.kt`
- `app/src/main/java/com/yourname/smartrecorder/ui/screens/LibraryScreen.kt`
- `app/src/main/java/com/yourname/smartrecorder/ui/screens/StudyScreen.kt`

```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    // Main content
    // ...
    
    // Banner ad at bottom
    AdMobBanner(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    )
}
```

### Step 6: Integrate Interstitial Ads

**File:** `app/src/main/java/com/yourname/smartrecorder/SmartRecorderApplication.kt`

```kotlin
class SmartRecorderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        // Preload interstitial ads
    }
}
```

**File:** `app/src/main/java/com/yourname/smartrecorder/ui/SmartRecorderApp.kt`

```kotlin
@Composable
fun SmartRecorderApp() {
    val interstitialManager = remember { AdMobInterstitialManager(context) }
    
    LaunchedEffect(Unit) {
        interstitialManager.loadInterstitial()
    }
    
    // Show ad when app opens (after onboarding)
    // ...
}
```

---

## 🧪 Testing

### Test Ad Unit IDs (Development)
- **Banner Test ID:** `ca-app-pub-3940256099942544/6300978111`
- **Interstitial Test ID:** `ca-app-pub-3940256099942544/1033173712`

**Important:** 
- Luôn dùng test IDs khi development
- Chỉ dùng production IDs khi publish
- Test trên real device (ads không hiện trên emulator)

---

## 📋 Checklist

### Pre-Implementation
- [ ] Review AdMob policies: https://support.google.com/admob/answer/6128543
- [ ] Plan ad placement (UX consideration)
- [ ] Decide ad frequency (không quá nhiều ads)

### Implementation
- [ ] Add AdMob dependency
- [ ] Add App ID to Manifest
- [ ] Create AdMobBanner composable
- [ ] Create AdMobInterstitial manager
- [ ] Integrate banner ads vào main screens
- [ ] Integrate interstitial ads (app open, after recording)
- [ ] Test với test ad unit IDs
- [ ] Test trên real device

### Post-Implementation
- [ ] Verify ads hiển thị đúng
- [ ] Test user experience (không làm gián đoạn workflow)
- [ ] Monitor ad performance trong AdMob dashboard
- [ ] Consider Premium option để remove ads

---

## 💡 Best Practices

1. **Ad Frequency:**
   - Banner: Có thể hiển thị liên tục ở bottom
   - Interstitial: Không quá 1 ad mỗi 2-3 actions (tránh spam)

2. **User Experience:**
   - Không show ads khi đang recording
   - Không show ads khi đang transcribing
   - Show ads ở thời điểm phù hợp (idle, after completion)

3. **Premium Option:**
   - Có thể thêm Premium upgrade để remove ads
   - Link từ Settings screen

4. **Error Handling:**
   - Handle ad load failures gracefully
   - Không block app nếu ads không load được

---

## 📚 References

- [Google Mobile Ads SDK Documentation](https://developers.google.com/admob/android/quick-start)
- [AdMob Policies](https://support.google.com/admob/answer/6128543)
- [AdMob Best Practices](https://support.google.com/admob/answer/6329638)

---

## ⚠️ Notes

- **Status:** ⏸️ DEFERRED - Triển khai sau khi app stable
- **Priority:** Low - Không ảnh hưởng core functionality
- **Estimated Time:** 4-6 giờ (bao gồm testing)

