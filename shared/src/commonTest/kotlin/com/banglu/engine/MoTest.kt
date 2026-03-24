package com.banglu.engine
import kotlin.test.Test
class MoTest {
    @Test fun test() {
        val e = SmartEngine(); e.initializeSync()
        for (w in listOf("mongla","moy","mohol","moyna","moyur","moyna","mongol","mondo","montri","mondir","monhush","morjada"))
            println("$w|${e.convertWord(w).bengali}")
    }
}
