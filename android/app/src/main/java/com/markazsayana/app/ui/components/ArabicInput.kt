package com.markazsayana.app.ui.components

/** Accept either common Arabic digit set in phone numbers and appointment fields. */
fun westernDigits(value: String): String = buildString(value.length) {
    value.forEach { character ->
        append(when (character) {
            in '٠'..'٩' -> '0' + (character - '٠')
            in '۰'..'۹' -> '0' + (character - '۰')
            else -> character
        })
    }
}
