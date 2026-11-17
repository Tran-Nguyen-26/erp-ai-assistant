package vn.edu.uet.erpai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import vn.edu.uet.erpai.agent.repository.ViolationExplanationRepository;
import vn.edu.uet.erpai.agent.workflow.ExplainViolationWorkflow;
import vn.edu.uet.erpai.agent.workflow.ExplanationOutput;
import vn.edu.uet.erpai.common.llm.LlmClient;
import vn.edu.uet.erpai.common.pii.PiiMaskingService;
import vn.edu.uet.erpai.common.security.CurrentUser;
import vn.edu.uet.erpai.common.security.Role;
import vn.edu.uet.erpai.erp.model.ErpEntityType;
import vn.edu.uet.erpai.erp.model.ErpPartnerRef;
import vn.edu.uet.erpai.erp.model.ErpVoucher;
import vn.edu.uet.erpai.erp.service.ErpQueryService;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievalResult;
import vn.edu.uet.erpai.knowledge.retrieval.RetrievedChunk;
import vn.edu.uet.erpai.knowledge.service.KnowledgeSearchService;
import vn.edu.uet.erpai.rule.service.RuleAdminService;
import vn.edu.uet.erpai.rule.service.RuleResponse;
import vn.edu.uet.erpai.rule.service.ViolationResponse;
import vn.edu.uet.erpai.rule.service.ViolationService;

/**
 * Nguyên tắc #3: provider ≠ ollama thì prompt gửi đi phải được mask, câu trả lời được khôi phục.
 */
class ExplainViolationWorkflowMaskingTest {

  @Test
  void cloudProviderReceivesMaskedPromptAndUserSeesRestoredText() {
    var violations = mock(ViolationService.class);
    var rules = mock(RuleAdminService.class);
    var erp = mock(ErpQueryService.class);
    var knowledge = mock(KnowledgeSearchService.class);
    var llm = mock(LlmClient.class);
    var repo = mock(ViolationExplanationRepository.class);
    var events = mock(ApplicationEventPublisher.class);

    var user = new CurrentUser(UUID.randomUUID(), "u", Role.ACCOUNTANT, "C1");
    var vid = UUID.randomUUID();
    when(violations.get("C1", vid))
        .thenReturn(
            new ViolationResponse(
                vid,
                "FLD_TAX_CODE_FORMAT",
                "MST sai",
                "WARNING",
                "OPEN",
                "PURCHASE_INVOICE",
                "ref",
                "MH1",
                LocalDate.of(2026, 10, 15),
                "MST 0101234567-1 sai định dạng",
                Map.of(),
                false,
                null,
                null,
                null,
                "fp"));
    when(rules.get("FLD_TAX_CODE_FORMAT"))
        .thenReturn(
            new RuleResponse(
                "FLD_TAX_CODE_FORMAT",
                "n",
                "FLD",
                "WARNING",
                "PURCHASE_INVOICE",
                "SPEL",
                Map.of(),
                List.of(),
                "cấu trúc mã số thuế",
                true,
                1,
                0L,
                null,
                null));
    when(erp.getPurchaseInvoice(any(), any()))
        .thenReturn(
            new ErpVoucher(
                "ref",
                "C1",
                ErpEntityType.PURCHASE_INVOICE,
                210,
                "MH1",
                LocalDate.of(2026, 10, 15),
                null,
                "Mua hàng",
                new ErpPartnerRef("p", "N", "Công ty Bí Mật", "0101234567-1"),
                "VND",
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                true,
                List.of(),
                null));
    var chunk =
        new RetrievedChunk(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "TT",
            "t",
            "Điều 1",
            "Mã số thuế gồm 10 chữ số.",
            null,
            0.1,
            0.9);
    when(knowledge.search(anyString(), any(), any()))
        .thenReturn(new RetrievalResult(List.of(chunk), 0.9, true));
    when(llm.modelName()).thenReturn("cloud-model");
    when(llm.generate(anyString(), anyString(), eq(ExplanationOutput.class)))
        .thenReturn(
            new ExplanationOutput(
                "MST [[TAX_1]] sai",
                "Đối tượng [[PARTNER_1]] có MST [[TAX_1]] sai [1].",
                List.of(new ExplanationOutput.CitationOut(1, "gồm 10 chữ số")),
                0.8,
                null));
    when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    when(repo.findFirstByViolationIdAndPromptVersionOrderByCreatedAtDesc(any(), any()))
        .thenReturn(Optional.empty());

    var workflow =
        new ExplainViolationWorkflow(
            violations,
            rules,
            erp,
            knowledge,
            llm,
            new PiiMaskingService(),
            repo,
            mock(vn.edu.uet.erpai.review.service.ProposalService.class),
            events,
            "openai");
    var response = workflow.explain(user, vid, false, false);

    var prompt = ArgumentCaptor.forClass(String.class);
    verify(llm).generate(anyString(), prompt.capture(), eq(ExplanationOutput.class));
    assertThat(prompt.getValue())
        .doesNotContain("Công ty Bí Mật", "0101234567")
        .contains("[[PARTNER_1]]", "[[TAX_1]]");
    assertThat(response.explanation().explanation())
        .contains("Công ty Bí Mật")
        .contains("0101234567-1");
    assertThat(response.explanation().supportStatus()).isEqualTo("SUPPORTED");
  }
}
