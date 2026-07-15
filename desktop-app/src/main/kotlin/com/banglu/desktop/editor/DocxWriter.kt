package com.banglu.desktop.editor

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal OOXML by hand — deliberately no POI/docx4j (spec §6). A .docx is a
 * zip of three XML parts; Word/Pages/LibreOffice do the Bangla shaping
 * themselves, so conjuncts are correct even without our bundled font.
 * w:cs (complex script) attributes are REQUIRED — without them Word picks a
 * Latin font for Bengali runs and sizes them wrong.
 */
object DocxWriter {

    private const val FONTS =
        """<w:rFonts w:ascii="Noto Sans Bengali" w:hAnsi="Noto Sans Bengali" w:cs="Noto Sans Bengali"/>"""
    private const val RPR = """<w:rPr>$FONTS<w:sz w:val="28"/><w:szCs w:val="28"/><w:cs/></w:rPr>"""

    fun write(text: String, out: File) {
        ZipOutputStream(out.outputStream()).use { zip ->
            zip.put("[Content_Types].xml", CONTENT_TYPES)
            zip.put("_rels/.rels", RELS)
            zip.put("word/document.xml", documentXml(text))
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }

    private fun documentXml(text: String): String {
        val paragraphs = text.split("\n").joinToString("") { line ->
            "<w:p><w:pPr>$RPR</w:pPr>" +
                (if (line.isEmpty()) "" else "<w:r>$RPR<w:t xml:space=\"preserve\">${escape(line)}</w:t></w:r>") +
                "</w:p>"
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            "<w:body>$paragraphs</w:body></w:document>"
    }

    private fun escape(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private val RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
}
