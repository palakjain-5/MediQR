# MediQR release rules for R8.
#
# Most keep rules are shipped by the libraries themselves as consumer rules
# (androidx, kotlinx-serialization, ktor, supabase-kt), so this file only
# needs project-specific additions. Every rule must carry a comment that
# explains why it exists.

# ZXing core is plain Java with no reflection - no keep rules required.
