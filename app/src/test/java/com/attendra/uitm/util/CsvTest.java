package com.attendra.uitm.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** CSV export: quoting, empty fields, line endings and file names. */
public class CsvTest {

    @Test
    public void plainFieldsAreNotQuoted() {
        assertEquals("2025000001", Csv.escape("2025000001"));
        assertEquals("", Csv.escape(null));
    }

    @Test
    public void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"Ali, Abu\"", Csv.escape("Ali, Abu"));
        assertEquals("\"Ali \"\"Bob\"\"\"", Csv.escape("Ali \"Bob\""));
        assertEquals("\"line1\nline2\"", Csv.escape("line1\nline2"));
    }

    @Test
    public void rowsStartWithBomAndEndWithCrLf() {
        String csv = new Csv().row("a", null, "c,d").row("e").toString();
        assertEquals("﻿a,,\"c,d\"\r\ne\r\n", csv);
    }

    @Test
    public void fileNamesAreSafe() {
        assertEquals("ICT602_Week_5_lecture_2026-10-12.csv",
                Csv.fileName("ICT602 · Week 5 lecture 2026-10-12"));
        assertEquals("attendance.csv", Csv.fileName("···"));
    }
}
