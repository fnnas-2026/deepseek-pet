# ===== deepseek桌宠 R8/ProGuard 基础混淆规则 =====

# 保留注解（Compose / 序列化 / 反射依赖）
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ---- Kotlin 元数据 ----
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** { @kotlin.Metadata <methods>; }

# ---- Compose ----
-keep class androidx.compose.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-keep class androidx.compose.runtime.** { *; }

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ---- org.json ----
-keep class org.json.** { *; }

# ---- 应用内通过 Manifest 注册的四大组件必须保留（类名需与 Manifest 一致）----
-keep class com.ferry.pet.MainActivity { *; }
-keep class com.ferry.pet.ChatActivity { *; }
-keep class com.ferry.pet.account.OAuthLoginActivity { *; }
-keep class com.ferry.pet.PetOverlayService { *; }
-keep class com.ferry.pet.phone.PetA11yService { *; }

# ---- 人格 JSON 序列化字段（反射读写）----
-keep class com.ferry.pet.ChatOptions$Persona { *; }
-keepclassmembers class com.ferry.pet.ChatOptions$Persona { <fields>; }

# ---- 其余应用内部类允许 R8 混淆 / 收缩 ----

# ---- 资源 / BuildConfig ----
-keep class com.ferry.pet.BuildConfig { *; }

# 移除日志（Release）
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
