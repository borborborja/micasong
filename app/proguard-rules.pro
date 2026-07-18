# MiCaSong ProGuard rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
