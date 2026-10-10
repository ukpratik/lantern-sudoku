# SPDX-License-Identifier: GPL-3.0-or-later
# Strip android.util.Log calls from release builds (LLD §6).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
