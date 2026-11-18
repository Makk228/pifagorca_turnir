package ru.mentalarena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class XlsxSyllabusParserTest {
  @Test
  void readsCountsAndKeepsNfRowsFromSimpleToComplex() throws Exception {
    var parsed = new XlsxSyllabusParser().parse(workbook());

    assertThat(parsed.preschoolCount()).isEqualTo(60);
    assertThat(parsed.schoolCount()).isEqualTo(70);
    var preschool = parsed.rules().stream()
        .filter(rule -> rule.category().equals("BABY"))
        .toList();
    assertThat(preschool).extracting(XlsxSyllabusParser.Rule::digits)
        .containsExactly(1, 1, 1, 1, 1, 1, 2, 2);
    assertThat(preschool).extracting(XlsxSyllabusParser.Rule::terms)
        .containsExactly(2, 2, 3, 3, 4, 4, 2, 2);
    assertThat(preschool).extracting(XlsxSyllabusParser.Rule::count)
        .containsExactly(8, 7, 8, 7, 5, 5, 10, 10);
  }

  private byte[] workbook() throws Exception {
    String workbook = """
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="Лист1" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
        """;
    String rels = """
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
        </Relationships>
        """;
    String sheet = """
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
          <row r="1"><c r="A1" t="inlineStr"><is><t>дошкольники 4-5</t></is></c></row>
          <row r="2"><c r="A2" t="inlineStr"><is><t>NF</t></is></c><c r="B2" t="inlineStr"><is><t>количество</t></is></c></row>
          <row r="3"><c r="A3" t="inlineStr"><is><t>1R2D</t></is></c><c r="B3"><v>15</v></c></row>
          <row r="4"><c r="A4" t="inlineStr"><is><t>1R3D</t></is></c><c r="B4"><v>15</v></c></row>
          <row r="5"><c r="A5" t="inlineStr"><is><t>1R4D</t></is></c><c r="B5"><v>10</v></c></row>
          <row r="6"><c r="A6" t="inlineStr"><is><t>2R2D</t></is></c><c r="B6"><v>20</v></c></row>
          <row r="8"><c r="A8" t="inlineStr"><is><t>школьники 8-9</t></is></c></row>
          <row r="9"><c r="A9" t="inlineStr"><is><t>NF</t></is></c><c r="B9" t="inlineStr"><is><t>количество</t></is></c></row>
          <row r="10"><c r="A10" t="inlineStr"><is><t>1R2D</t></is></c><c r="B10"><v>70</v></c></row>
        </sheetData></worksheet>
        """;
    var out = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      for (var entry : Map.of(
          "xl/workbook.xml", workbook,
          "xl/_rels/workbook.xml.rels", rels,
          "xl/worksheets/sheet1.xml", sheet).entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return out.toByteArray();
  }
}
