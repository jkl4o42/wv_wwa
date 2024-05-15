package com.jkl.mylibrary

interface Fetch {

    suspend fun fetch(value: String)
    fun fetch(value: String, value2: Map<String, String>)
}