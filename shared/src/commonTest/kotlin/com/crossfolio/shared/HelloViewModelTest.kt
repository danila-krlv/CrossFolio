package com.crossfolio.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class HelloViewModelTest {
    @Test
    fun greetingIsHello() {
        assertEquals("Hello", HelloViewModel().greeting)
    }
}
