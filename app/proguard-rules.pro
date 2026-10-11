# SPDX-License-Identifier: GPL-3.0-or-later
# Strip android.util.Log calls from release builds (LLD §6).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# play flavour (US-025): review-ktx 2.0.2 references a compile-time-only annotation from play-services-basement that
# isn't shipped. It's an annotation with no runtime use (R8's generated missing_rules.txt suggests exactly this).
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
