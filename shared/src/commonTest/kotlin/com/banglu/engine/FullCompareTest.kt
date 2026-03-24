package com.banglu.engine

import kotlin.test.Test

class FullCompareTest {
    @Test fun fullCompare() {
        val e = SmartEngine(); e.initializeSync()
        val words = listOf(
            // oy/ay/ey patterns (trailing য়)
            "moy","hoy","koy","noy","joy","soy","toy","doy","boy","roy",
            "hay","jay","kay","may","nay","pay","say","day","ray","way",
            "key","hey","dey","ley","mey","ney","pey","rey","sey","tey",
            // Common words
            "ami","tumi","apni","se","tar","era","ora","amra","tara","amar",
            "tomar","ki","ke","keno","kokhon","kothay","koto","kemon",
            // Verbs
            "kori","kore","ache","hobe","jai","ase","boli","dekhi","shuni",
            "korbo","korlo","korte","korechi","holo","chhilo","thakbo",
            // ত/ট ন/ণ র/ড়
            "ekti","ekta","pete","ete","du","aponar","kina","karon","por","er",
            "sorokar","bari","gari","dari","taka","desh",
            // শ/ষ/স
            "sundor","proshno","ongsh","shob","sob","sokal","shokal",
            // Conjuncts
            "jonyo","ortho","bondho","modhyo","sotto","matro","byapar",
            "tothyo","otyadhunik","srishti","biswas","rashtra",
            // Daily
            "bangla","bangladesh","dhaka","bhalo","khabar","kharap","ekhon",
            "kintu","ebong","ar","tai","din","rat","ghor","pani","ma","baba",
            "notun","lal","nil","boro","chhoto",
            // Colloquial ো
            "holo","korbo","korlo","chhilo","keno","moto","aro","upor","gota",
            // v/bh
            "bhalo","val","bhai","vai",
            // Pattern edge cases
            "prem","gram","tren","pran","dram","from",
            "kri","pri","tri","bri","gri","sri",
            "kya","bya","tya","dya","sya","mya",
            "ang","ong","ung","eng","ing",
            // More common
            "jol","mon","kaj","kotha","somoy","rasta","bondhu","meye","chhele",
            "shikkha","bidya","gyan","biggan","dunia","otyachar",
            // Numbers  
            "ek","dui","tin","char","panch","chhoy","shat","aat","noy","dosh",
            // Mixed
            "cholun","asun","bosun","dekhi","shuni","jani","pari","chai",
            "khelbo","parbo","jabo","asbo","thakbo","korbo",
            // Ending patterns
            "kora","bola","dekha","shona","jana","mara","dhora","pora",
            "kori","boli","dekhi","shuni","jani","mari","dhori","pori"
        )
        for (w in words) println("$w|${e.convertWord(w).bengali}")
    }
}
