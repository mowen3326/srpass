# Keep line numbers so obfuscated release crashes remain diagnosable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# KeePass / KDBX (kotpass) and BouncyCastle must not be obfuscated or stripped.
-keep class app.keemobile.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class kotlinx.coroutines.** { *; }
