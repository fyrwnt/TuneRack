## NewPipe Extractor (from its README)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

## Rhino (NewPipe's JavaScript engine) references desktop-Java APIs that Android lacks. Those code
## paths (JSR-223 scripting, java.beans conversion, invokedynamic linking) are never used here.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
