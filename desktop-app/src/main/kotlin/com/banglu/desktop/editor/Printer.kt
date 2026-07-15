package com.banglu.desktop.editor

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import java.text.AttributedString

/**
 * প্রিন্ট / PDF (spec §6): the OS print dialog — macOS offers "Save as PDF",
 * Windows "Microsoft Print to PDF". Java2D + LineBreakMeasurer shape Bengali
 * correctly; direct-PDF Java libraries do NOT (no complex-script shaping),
 * which is why there is deliberately no PDF library here.
 */
object Printer {

    fun print(text: String, jobName: String): Boolean {
        val job = PrinterJob.getPrinterJob()
        job.setJobName(jobName)
        job.setPrintable(BanglaPrintable(text, jobName))
        if (!job.printDialog()) return false
        job.print()
        return true
    }

    private class BanglaPrintable(private val text: String, private val header: String) : Printable {
        private val body = AwtBengaliFont.deriveFont(14f)
        private val headerFont = AwtBengaliFont.deriveFont(9f)

        override fun print(g: java.awt.Graphics, pf: PageFormat, pageIndex: Int): Int {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val x = pf.imageableX.toFloat() + 24f
            val width = pf.imageableWidth.toFloat() - 48f
            val top = pf.imageableY.toFloat() + 40f
            val bottom = (pf.imageableY + pf.imageableHeight).toFloat() - 32f

            // Lay out ALL lines once, then draw only this page's slice.
            data class Line(val draw: (Graphics2D, Float) -> Unit, val ascent: Float, val descent: Float)
            val lines = mutableListOf<Line>()
            for (para in text.split("\n")) {
                if (para.isEmpty()) {
                    val lm = g2.getFontMetrics(body)
                    lines.add(Line({ _, _ -> }, lm.ascent.toFloat(), lm.descent + lm.leading.toFloat()))
                    continue
                }
                val attr = AttributedString(para, mapOf(TextAttribute.FONT to body))
                val measurer = LineBreakMeasurer(attr.iterator, g2.fontRenderContext)
                while (measurer.position < para.length) {
                    val layout = measurer.nextLayout(width)
                    lines.add(Line({ g, y -> layout.draw(g, x, y) }, layout.ascent, layout.descent + layout.leading))
                }
            }

            val pageHeight = bottom - top
            var page = 0
            var y = 0f
            var i = 0
            val start = mutableListOf(0)
            while (i < lines.size) {
                val h = lines[i].ascent + lines[i].descent
                if (y + h > pageHeight && y > 0f) { start.add(i); y = 0f; page++ }
                y += h; i++
            }
            if (pageIndex > page) return Printable.NO_SUCH_PAGE

            g2.font = headerFont
            g2.drawString(header, x, pf.imageableY.toFloat() + 18f)

            var drawY = top
            val from = start[pageIndex]
            val to = if (pageIndex + 1 < start.size) start[pageIndex + 1] else lines.size
            for (j in from until to) {
                drawY += lines[j].ascent
                lines[j].draw(g2, drawY)
                drawY += lines[j].descent
            }
            return Printable.PAGE_EXISTS
        }
    }
}
