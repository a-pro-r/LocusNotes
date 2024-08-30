package com.beakoninc.locusnotes

interface BaseRepository<T> {
    suspend fun getAll(): List<T>
    suspend fun getById(id: String): T?
    suspend fun insert(item: T): String
    suspend fun update(item: T)
    suspend fun delete(item: T)
}