package org.gcrel.tfbackevent.unit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.gcrel.tfbackevent.report.service.Csv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvTest {
    @Test
    void row_JoinsCellsWithCommasAndEndsWithCrLf() {
        assertEquals("Ana,Usada,2026-03-20 09:15:00\r\n", Csv.row("Ana", "Usada", "2026-03-20 09:15:00"));
    }

    @Test
    void row_LeavesNullCellsEmpty() {
        assertEquals("Ana,,\r\n", Csv.row("Ana", null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "=1+1", "+54911", "-5", "@SUM(A1)", "\tTabulado", "\rRetorno" })
    void row_NeutralizesCellsThatExcelWouldRunAsFormulas(String name) {
        String row = Csv.row(name);
        assertEquals('\'', row.charAt(row.startsWith("\"") ? 1 : 0));
    }

    @Test
    void row_PrefixesAnApostropheOnlyAtTheStart() {
        assertEquals("'=1+1\r\n", Csv.row("=1+1"));
        assertEquals("a=1\r\n", Csv.row("a=1"));
        assertEquals("a-b\r\n", Csv.row("a-b"));
    }

    @Test
    void row_QuotesCellsWithCommasQuotesOrLineBreaksAndDoublesTheQuotes() {
        assertEquals("\"García, Ana\"\r\n", Csv.row("García, Ana"));
        assertEquals("\"Ana \"\"la jefa\"\"\"\r\n", Csv.row("Ana \"la jefa\""));
        assertEquals("\"línea1\nlínea2\"\r\n", Csv.row("línea1\nlínea2"));
    }

    @Test
    void row_NeutralizesAndQuotesAFormulaWithACommaInOneStep() {
        assertEquals("\"'=A1,B1\"\r\n", Csv.row("=A1,B1"));
    }

    @Test
    void bom_IsTheUtf8ByteOrderMarkThatExcelNeedsForAccents() {
        assertEquals(3, Csv.BOM.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(0xEF, Csv.BOM.getBytes(StandardCharsets.UTF_8)[0] & 0xFF);
        assertEquals(0xBB, Csv.BOM.getBytes(StandardCharsets.UTF_8)[1] & 0xFF);
        assertEquals(0xBF, Csv.BOM.getBytes(StandardCharsets.UTF_8)[2] & 0xFF);
    }
}
