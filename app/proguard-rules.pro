# Proguard rules for Progress 1%
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin Serialization
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    @kotlinx.serialization.Serializable class *;
}

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
