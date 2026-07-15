package com.banglu.desktop.editor

import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocxWriterTest {
    @Test
    fun writesAValidDocxContainingTheExactBangla() {
        val out = File(createTempDirectory("banglu-test").toFile(), "চিঠি.docx")
        DocxWriter.write("আমার প্রিয় বন্ধু,\nকেমন আছো? <3 & সব ভালো\n\nইতি", out)
        ZipFile(out).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue("word/document.xml" in names)
            assertTrue("[Content_Types].xml" in names)
            assertTrue("_rels/.rels" in names)
            val doc = zip.getInputStream(zip.getEntry("word/document.xml")).readBytes().decodeToString()
            assertTrue("আমার প্রিয় বন্ধু," in doc)
            assertTrue("&lt;3 &amp; সব ভালো" in doc)        // XML-escaped
            assertTrue("Noto Sans Bengali" in doc)
            // 4 paragraphs (empty line = empty paragraph)
            assertEquals(4, Regex("<w:p>").findAll(doc).count())
            // must parse as XML
            javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(zip.getInputStream(zip.getEntry("word/document.xml")))
        }
    }
}
