package dev.roastbench.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PythonLiteralParserTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        assertThat(o).isInstanceOf(Map.class);
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        assertThat(o).isInstanceOf(List.class);
        return (List<Object>) o;
    }

    @Test
    void parsesPythonDictWithSingleQuotesAndPythonConstants() {
        Map<String, Object> map = asMap(PythonLiteralParser.parse(
                "{'title': 'Ethiopia Guji', 'viewerMode': False, 'x': None, 'ok': True, 'n': 12, 'f': -3.5e1,"
                        + " 'list': [1, 2.5, 'a'], 'nested': {'k': [ ]}}"));

        assertThat(map.get("title")).isEqualTo("Ethiopia Guji");
        assertThat(map.get("viewerMode")).isEqualTo(false);
        assertThat(map.get("ok")).isEqualTo(true);
        assertThat(map).containsKey("x");
        assertThat(map.get("x")).isNull();
        assertThat(map.get("n")).isEqualTo(12L);
        assertThat(map.get("f")).isEqualTo(-35.0);
        assertThat(asList(map.get("list"))).containsExactly(1L, 2.5, "a");
        assertThat(asMap(map.get("nested")).get("k")).isEqualTo(List.of());
    }

    @Test
    void parsesPlainJsonToo() {
        Map<String, Object> map =
                asMap(PythonLiteralParser.parse("{\"a\": [1, 2, 3], \"b\": true, \"c\": null, \"d\": \"x\"}"));

        assertThat(asList(map.get("a"))).containsExactly(1L, 2L, 3L);
        assertThat(map.get("b")).isEqualTo(true);
        assertThat(map.get("c")).isNull();
        assertThat(map.get("d")).isEqualTo("x");
    }

    @Test
    void acceptsTrailingCommasAndTuples() {
        Map<String, Object> map = asMap(PythonLiteralParser.parse("{'a': (1, 2,), 'b': [3, 4,],}"));

        assertThat(asList(map.get("a"))).containsExactly(1L, 2L);
        assertThat(asList(map.get("b"))).containsExactly(3L, 4L);
    }

    @Test
    void decodesEscapesInStrings() {
        Map<String, Object> map =
                asMap(PythonLiteralParser.parse("{'a': 'it\\'s', 'b': \"line\\nbreak\", 'c': '\\u00e9\\x41'}"));

        assertThat(map.get("a")).isEqualTo("it's");
        assertThat(map.get("b")).isEqualTo("line\nbreak");
        assertThat(map.get("c")).isEqualTo("éA");
    }

    @Test
    void parsesNanInfAndHugeIntegers() {
        List<Object> list = asList(PythonLiteralParser.parse("[nan, inf, -inf, 123456789012345678901234567890]"));

        assertThat((Double) list.get(0)).isNaN();
        assertThat(list.get(1)).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(list.get(2)).isEqualTo(Double.NEGATIVE_INFINITY);
        assertThat(list.get(3)).isEqualTo(1.2345678901234568e29);
    }

    @Test
    void neverEvaluatesCode() {
        assertThatThrownBy(() -> PythonLiteralParser.parse("{'a': __import__('os').system('calc')}"))
                .isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> PythonLiteralParser.parse("[1 + 2]")).isInstanceOf(ImportException.class);
    }

    @Test
    void rejectsExcessiveNestingWithoutStackOverflow() {
        String deep = "[".repeat(5000) + "]".repeat(5000);

        assertThatThrownBy(() -> PythonLiteralParser.parse(deep))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("too deep");
    }

    @Test
    void reportsLineAndColumnOfSyntaxErrors() {
        assertThatThrownBy(() -> PythonLiteralParser.parse("{'a': 1,\n 'b': }"))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void rejectsUnterminatedStringsAndTrailingContent() {
        assertThatThrownBy(() -> PythonLiteralParser.parse("{'a': 'oops}")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> PythonLiteralParser.parse("{'a': 1} extra")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> PythonLiteralParser.parse("")).isInstanceOf(ImportException.class);
        assertThatThrownBy(() -> PythonLiteralParser.parse("{'a': 1")).isInstanceOf(ImportException.class);
    }
}
