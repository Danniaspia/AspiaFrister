package dk.aspia.frister

/** Aspias egne indstillinger. Ret her og byg igen. */
object Config {
    /** Hvor Hjælp-beskeder ender – bruges også, hvis appen må falde tilbage til kundens mail-app. */
    const val ASPIA_EMAIL = "danni.blomquist@aspia.dk"

    /**
     * Adgangsnøgle fra web3forms.com (oprettes på Aspias modtageradresse).
     * Er den tom, åbner Hjælp i stedet kundens mail-app med en udfyldt mail.
     */
    const val WEB3FORMS_KEY = ""

    const val WEB3FORMS_URL = "https://api.web3forms.com/submit"
}
