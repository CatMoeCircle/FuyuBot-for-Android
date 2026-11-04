# Fuyu-Bot JNI 绑定：libfuyubotjni.so 在 JNI_OnLoad 里用 RegisterNatives
# 按类名 + 方法名 + 签名做运行期绑定，混淆会改类名/方法名导致 UnsatisfiedLinkError。
-keep class com.catmoecircle.fuyubot.runtime.Native { *; }
-keep class com.catmoecircle.fuyubot.runtime.Native$Callback { *; }
