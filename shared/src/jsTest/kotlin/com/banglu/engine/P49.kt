package com.banglu.engine
import kotlin.test.Test

// S74: `require` resolved at module load broke the BROWSER test target
// (:shared:check runs jsBrowserTest too) — this probe is Node-only, so it
// now resolves fs lazily and skips cleanly where require doesn't exist.
private fun nodeFs(): dynamic = if (js("typeof require") != "undefined") js("require('fs')") else null

class P49 {
  @Test fun p() {
    val fs2 = nodeFs() ?: return // Node-only probe; browser target skips
    val e = SmartEngine(); e.initializeSync()
    println("P49 seedonly kmon -> " + e.convertWord("kmon").let{"${it.bengali}/${it.source}/${it.confidence}"})
    val path = "/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp/shared/banglu-slim.json"
    BangluWebEngine.attachSlimDictionary(fs2.readFileSync(path, "utf8") as String)
    println("P49 slim kmon -> " + BangluWebEngine.convert("kmon"))
    println("P49 slim sugg -> " + BangluWebEngine.suggestions("kmon", 5).joinToString("|"))
  }
}
