package com.xu.wake.data

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

object XlsxReader {

    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024
    private const val MAX_ROWS = 4096
    private const val MAX_COLS = 256

    fun read(input: InputStream): List<List<String?>> {
        var shared: List<String> = emptyList()
        var sheet: ByteArray? = null

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "xl/sharedStrings.xml" -> shared = sharedStrings(readEntry(zip))
                    sheet == null && entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml") ->
                        sheet = readEntry(zip)
                }
            }
        }

        val bytes = sheet ?: throw IllegalArgumentException("文件里没有工作表，可能不是 xlsx")
        return parseSheet(bytes, shared)
    }

    private fun readEntry(zip: ZipInputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            require(out.size() + n <= MAX_ENTRY_BYTES) { "工作表内容过大" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        var current: StringBuilder? = null
        sax(bytes).parse(bytes.inputStream(), object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (qName) {
                    "si" -> current = StringBuilder()
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                current?.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (qName == "si") { out += current?.toString().orEmpty(); current = null }
            }
        })
        return out
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String?>> {
        val grid = mutableListOf<MutableList<String?>>()
        var rowIndex = -1
        var cellRef: String? = null
        var cellType: String? = null
        var cellValue: StringBuilder? = null
        var inlineText: StringBuilder? = null

        sax(bytes).parse(bytes.inputStream(), object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (qName) {
                    "row" -> {
                        val r = attributes?.getValue("r")?.toIntOrNull()
                        rowIndex = if (r != null) r - 1 else rowIndex + 1
                        require(rowIndex < MAX_ROWS) { "工作表行数超出上限" }
                    }
                    "c" -> {
                        cellRef = attributes?.getValue("r")
                        cellType = attributes?.getValue("t")
                        cellValue = StringBuilder()
                        inlineText = null
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (cellType == "inlineStr") {
                    if (inlineText == null) inlineText = StringBuilder()
                    inlineText!!.append(ch, start, length)
                } else cellValue?.append(ch, start, length)
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (qName != "c") return
                val col = columnOf(cellRef)
                val raw = inlineText?.toString() ?: cellValue?.toString().orEmpty()
                val value = when {
                    raw.isEmpty() -> null
                    cellType == "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) }
                    else -> raw
                }
                if (col != null && value != null && rowIndex >= 0 && col < MAX_COLS) put(grid, rowIndex, col, value)
                cellRef = null; cellType = null; cellValue = null; inlineText = null
            }
        })
        return grid
    }

    private fun put(grid: MutableList<MutableList<String?>>, row: Int, col: Int, value: String) {
        while (grid.size <= row) grid.add(mutableListOf())
        val line = grid[row]
        while (line.size <= col) line.add(null)
        line[col] = value
    }

    private fun columnOf(ref: String?): Int? {
        if (ref.isNullOrBlank()) return null
        var value = 0
        var letters = 0
        for (ch in ref) {
            when {
                ch in 'A'..'Z' -> { value = value * 26 + (ch - 'A' + 1); letters++ }
                ch in 'a'..'z' -> { value = value * 26 + (ch - 'a' + 1); letters++ }
                else -> break
            }
        }
        return if (letters == 0) null else value - 1
    }

    private fun sax(bytes: ByteArray) = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newSAXParser()
}
