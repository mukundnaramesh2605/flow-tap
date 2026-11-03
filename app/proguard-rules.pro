# kotlinx.serialization: keep serializers for @Serializable classes (nav routes, flow models, export JSON).
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.flowtap.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
