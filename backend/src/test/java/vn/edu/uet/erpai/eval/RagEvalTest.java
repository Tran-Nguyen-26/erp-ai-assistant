package vn.edu.uet.erpai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.uet.erpai.eval.RagQuestion.Expected;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievalResult;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;

class RagEvalTest {

  private static RetrievedChunk chunk(String doc, String heading) {
    return new RetrievedChunk(
        UUID.randomUUID(), UUID.randomUUID(), doc, "t", heading, "nội dung", null, 0.1, 0.9);
  }

  private static RagQuestion q(String text, boolean answerable, Expected... expected) {
    return new RagQuestion(text, LocalDate.of(2026, 10, 15), answerable, List.of(expected));
  }

  @Test
  void computesHitAtKMrrRefusalAndLatencyFromARealisticMix() {
    var searcher =
        (RagEvalRunner.Searcher)
            (query, asOf) ->
                switch (query) {
                  case "a" ->
                      new RetrievalResult(
                          List.of(chunk("ND-1", "Chương I > Điều 3 > Khoản 1"), chunk("ND-9", "x")),
                          0.9,
                          true); // hạng 1 (đoạn con của Điều 3)
                  case "b" ->
                      new RetrievalResult(
                          List.of(chunk("ND-9", "x"), chunk("TT-2", "Điều 5")),
                          0.8,
                          true); // hạng 2
                  case "c" ->
                      new RetrievalResult(
                          List.of(
                              chunk("ND-9", "x"),
                              chunk("ND-8", "y"),
                              chunk("ND-7", "z"),
                              chunk("ND-1", "Chương I > Điều 3")),
                          0.7,
                          true); // hạng 4
                  case "d" -> new RetrievalResult(List.of(chunk("ND-9", "x")), 0.6, true); // trượt
                  case "ngoai-kho-ok" -> new RetrievalResult(List.of(), 0.1, false); // từ chối đúng
                  default ->
                      new RetrievalResult(
                          List.of(chunk("ND-9", "x")), 0.8, true); // lẽ ra phải từ chối
                };
    var report =
        new RagEvalRunner(searcher)
            .run(
                List.of(
                    q("a", true, new Expected("ND-1", "Chương I > Điều 3")),
                    q("b", true, new Expected("TT-2", "Điều 5")),
                    q("c", true, new Expected("ND-1", "Chương I > Điều 3")),
                    q("d", true, new Expected("ND-1", "Điều 1")),
                    q("ngoai-kho-ok", false),
                    q("ngoai-kho-sai", false)));

    assertThat(report.questions()).isEqualTo(6);
    assertThat(report.answerable()).isEqualTo(4);
    assertThat(report.hitAt1()).isEqualTo(0.25);
    assertThat(report.hitAt3()).isEqualTo(0.5);
    assertThat(report.hitAt5()).isEqualTo(0.75);
    assertThat(report.mrr())
        .isCloseTo((1 + 0.5 + 0.25) / 4, org.assertj.core.data.Offset.offset(1e-9));
    assertThat(report.unanswerable()).isEqualTo(2);
    assertThat(report.refusalRate()).isEqualTo(0.5);
    assertThat(report.p95Millis()).isGreaterThanOrEqualTo(report.p50Millis());
  }

  @Test
  void headingPrefixMustMatchAndDocNumberMustBeEqual() {
    var question = q("x", true, new Expected("ND-1", "Điều 3"));
    assertThat(RagEvalRunner.matches(chunk("ND-1", "Điều 3 > Khoản 2"), question)).isTrue();
    assertThat(RagEvalRunner.matches(chunk("ND-1", "Điều 4"), question)).isFalse();
    assertThat(RagEvalRunner.matches(chunk("ND-2", "Điều 3"), question)).isFalse();
    assertThat(RagEvalRunner.matches(chunk(null, "Điều 3"), question)).isFalse();
  }

  @Test
  void exampleDatasetFileParses() throws Exception {
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var file = Path.of("../eval/datasets/rag-questions.example.json");
    var list = mapper.readValue(Files.readString(file), new TypeReference<List<RagQuestion>>() {});
    assertThat(list).hasSize(2);
    assertThat(list.get(0).answerable()).isTrue();
    assertThat(list.get(0).relevant()).hasSize(1);
    assertThat(list.get(1).relevant()).isEmpty();
  }

  @Test
  void ragReportIsWritten() throws Exception {
    var report = new RagEvalReport(2, 1, 1, 1, 1, 1, 1, 1, 10, 20);
    var dir = Files.createTempDirectory("rag-eval");
    var file = EvalReportWriter.write(report, dir);
    assertThat(Files.readString(file)).contains("Hit@1").contains("MRR").contains("p95 20 ms");
  }
}
