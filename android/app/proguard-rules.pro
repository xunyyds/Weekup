# Framework-instantiated Activity/Receiver classes are retained by AGP manifest rules.
# Jsoup uses direct calls; no broad keep-all rule is necessary.
-keepattributes Signature,InnerClasses,EnclosingMethod

# The desktop-mode page hook calls XwakePostChannel.register by reflection, and
# @JavascriptInterface methods are only reachable through that annotation.
-keepclassmembers class ** {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*
