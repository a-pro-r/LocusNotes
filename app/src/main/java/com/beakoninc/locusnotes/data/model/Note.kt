package com.beakoninc.locusnotes.data.model

import java.util.UUID

// Represents a single note in the application
data class Note(
    val id: String = UUID.randomUUID().toString(), // Unique identifier for the note
    val title: String, // Title of the note
    val content: String, // Content of the note
    val createdAt: Long = System.currentTimeMillis(), // Timestamp of when the note was created
    val updatedAt: Long = createdAt // Timestamp of when the note was last updated
)