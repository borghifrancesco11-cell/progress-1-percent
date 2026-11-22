# ============================================
# ProGuard Rules for Progress 1 Percent
# APK Optimization Configuration
# ============================================

# ============================================
# CODE SHRINKING & OPTIMIZATION
# ============================================

-optimizationpasses 5
-allowaccessmodification
-dontskipnonpubliclibraryclassmembers

# Preserve Compose Components
-keep class androidx.compose.** { *; }
-keep class com.progressone.nativeapp.ui.** { *; }

# Keep Main Activity and Navigation
-keep class com.progressone.nativeapp.MainActivity { *; }
-keep class com.progressone.nativeapp.ui.TodayScreen { *; }
-keep class com.progressone.nativeapp.ui.FoodScreen { *; }
-keep class com.progressone.nativeapp.ui.EnhancedProgressScoreOverview { *; }
-keep class com.progressone.nativeapp.ui.EnhancedFoodDialog { *; }

# ============================================
# DATA BINDING & SERIALIZATION
# ============================================

# Keep Data Models
-keepclassmembers class com.progressone.nativeapp.data.** { *; }
-keep class com.progressone.nativeapp.data.Food { *; }
-keep class com.progressone.nativeapp.data.ProgressScore { *; }
-keep class com.progressone.nativeapp.data.LocalStore { *; }

# Preserve Serialization
-keepclassmembers class * implements java.io.Serializable {
    private static final long serialVersionUID;
}

# Gson Annotations
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ============================================
# MACHINE LEARNING & AI MODELS
# ============================================

# ML Kit Dependencies
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# ONNX Runtime Models
-keep class com.microsoft.onnxruntime.** { *; }
-keep class com.progressone.nativeapp.foodvision.** { *; }

# Preserve Model Classes
-keepclassmembers class * implements com.microsoft.onnxruntime.OnnxRuntime {
    <methods>;
}

# Keep Food Vision Classifier
-keep class com.progressone.nativeapp.foodvision.Food101Classifier { *; }
-keep class com.progressone.nativeapp.foodvision.FoodDatabase { *; }

# ============================================
# COMPOSE COMPONENTS
# ============================================

# Preserve Compose State
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose Layouts
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }

# ============================================
# REMAINING CLASSES
# ============================================

# Preserve all class members
-keepclassmembers class * {
    public <init>(...);
}

-keepclassmembers class * {
    public <init>(android.content.Context);
}

# Keep ViewModel classes
-keep class androidx.lifecycle.** { *; }

# ============================================
# RESOURCE OPTIMIZATION
# ============================================

# Exclude unnecessary metadata
-dontnote android.support.**
-dontnote androidx.**

# Resource shrinking (isShrinkResources = true) keeps resources referenced from
# the APK. R8 does not support the ProGuard "-keepresources" directive, so it is
# intentionally omitted here.

# Exclude duplicate files
-dontshrink
-dontoptimize

# ============================================
# DEBUG OPTIMIZATION
# ============================================

-keepattributes Exceptions,InnerClasses,Signature,
    RuntimeVisibleAnnotations,AnnotationDefault

# Log ProGuard statistics
-printmapping mapping.txt
-printseeds seeds.txt

# ============================================
# CUSTOM RULES FOR FOOD INTEGRATION
# ============================================

# Food Interaction Components
-keep class com.progressone.nativeapp.ui.FoodCard { *; }
-keep class com.progressone.nativeapp.ui.FoodSummaryCard { *; }
-keep class com.progressone.nativeapp.ui.EnhancedFoodCard { *; }

# Nutrition and Statistics
-keep class com.progressone.nativeapp.data.NutritionMath { *; }
-keep class com.progressone.nativeapp.data.Metrics { *; }

# ============================================
# END OF PROGUARD RULES
# ============================================
