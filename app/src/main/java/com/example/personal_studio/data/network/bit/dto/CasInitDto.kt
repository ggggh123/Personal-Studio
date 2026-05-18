package com.example.personal_studio.data.network.bit.dto

/** Result of parsing the GET /cas/login HTML page. Pure data, no Retrofit. */
data class CasInitDto(
    val execution: String,
    val salt: String,     // CAS calls this "croypto" in the form
)
