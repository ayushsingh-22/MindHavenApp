package com.example.mindhaven.service

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth

object SupabaseManager {
    val client = createSupabaseClient(
        supabaseUrl = "https://koggimlmvpghifzxqmoh.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtvZ2dpbWxtdnBnaGlmenhxbW9oIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQxNDg4MjgsImV4cCI6MjA2OTcyNDgyOH0._aiUR6eOs_yVHcvhwHdlPP4SV2csRChop24oTlhrRwU"
    ) {
        install(Auth)
    }
}


