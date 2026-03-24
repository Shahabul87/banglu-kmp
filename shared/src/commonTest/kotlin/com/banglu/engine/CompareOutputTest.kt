package com.banglu.engine

import kotlin.test.Test

class CompareOutputTest {
    @Test
    fun compareWords() {
        val engine = SmartEngine()
        engine.initializeSync()
        val words = listOf("ami","tumi","apni","se","bangladesh","bhalo","khabar","sundor","tui","er","jonyo","ekti","aponar","du","karon","kono","upor","holo","aro","moto","por","proshno","ortho","bondho","modhyo","sotto","matro","ekhon","kintu","ar","tai","din","sorokar","sob","val","von","zon","dosh","srishti","rashtra","biswas","gota","korbo","korlo","chhilo","keno","otyadhunik","byapar","tothyo","shahajyo")
        for (w in words) {
            val r = engine.convertWord(w)
            println("$w|${r.bengali}")
        }
    }
}
