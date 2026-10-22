# Framework-instantiated Activity/Receiver classes are retained by AGP manifest rules.
# Jsoup uses direct calls; no broad keep-all rule is necessary.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
