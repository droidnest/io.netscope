# Keep all public NetScope API classes so consumers using R8/ProGuard can call them.
-keep class io.netscope.NetScope { *; }
-keep class io.netscope.NetScopeConfig { *; }
-keep class io.netscope.NotificationStyle { *; }
-keep class io.netscope.interceptor.NetScopeInterceptor { *; }
-keep class io.netscope.interceptor.NetScopeTimingListenerFactory { *; }
-keep class io.netscope.shake.NetScopeShakeDetector { *; }
-keep class io.netscope.model.** { *; }
-keep class io.netscope.domain.** { *; }

# Room — keep generated DAO implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
