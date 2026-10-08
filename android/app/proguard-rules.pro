# QUANTUM-CLI Android — ProGuard/R8 rules

# NanoHTTPD (reflection-free, but keep server entry points under optimization)
-keep class fi.iki.elonen.** { *; }

# MediaPipe GenAI LLM Inference loads its native pipeline via JNI
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.genai.** { *; }

# org.json ships with the platform; Tink is used by security-crypto
-keep class com.google.crypto.tink.** { *; }

# Kotlin coroutines debug metadata
-dontwarn kotlinx.coroutines.debug.**

# MediaPipe protos + AutoValue/javax.annotation compile-only references
# (R8-generated: build/outputs/mapping/fullRelease/missing_rules.txt)
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
-dontwarn javax.annotation.processing.AbstractProcessor
-dontwarn javax.annotation.processing.SupportedAnnotationTypes
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.element.Modifier
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8

# Strip noisy MediaPipe/Tink logging from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
