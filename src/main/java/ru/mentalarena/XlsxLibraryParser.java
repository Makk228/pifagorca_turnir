package ru.mentalarena;

import static ru.mentalarena.Db.require;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

@Component
class XlsxLibraryParser {
  private static final Pattern CHAIN = Pattern.compile("^(\\d)D(\\d)R$");
  private static final Pattern PRODUCT = Pattern.compile("^(\\d)D([×x*÷/])(\\d)D$");
  private static final Pattern PRODUCT_EXPRESSION =
      Pattern.compile("(?:№\\s*\\d+\\s*)?(-?\\d+)\\s*([×x*÷/])\\s*(-?\\d+)\\s*=?");
  private static final int MAX_UNCOMPRESSED = 40 * 1024 * 1024;

  record Example(int digits, int terms, String operation, String expression, int answer) {}

  List<Example> parse(byte[] bytes) {
    var entries = unzip(bytes);
    require(entries.containsKey("xl/workbook.xml"), "Файл не похож на книгу Excel .xlsx");
    var shared = sharedStrings(entries.get("xl/sharedStrings.xml"));
    var relationships = relationships(entries.get("xl/_rels/workbook.xml.rels"));
    var workbook = xml(entries.get("xl/workbook.xml"));
    var result = new LinkedHashMap<String, Example>();
    var sheets = workbook.getElementsByTagNameNS("*", "sheet");
    for (int i = 0; i < sheets.getLength(); i++) {
      var sheet = (Element) sheets.item(i);
      String name = sheet.getAttribute("name").trim();
      if (name.toLowerCase(Locale.ROOT).contains("ответ")) continue;
      String relation = sheet.getAttribute("r:id");
      if (relation.isBlank())
        relation = sheet.getAttributeNS(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
      String target = relationships.get(relation);
      require(target != null, "Не удалось прочитать лист «" + name + "»");
      String path = target.startsWith("/") ? target.substring(1) : "xl/" + target;
      path = normalize(path);
      byte[] source = entries.get(path);
      require(source != null, "Не удалось прочитать данные листа «" + name + "»");
      var cells = cells(source, shared);
      var chain = CHAIN.matcher(name);
      var product = PRODUCT.matcher(name);
      if (chain.matches()) {
        parseChain(name, Integer.parseInt(chain.group(1)), Integer.parseInt(chain.group(2)), cells, result);
      } else if (product.matches()) {
        parseProducts(name, Integer.parseInt(product.group(1)), cells, result);
      }
    }
    require(!result.isEmpty(), "В Excel не найдены листы вида 1D2R, 2D×1D или 3D÷1D");
    return new ArrayList<>(result.values());
  }

  private void parseChain(
      String sheet,
      int digits,
      int terms,
      NavigableMap<Integer, Map<Integer, String>> rows,
      Map<String, Example> result) {
    for (var row : rows.entrySet()) {
      for (var cell : row.getValue().entrySet()) {
        if (!cell.getValue().strip().startsWith("№")) continue;
        var numbers = new ArrayList<Integer>();
        for (int offset = 1; offset <= terms; offset++) {
          String value = rows.getOrDefault(row.getKey() + offset, Map.of()).get(cell.getKey());
          require(value != null && !value.isBlank(),
              "На листе «" + sheet + "» не хватает чисел после " + cell.getValue());
          numbers.add(integer(value, sheet));
        }
        boolean positive = false, negative = false;
        var expression = new StringBuilder(Integer.toString(numbers.getFirst()));
        for (int i = 1; i < numbers.size(); i++) {
          int value = numbers.get(i);
          positive |= value >= 0;
          negative |= value < 0;
          expression.append(value < 0 ? " - " : " + ").append(Math.abs(value));
        }
        String operation = negative ? (positive ? "MIXED" : "SUBTRACTION") : "ADDITION";
        int answer = Math.addExact(0, numbers.stream().mapToInt(Integer::intValue).sum());
        add(result, new Example(digits, terms, operation, expression.toString(), answer));
      }
    }
  }

  private void parseProducts(
      String sheet,
      int digits,
      NavigableMap<Integer, Map<Integer, String>> rows,
      Map<String, Example> result) {
    for (var row : rows.values()) {
      for (String value : row.values()) {
        if (!value.strip().startsWith("№")) continue;
        var matcher = PRODUCT_EXPRESSION.matcher(value.strip());
        require(matcher.matches(), "Не удалось разобрать пример «" + value + "» на листе «" + sheet + "»");
        int left = Integer.parseInt(matcher.group(1)), right = Integer.parseInt(matcher.group(3));
        String sign = matcher.group(2);
        boolean division = sign.equals("÷") || sign.equals("/");
        require(!division || right != 0 && left % right == 0,
            "Деление должно иметь целый ответ: «" + value + "»");
        int answer = division ? left / right : Math.multiplyExact(left, right);
        String operation = division ? "DIVISION" : "MULTIPLICATION";
        String expression = left + (division ? " ÷ " : " × ") + right;
        add(result, new Example(digits, 2, operation, expression, answer));
      }
    }
  }

  private void add(Map<String, Example> result, Example example) {
    result.putIfAbsent(example.expression().replaceAll("\\s+", "") + ":" + example.answer(), example);
  }

  private int integer(String value, String sheet) {
    try {
      return new BigDecimal(value.strip()).intValueExact();
    } catch (ArithmeticException | NumberFormatException e) {
      throw new IllegalArgumentException("На листе «" + sheet + "» найдено нецелое число: " + value);
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
      if (part.equals("..")) {
        if (!stack.isEmpty()) stack.removeLast();
      } else if (!part.isBlank() && !part.equals(".")) stack.addLast(part);
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
      String reference = cell.getAttribute("r");
      var address = Pattern.compile("([A-Z]+)(\\d+)").matcher(reference);
      if (!address.matches()) continue;
      int column = column(address.group(1)), row = Integer.parseInt(address.group(2));
      String type = cell.getAttribute("t"), value = "";
      if (type.equals("inlineStr")) {
        var inline = cell.getElementsByTagNameNS("*", "is");
        if (inline.getLength() > 0) value = text(inline.item(0));
      } else {
        var values = cell.getElementsByTagNameNS("*", "v");
        if (values.getLength() > 0) value = values.item(0).getTextContent();
        if (type.equals("s") && !value.isBlank()) {
          int index = Integer.parseInt(value);
          require(index >= 0 && index < shared.size(), "В Excel повреждена таблица строк");
          value = shared.get(index);
        }
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
