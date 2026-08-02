# Keep generic signatures (fixes Class cannot be cast to ParameterizedType)
-keepattributes Signature
-keepattributes *Annotation*

# Keep Retrofit and Gson classes
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }

# Keep network models and interfaces
-keep class com.fieldweather.recorder.network.** { *; }
