# Сохраняем сигнатуры для корректной работы Generics (исправляет краш TypeToken)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson конкретные правила
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements com.google.gson.TypeAdapterFactory
-keep public class * implements com.google.gson.JsonSerializer
-keep public class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$Adapter { *; }

# Сохраняем наши модели данных, чтобы Gson мог заполнять их поля
-keep class com.vkturn.proxy.models.** { *; }
-keep class com.vkturn.proxy.data.** { *; }

# Сохраняем ViewModel и их конструкторы для корректного создания
-keep class com.vkturn.proxy.viewmodel.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# CameraX и ML Kit (для работы QR)
-keep class androidx.camera.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# JSch (SSH) правила
-keep class com.jcraft.jsch.** { *; }
-keep class com.jcraft.jzlib.** { *; }
-keep class org.bouncycastle.** { *; }
-keep interface com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn pbkdf2.**