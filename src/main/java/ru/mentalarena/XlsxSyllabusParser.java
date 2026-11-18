package ru.mentalarena;

import static ru.mentalarena.Db.require;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

@Component
class XlsxSyllabusParser {
  private static final Pattern CHAIN =
      Pattern.compile("^(\\d)\\s*[RРDД]\\s*(\\d)\\s*[DДRР]$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PRODUCT = Pattern.compile("^(\\d)\\s*([*x×/÷])\\s*(\\d)$");
  private static final int MAX_UNCOMPRESSED = 20 * 1024 * 1024;

  record Rule(
      String ageBand,
      String category,
      String competitionCategory,
      int position,
      int digits,
      int terms,
      String operation,
      int count) {}

  record Parsed(List<Rule> rules, int preschoolCount, int schoolCount) {}

  Parsed parse(byte[] bytes) {
    var entries = unzip(bytes);
    require(entries.containsKey("xl/workbook.xml"), "Файл не похож на книгу Excel .xlsx");
    var shared = sharedStrings(entries.get("xl/sharedStrings.xml"));
    var relationships = relationships(entries.get("xl/_rels/workbook.xml.rels"));
    var workbook = xml(entries.get("xl/workbook.xml"));
    var result = new ArrayList<Rule>();
    var sheets = workbook.getElementsByTagNameNS("*", "sheet");
    for (int i = 0; i < sheets.getLength(); i++) {
      var sheet = (Element) sheets.item(i);
      String relation = sheet.getAttribute("r:id");
      if (relation.isBlank())
        relation = sheet.getAttributeNS(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
      String target = relationships.get(relation);
      if (target == null) continue;
      String path = normalize(target.startsWith("/") ? target.substring(1) : "xl/" + target);
      byte[] source = entries.get(path);
      if (source != null) parseSheet(cells(source, shared), result);
    }
    require(!result.isEmpty(),
        "В Excel не найдены возрастные блоки и колонки NF, F5, F10+, F10 или FF");
    var totals = new LinkedHashMap<String, Integer>();
    for (Rule rule : result)
      totals.merge(rule.ageBand() + ":" + rule.category() + ":" + rule.competitionCategory(),
          rule.count(), Integer::sum);
    int preschool = consistentTotal(totals, "BABY");
    int school = consistentTotal(totals, "SCHOOL");
    require(preschool > 0 || school > 0, "В силлабусе нет правил с количеством примеров");
    return new Parsed(List.copyOf(result), preschool, school);
  }

  private int consistentTotal(Map<String, Integer> totals, String category) {
    var values = totals.entrySet().stream()
        .filter(e -> e.getKey().contains(":" + category + ":"))
        .map(Map.Entry::getValue).distinct().toList();
    if (values.isEmpty()) return 0;
    require(values.size() == 1,
        "Во всех возрастах и категориях «" + (category.equals("BABY") ? "Дошкольники" : "Школьники")
            + "» должно быть одинаковое итоговое количество примеров");
    int value = values.getFirst();
    require(value >= 1 && value <= 200, "На один день должно быть от 1 до 200 примеров");
    return value;
  }

  private void parseSheet(
      NavigableMap<Integer, Map<Integer, String>> rows, List<Rule> result) {
    String ageBand = null, category = null;
    Map<Integer, String> headers = Map.of();
    var positions = new HashMap<String, Integer>();
    for (var row : rows.entrySet()) {
      String first = row.getValue().getOrDefault(1, "").strip();
      String detectedAge = ageBand(first);
      if (detectedAge != null) {
        ageBand = detectedAge;
        category = first.toLowerCase(Locale.ROOT).contains("дошколь") ? "BABY" : "SCHOOL";
        headers = Map.of();
        continue;
      }
      if (ageBand == null) continue;
      var foundHeaders = new LinkedHashMap<Integer, String>();
      for (var cell : row.getValue().entrySet()) {
        String competition = competition(cell.getValue());
        if (competition != null) foundHeaders.put(cell.getKey(), competition);
      }
      if (!foundHeaders.isEmpty()) {
        headers = foundHeaders;
        continue;
      }
      for (var header : headers.entrySet()) {
        String notation = row.getValue().getOrDefault(header.getKey(), "").strip();
        String rawCount = row.getValue().getOrDefault(header.getKey() + 1, "").strip();
        if (notation.isBlank() || rawCount.isBlank()) continue;
        int count = integer(rawCount);
        require(count >= 1 && count <= 200, "Количество в силлабусе должно быть от 1 до 200");
        String key = ageBand + ":" + category + ":" + header.getValue();
        var chain = CHAIN.matcher(notation.replace(" ", ""));
        var product = PRODUCT.matcher(notation.replace(" ", ""));
        if (chain.matches()) {
          int digits = Integer.parseInt(chain.group(1));
          int terms = Integer.parseInt(chain.group(2));
          require(digits >= 1 && digits <= 6 && terms >= 2 && terms <= 7,
              "Параметры «" + notation + "» выходят за диапазон 1D–6D и 2R–7R");
          if (header.getValue().equals("LAWS_PLUS_10")) {
            add(result, positions, key, ageBand, category, header.getValue(),
                digits, terms, "ADDITION", count);
          } else {
            int addition = (count + 1) / 2;
            add(result, positions, key, ageBand, category, header.getValue(),
                digits, terms, "ADDITION", addition);
            if (count > addition)
              add(result, positions, key, ageBand, category, header.getValue(),
                  digits, terms, "SUBTRACTION", count - addition);
          }
        } else if (product.matches()) {
          String operation = Set.of("/", "÷").contains(product.group(2))
              ? "DIVISION" : "MULTIPLICATION";
          int digits = Integer.parseInt(product.group(1));
          add(result, positions, key, ageBand, category, header.getValue(),
              digits, 2, operation, count);
        } else {
          throw new IllegalArgumentException("Не удалось разобрать обозначение «" + notation + "»");
        }
      }
    }
  }

  private void add(List<Rule> result, Map<String, Integer> positions, String key,
      String ageBand, String category, String competition, int digits, int terms,
      String operation, int count) {
    if (count == 0) return;
    int position = positions.merge(key, 1, Integer::sum);
    result.add(new Rule(ageBand, category, competition, position, digits, terms, operation, count));
  }

  private String ageBand(String value) {
    String normalized = value.toLowerCase(Locale.ROOT).replace('–', '-').replace('—', '-');
    if (!normalized.contains("дошколь") && !normalized.contains("школь")) return null;
    if (normalized.contains("4-5")) return "4_5";
    if (normalized.contains("6-7")) return "6_7";
    if (normalized.contains("8-9")) return "8_9";
    if (normalized.contains("10-11")) return "10_11";
    if (normalized.contains("12+")) return "12_PLUS";
    return null;
  }

  private String competition(String value) {
    String v = value.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    return switch (v) {
      case "NF" -> "NO_LAWS";
      case "F5" -> "LAWS_5";
      case "F10+" -> "LAWS_PLUS_10";
      case "F10" -> "LAWS_10";
      case "FF" -> "ALL_LAWS";
      case "*/", "*\\", "×/" -> "SINGLE_DIGIT";
      case "*/МНОГОЗНАЧНЫЕ", "*/МНОГОЗНАЧНОЕ" -> "MULTI_DIGIT";
      default -> null;
    };
  }

  private int integer(String value) {
    try {
      return new BigDecimal(value).intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw new IllegalArgumentException("Количество должно быть целым числом: " + value, e);
    }
  }

  private Map<String, byte[]> unzip(byte[] bytes) {
    var result = new HashMap<String, byte[]>();
    int total = 0, count = 0;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        require(++count <= 500, "В Excel слишком много внутренних файлов");
        if (entry.isDirectory()) continue;
        byte[] content = zip.readNBytes(MAX_UNCOMPRESSED - total + 1);
        total += content.length;
        require(total <= MAX_UNCOMPRESSED, "Excel слишком большой после распаковки");
        result.put(normalize(entry.getName()), content);
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Не удалось прочитать Excel", e);
    }
    return result;
  }

  private String normalize(String path) {
    var stack = new ArrayDeque<String>();
    for (String part : path.replace('\\', '/').split("/")) {
      if (part.equals("..")) { if (!stack.isEmpty()) stack.removeLast(); }
      else if (!part.isBlank() && !part.equals(".")) stack.addLast(part);
    }
    return String.join("/", stack);
  }

  private List<String> sharedStrings(byte[] source) {
    if (source == null) return List.of();
    var result = new ArrayList<String>();
    var nodes = xml(source).getElementsByTagNameNS("*", "si");
    for (int i = 0; i < nodes.getLength(); i++) result.add(text(nodes.item(i)));
    return result;
  }

  private Map<String, String> relationships(byte[] source) {
    require(source != null, "В Excel отсутствуют связи листов");
    var result = new HashMap<String, String>();
    var nodes = xml(source).getElementsByTagNameNS("*", "Relationship");
    for (int i = 0; i < nodes.getLength(); i++) {
      var relation = (Element) nodes.item(i);
      result.put(relation.getAttribute("Id"), relation.getAttribute("Target"));
    }
    return result;
  }

  private NavigableMap<Integer, Map<Integer, String>> cells(byte[] source, List<String> shared) {
    var result = new TreeMap<Integer, Map<Integer, String>>();
    var nodes = xml(source).getElementsByTagNameNS("*", "c");
    for (int i = 0; i < nodes.getLength(); i++) {
      var cell = (Element) nodes.item(i);
      var address = Pattern.compile("([A-Z]+)(\\d+)").matcher(cell.getAttribute("r"));
      if (!address.matches()) continue;
      int column = column(address.group(1)), row = Integer.parseInt(address.group(2));
      String type = cell.getAttribute("t"), value = "";
      if (type.equals("inlineStr")) {
        var inline = cell.getElementsByTagNameNS("*", "is");
        if (inline.getLength() > 0) value = text(inline.item(0));
      } else {
        var values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() > 0) value = values.item(0).getTextContent();
        if (type.equals("s") && !value.isBlank()) value = shared.get(Integer.parseInt(value));
      }
      if (!value.isBlank()) result.computeIfAbsent(row, ignored -> new TreeMap<>()).put(column, value);
    }
    return result;
  }

  private int column(String letters) {
    int value = 0;
    for (char letter : letters.toCharArray()) value = value * 26 + letter - 'A' + 1;
    return value;
  }

  private String text(Node node) {
    var result = new StringBuilder();
    var values = ((Element) node).getElementsByTagNameNS("*", "t");
    for (int i = 0; i < values.getLength(); i++) result.append(values.item(i).getTextContent());
    return result.toString();
  }

  private Document xml(byte[] source) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(source));
    } catch (Exception e) {
      throw new IllegalArgumentException("Не удалось прочитать внутреннюю структуру Excel", e);
    }
  }
}
