package com.github.klboke.kkrepo.server.security;

import com.github.klboke.kkrepo.protocol.maven.path.Coordinates;
import com.github.klboke.kkrepo.protocol.maven.path.MavenPathParser;
import com.github.klboke.kkrepo.protocol.npm.NpmPath;
import com.github.klboke.kkrepo.protocol.npm.NpmPathParser;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ContentSelectorExpressionEvaluator {
  private static final MavenPathParser MAVEN_PATH_PARSER = new MavenPathParser();
  private static final NpmPathParser NPM_PATH_PARSER = new NpmPathParser();

  private ContentSelectorExpressionEvaluator() {
  }

  static boolean matches(String expression, String repository, String format, String path) {
    if (expression == null || expression.isBlank()) {
      return true;
    }
    try {
      return new Parser(new Lexer(expression).lex(), variables(repository, format, path)).parse();
    } catch (RuntimeException e) {
      return false;
    }
  }

  static String nexusFormat(String format) {
    if (format == null || format.isBlank()) {
      return "*";
    }
    return format.trim().toLowerCase(Locale.ROOT);
  }

  private static Map<String, String> variables(String repository, String format, String path) {
    String normalizedPath = stripLeadingSlashes(path == null ? "" : path);
    String normalizedFormat = nexusFormat(format);
    Map<String, String> values = new HashMap<>();
    put(values, "path", normalizedPath);
    put(values, "name", normalizedPath);
    put(values, "asset.name", normalizedPath);
    put(values, "asset.path", normalizedPath);
    put(values, "content.path", normalizedPath);
    put(values, "format", normalizedFormat);
    put(values, "component.format", normalizedFormat);
    put(values, "asset.format", normalizedFormat);
    put(values, "repository", repository);
    put(values, "repository.name", repository);
    put(values, "repository.format", normalizedFormat);
    addFormatCoordinates(values, normalizedFormat, normalizedPath);
    return values;
  }

  private static void addFormatCoordinates(Map<String, String> values, String format, String path) {
    if ("maven2".equals(format)) {
      Coordinates coordinates = MAVEN_PATH_PARSER.parsePath(path).coordinates();
      if (coordinates != null) {
        put(values, "coordinate.groupId", coordinates.groupId());
        put(values, "coordinate.artifactId", coordinates.artifactId());
        put(values, "coordinate.version", coordinates.baseVersion());
        put(values, "coordinate.baseVersion", coordinates.baseVersion());
        put(values, "coordinate.extension", coordinates.extension());
        put(values, "coordinate.classifier", stringOrEmpty(coordinates.classifier()));
      }
    } else if ("npm".equals(format)) {
      NpmPath npmPath = NPM_PATH_PARSER.parse(path);
      if (npmPath.packageId() != null) {
        put(values, "coordinate.name", npmPath.packageId().id());
        put(values, "coordinate.scope", npmPath.packageId().scope());
        put(values, "coordinate.version", npmPath.packageVersion());
        put(values, "package.name", npmPath.packageId().id());
        put(values, "package.scope", npmPath.packageId().scope());
      }
    }
  }

  private static void put(Map<String, String> values, String key, String value) {
    if (value != null) {
      values.put(key, value);
    }
  }

  private static String stripLeadingSlashes(String value) {
    String result = value;
    while (result.startsWith("/")) {
      result = result.substring(1);
    }
    return result;
  }

  private static String stringOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private static boolean safeRegexMatches(String regex, String value) {
    String normalizedRegex = regex == null ? "" : regex;
    String normalizedValue = value == null ? "" : value;
    Boolean simpleNegativeLookahead = simpleLeadingNegativeLookaheadMatches(normalizedRegex, normalizedValue);
    if (simpleNegativeLookahead != null) {
      return simpleNegativeLookahead;
    }
    try {
      return Pattern.matches(normalizedRegex, normalizedValue);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  private static Boolean simpleLeadingNegativeLookaheadMatches(String regex, String value) {
    if (!regex.startsWith("(?!")) {
      return null;
    }
    int end = regex.indexOf(')', 3);
    if (end < 0) {
      return null;
    }
    String forbiddenRegex = regex.substring(3, end);
    String tailRegex = regex.substring(end + 1);
    if (forbiddenRegex.contains("(?") || tailRegex.contains("(?")) {
      return null;
    }
    try {
      return !Pattern.compile(forbiddenRegex).matcher(value).lookingAt()
          && Pattern.matches(tailRegex.isBlank() ? ".*" : tailRegex, value);
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  private record Token(TokenType type, String value) {
  }

  private enum TokenType {
    IDENTIFIER,
    STRING,
    TRUE,
    FALSE,
    EQ,
    NE,
    REGEX,
    STARTS_WITH,
    AND,
    OR,
    NOT,
    LPAREN,
    RPAREN,
    EOF
  }

  private static final class Lexer {
    private final String input;
    private int index;

    private Lexer(String input) {
      this.input = input;
    }

    private java.util.List<Token> lex() {
      java.util.List<Token> tokens = new java.util.ArrayList<>();
      while (index < input.length()) {
        char c = input.charAt(index);
        if (Character.isWhitespace(c)) {
          index++;
        } else if (c == '(') {
          tokens.add(new Token(TokenType.LPAREN, "("));
          index++;
        } else if (c == ')') {
          tokens.add(new Token(TokenType.RPAREN, ")"));
          index++;
        } else if (c == '\'' || c == '"') {
          tokens.add(new Token(TokenType.STRING, string(c)));
        } else if (startsWith("==")) {
          tokens.add(new Token(TokenType.EQ, "=="));
          index += 2;
        } else if (startsWith("!=")) {
          tokens.add(new Token(TokenType.NE, "!="));
          index += 2;
        } else if (startsWith("=~")) {
          tokens.add(new Token(TokenType.REGEX, "=~"));
          index += 2;
        } else if (startsWith("=^")) {
          tokens.add(new Token(TokenType.STARTS_WITH, "=^"));
          index += 2;
        } else if (startsWith("&&")) {
          tokens.add(new Token(TokenType.AND, "&&"));
          index += 2;
        } else if (startsWith("||")) {
          tokens.add(new Token(TokenType.OR, "||"));
          index += 2;
        } else if (c == '!') {
          tokens.add(new Token(TokenType.NOT, "!"));
          index++;
        } else if (isIdentifierStart(c)) {
          tokens.add(identifier());
        } else {
          throw new IllegalArgumentException("Unsupported CSEL token at " + index);
        }
      }
      tokens.add(new Token(TokenType.EOF, ""));
      return tokens;
    }

    private boolean startsWith(String token) {
      return input.startsWith(token, index);
    }

    private String string(char quote) {
      index++;
      StringBuilder result = new StringBuilder();
      while (index < input.length()) {
        char c = input.charAt(index++);
        if (c == quote) {
          return result.toString();
        }
        if (c == '\\' && index < input.length()) {
          char escaped = input.charAt(index++);
          result.append(switch (escaped) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> escaped;
          });
        } else {
          result.append(c);
        }
      }
      throw new IllegalArgumentException("Unterminated string literal");
    }

    private Token identifier() {
      int start = index;
      index++;
      while (index < input.length() && isIdentifierPart(input.charAt(index))) {
        index++;
      }
      String value = input.substring(start, index);
      return switch (value) {
        case "true" -> new Token(TokenType.TRUE, value);
        case "false" -> new Token(TokenType.FALSE, value);
        case "and" -> new Token(TokenType.AND, value);
        case "or" -> new Token(TokenType.OR, value);
        case "not" -> new Token(TokenType.NOT, value);
        default -> new Token(TokenType.IDENTIFIER, value);
      };
    }

    private boolean isIdentifierStart(char c) {
      return Character.isLetter(c) || c == '_' || c == '@';
    }

    private boolean isIdentifierPart(char c) {
      return Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == '@';
    }
  }

  private record Value(String text, Boolean bool, String variable) {
    private boolean truthy() {
      if (bool != null) {
        return bool;
      }
      return text != null && !text.isBlank();
    }
  }

  private static final class Parser {
    private final java.util.List<Token> tokens;
    private final Map<String, String> variables;
    private int index;

    private Parser(java.util.List<Token> tokens, Map<String, String> variables) {
      this.tokens = tokens;
      this.variables = variables;
    }

    private boolean parse() {
      boolean result = or();
      expect(TokenType.EOF);
      return result;
    }

    private boolean or() {
      boolean result = and();
      while (match(TokenType.OR)) {
        result = result | and();
      }
      return result;
    }

    private boolean and() {
      boolean result = unary();
      while (match(TokenType.AND)) {
        result = result & unary();
      }
      return result;
    }

    private boolean unary() {
      if (match(TokenType.NOT)) {
        return !unary();
      }
      return primary();
    }

    private boolean primary() {
      if (match(TokenType.LPAREN)) {
        boolean result = or();
        expect(TokenType.RPAREN);
        return result;
      }

      Value left = operand();
      if (match(TokenType.EQ)) {
        return equalsValue(left, operand());
      }
      if (match(TokenType.NE)) {
        return !equalsValue(left, operand());
      }
      if (match(TokenType.REGEX)) {
        return regexMatches(left, regexOperand());
      }
      if (match(TokenType.STARTS_WITH)) {
        return startsWith(left, operand());
      }
      return left.truthy();
    }

    private Value operand() {
      Token token = peek();
      if (match(TokenType.STRING)) {
        return new Value(token.value(), null, null);
      }
      if (match(TokenType.TRUE)) {
        return new Value("true", true, null);
      }
      if (match(TokenType.FALSE)) {
        return new Value("false", false, null);
      }
      if (match(TokenType.IDENTIFIER)) {
        return new Value(variables.get(token.value()), null, token.value());
      }
      throw new IllegalArgumentException("Expected operand: " + token.type());
    }

    private Value regexOperand() {
      Token token = peek();
      if (match(TokenType.STRING)) {
        return new Value(token.value(), null, null);
      }
      throw new IllegalArgumentException("Expected regex string literal: " + token.type());
    }

    private boolean equalsValue(Value left, Value right) {
      if (isCoordinateExtension(left) || isCoordinateExtension(right)) {
        return stripLeadingDots(defaultString(left.text()))
            .equals(stripLeadingDots(defaultString(right.text())));
      }
      if (isPath(left) || isPath(right)) {
        if (left.text() == null || right.text() == null) {
          return left.text() == right.text();
        }
        return stripLeadingSlashes(defaultString(left.text()))
            .equals(stripLeadingSlashes(defaultString(right.text())));
      }
      if (left.bool() != null || right.bool() != null) {
        return left.truthy() == right.truthy();
      }
      return Objects.equals(left.text(), right.text());
    }

    private boolean regexMatches(Value left, Value right) {
      String value = defaultString(left.text());
      String regex = defaultString(right.text());
      if (safeRegexMatches(regex, value)) {
        return true;
      }
      if (isPath(left) && !value.startsWith("/") && safeRegexMatches(regex, "/" + value)) {
        return true;
      }
      return isPath(left) && safeRegexMatches(stripSimpleLeadingSlashRegex(regex), value);
    }

    private boolean startsWith(Value left, Value right) {
      String value = defaultString(left.text());
      String prefix = defaultString(right.text());
      if (isPath(left) || isPath(right)) {
        value = stripLeadingSlashes(value);
        prefix = stripLeadingSlashes(prefix);
      }
      return value.startsWith(prefix);
    }

    private static boolean isPath(Value value) {
      return "path".equals(value.variable())
          || "name".equals(value.variable())
          || "asset.name".equals(value.variable())
          || "asset.path".equals(value.variable())
          || "content.path".equals(value.variable());
    }

    private static boolean isCoordinateExtension(Value value) {
      return "coordinate.extension".equals(value.variable());
    }

    private static String stripLeadingDots(String value) {
      String result = defaultString(value);
      while (result.startsWith(".")) {
        result = result.substring(1);
      }
      return result;
    }

    private static String stripSimpleLeadingSlashRegex(String regex) {
      if (regex.startsWith("^/")) {
        return "^" + regex.substring(2);
      }
      if (regex.startsWith("/")) {
        return regex.substring(1);
      }
      return regex;
    }

    private static String defaultString(String value) {
      return value == null ? "" : value;
    }

    private boolean match(TokenType type) {
      if (peek().type() == type) {
        index++;
        return true;
      }
      return false;
    }

    private void expect(TokenType type) {
      Token token = peek();
      if (token.type() != type) {
        throw new IllegalArgumentException("Expected " + type + " but got " + token.type());
      }
      index++;
    }

    private Token peek() {
      return tokens.get(index);
    }
  }
}
