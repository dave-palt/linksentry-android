# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
