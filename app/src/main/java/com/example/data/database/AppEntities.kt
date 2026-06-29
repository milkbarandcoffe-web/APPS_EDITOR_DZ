package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val email: String,
    val isOwner: Boolean = false,
    val guestKey: String = ""
)

@Entity(tableName = "settings")
data class AppSettingEntity(
    @PrimaryKey val id: Int = 1,

    // --- Config importato dal DZCFG1 ---
    // Exec: URL /exec di APPS EDITOR (usato dalla bolla per tutti; usato
    //        anche dal WebViewer per il guest).
    val bridgeExec: String = "",
    // Dev: URL /dev (solo owner). Il WebViewer owner apre questo, non exec.
    val bridgeDev: String = "",
    // Token bridge: OC_BRIDGE_TOKEN
    val bridgeToken: String = "",
    // Email utente (who per la bolla)
    val guestEmail: String = "",
    // Chiave guest (gkey per la bolla; vuota se owner)
    val guestKey: String = "",
    // Flag owner (derivato dal config, cached qui)
    val isOwner: Boolean = false,

    // --- Aspetto condiviso X + bolla ---
    val bubbleSize: Int = 60,
    val bubbleColorHex: String = "#FF6200EE",
    val bubbleText: String = "SEND",
    val bubbleOpacity: Float = 0.85f,
    val xSize: Int = 35,
    val xOpacity: Float = 0.10f,

    // Dominio extra che resta dentro la WebView (es. accounts.google.com)
    val verificationUrl: String = ""
)
