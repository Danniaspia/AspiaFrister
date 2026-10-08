package dk.aspia.frister

/** Aspias egne indstillinger. Ret her og byg igen. */
object Config {
    /** Hvor Hjælp-beskeder ender (står i Web3Forms-nøglen; her kun til visning). */
    const val ASPIA_EMAIL = "danni.blomquist@aspia.dk"

    /** Ring til Aspia, når kunden er offline. */
    const val ASPIA_PHONE = "+4542746874"
    const val ASPIA_PHONE_DISPLAY = "42 74 68 74"

    /** Adgangsnøgle fra web3forms.com. Tom = Hjælp kan kun ringe eller lægge i kø. */
    const val WEB3FORMS_KEY = ""
    const val WEB3FORMS_URL = "https://api.web3forms.com/submit"

    /**
     * Supabase-projektet med bogholdernes momsestimater (Project Settings → API).
     * Tomme = appen viser kun frister.
     * ⚠️ Testversion: nøglen er offentlig og giver adgang til alle rækker – kun fiktive tal.
     */
    const val SUPABASE_URL = "https://trlhuoqmzcbbzysagchh.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_ASHucX1ST305gp4fnykgmg_YPz-TSTj"
}
