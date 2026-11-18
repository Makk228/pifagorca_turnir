package ru.mentalarena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class XlsxLibraryParserTest {
  @Test
  void readsVerticalChainsAndProducts() throws Exception {
    var examples = new XlsxLibraryParser().parse(workbook());

    assertThat(examples)
        .containsExactlyInAnyOrder(
            new XlsxLibraryParser.Example(1, 2, "SUBTRACTION", "8 - 3", 5),
            new XlsxLibraryParser.Example(2, 2, "MULTIPLICATION", "24 × 3", 72));
  }

  private byte[] workbook() throws Exception {
    String workbook = """
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="1D2R" sheetId="1" r:id="rId1"/>
          <sheet name="2D×1D" sheetId="2" r:id="rId2"/></sheets>
        </workbook>
        """;
    String rels = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Target="worksheets/sheet2.xml"/>
        </Relationships>
        """;
    String chain = """
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
          <row r="1"><c r="A1" t="inlineStr"><is><t>№1</t></is></c></row>
          <row r="2"><c r="A2"><v>8</v></c></row>
          <row r="3"><c r="A3"><v>-3</v></c></row>
        </sheetData></worksheet>
        """;
    String product = """
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
          <row r="1"><c r="A1" t="inlineStr"><is><t>№1 24 × 3 =</t></is></c></row>
        </sheetData></worksheet>
        """;
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      for (var entry : Map.of(
          "xl/workbook.xml", workbook,
          "xl/_rels/workbook.xml.rels", rels,
          "xl/worksheets/sheet1.xml", chain,
          "xl/worksheets/sheet2.xml", product).entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }
}
