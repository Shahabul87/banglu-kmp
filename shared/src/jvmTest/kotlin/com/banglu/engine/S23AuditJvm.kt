package com.banglu.engine

import kotlin.test.Test

class S23AuditJvm {
    @Test
    fun audit() {
        if (System.getenv("PROBE") == null) return
        val e = ConjunctSolutionRoundJvmTest.engine
        val words = listOf(
            "interesting","beautiful","congratulations","amazing","awesome","serious",
            "problem","possible","impossible","available","comfortable","dangerous",
            "different","difficult","expensive","favourite","favorite","festival",
            "government","hospital","important","information","institute","international",
            "medicine","message","minister","necessary","officer","operation",
            "personal","picture","politics","popular","position","practice",
            "president","professor","program","project","question","relation",
            "religion","remember","request","research","response","restaurant",
            "science","security","service","situation","society","special",
            "station","student","subject","success","support","surprise",
            "teacher","technology","television","temperature","tradition","training",
            "transport","treatment","understand","welcome","wonderful","yesterday",
            "battery","brilliant","business","camera","career","celebrate",
            "chocolate","college","community","company","condition","confirm",
            "connection","culture","decision","delicious","development","directly",
            "discussion","education","election","electric","emergency","engineer",
            "entertainment","environment","exactly","examination","excellent","exercise",
            "experience","family","fantastic","fashion","feeling","freedom"
        )
        for (k in words) {
            val r = e.convertWord(k)
            val valid = e.getSuggestions(k, 1) // warm
            println("PROBE-AUDIT $k -> ${r.bengali} (${r.source})")
        }
    }
}
