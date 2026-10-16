package com.lcaf.tyguarnotes.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NoteEntity::class,
        NoteFolderEntity::class,
        GalleryItemEntity::class,
        DocumentItemEntity::class,
        RemoteFileEntity::class,
        SyncMetaEntity::class
    ],
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class TyguarDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun noteFolderDao(): NoteFolderDao
    abstract fun galleryItemDao(): GalleryItemDao
    abstract fun documentItemDao(): DocumentItemDao
    abstract fun syncDao(): SyncDao
}
