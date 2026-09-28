package com.example.mediqr.core

import com.example.mediqr.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Single Supabase client shared by the whole app.
 *
 * Credentials are injected at build time from secrets.properties via BuildConfig
 * (SUPABASE_URL / SUPABASE_ANON_KEY). Only the publishable (anon) key ships in the
 * app - all access rules live in Supabase Row Level Security.
 *
 * The Auth module stores the session securely on the device, so users stay signed
 * in across app restarts without the password ever being persisted by this app.
 */
val supabase: SupabaseClient by lazy {
    createSupabaseClient(
        BuildConfig.SUPABASE_URL,
        BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        // PostgREST for the medical profile tables; defaults to the "public"
        // schema and the client's Json (ignoreUnknownKeys, encodeDefaults=false).
        install(Postgrest)
    }
}
