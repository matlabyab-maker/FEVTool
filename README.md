# FEVTool

نسخه آغازین مستقل پروژه اندرویدی FEVTool.

## مرحله فعلی
در این نسخه فقط نوار ابزار افقی ثابت در بالای صفحه ساخته شده است. شش دکمه به ترتیب:
1. Import Video
2. Export Video
3. Delete
4. Color And Light
5. Tracks Cut Tool
6. Insert

اگر عرض صفحه برای نمایش همه دکمه‌ها کافی نباشد، نوار به‌صورت افقی پیمایش می‌شود. دکمه‌ها در این مرحله فقط رابط کاربری هستند و هنوز عملکرد ویرایشی به آن‌ها متصل نشده است.

## ساخت
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
- Kotlin: 2.0.21
- JDK: 17
- Minimum Android: 6.0 (API 23)

برای ساخت محلی، پروژه را در Android Studio باز کنید و اجازه دهید Gradle Sync کامل شود.
در GitHub، با هر Push روی هر برنچ، workflow فایل `.github/workflows/android.yml` اجرا می‌شود. اجرای دستی نیز از بخش Actions در دسترس است.
