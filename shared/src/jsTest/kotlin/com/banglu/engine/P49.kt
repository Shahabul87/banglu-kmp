package com.banglu.engine
import kotlin.test.Test
import kotlin.test.assertTrue

// S74/S76: Node-only probe made portable — lazily resolves fs (browser
// bundles can't), skips when the untracked slim JSON is absent (clean
// clones/CI), and actually asserts instead of only printing.
private fun nodeFs(): dynamic = if (js("typeof require") != "undefined") js("require('fs')") else null

class P49 {
  @Test fun p() {
    val fs2 = nodeFs() ?: return // Node-only probe; browser target skips
    val e = SmartEngine(); e.initializeSync()
    val seed = e.convertWord("kmon")
    println("P49 seedonly kmon -> ${seed.bengali}/${seed.source}/${seed.confidence}")
    assertTrue(seed.bengali.isNotEmpty(), "seed conversion produced nothing for kmon")

    val path = "/Users/mdshahabulalam/myprojects/banlgu/banglu-kmp/shared/banglu-slim.json"
    if (!(fs2.existsSync(path) as Boolean)) {
      println("P49: slim json absent at $path — skipping slim half")
      return
    }
    BangluWebEngine.attachSlimDictionary(fs2.readFileSync(path, "utf8") as String)
    val slim = BangluWebEngine.convert("kmon")
    println("P49 slim kmon -> $slim")
    println("P49 slim sugg -> " + BangluWebEngine.suggestions("kmon", 5).joinToString("|"))
    assertTrue(slim.any { it in 'ঀ'..'৿' }, "slim conversion for kmon is not Bengali: $slim")
  }
}
