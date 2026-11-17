package vn.edu.uet.erpai;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Kiểm tra quy tắc phụ thuộc giữa module (CLAUDE.md mục 3). */
class ModuleDependencyTest {

  private static final String BASE = "vn.edu.uet.erpai.";
  private static final List<String> MODULES =
      List.of("auth", "erp", "entry", "rule", "knowledge", "agent", "review", "audit");

  private final JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("vn.edu.uet.erpai");

  @Test
  void modulesDoNotUseOtherModulesRepositoryOrDomain() {
    for (var module : MODULES) {
      var forbidden =
          MODULES.stream()
              .filter(m -> !m.equals(module))
              .flatMap(
                  m ->
                      java.util.stream.Stream.of(
                          BASE + m + ".repository..", BASE + m + ".domain.."))
              .toArray(String[]::new);
      noClasses()
          .that()
          .resideInAPackage(BASE + module + "..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(forbidden)
          .because("module " + module + " chỉ được gọi service công khai của module khác")
          .allowEmptyShould(true)
          .check(classes);
    }
  }

  @Test
  void erpDoesNotDependOnAnyOtherBusinessModule() {
    var others =
        MODULES.stream()
            .filter(m -> !m.equals("erp"))
            .map(m -> BASE + m + "..")
            .toArray(String[]::new);
    noClasses()
        .that()
        .resideInAPackage(BASE + "erp..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(others)
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void erpDatasourceIsOnlyUsedByErpModule() {
    noClasses()
        .that()
        .resideOutsideOfPackages(BASE + "erp..", BASE + "config..", BASE + "rule..")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class)
        .because("truy cập SQL Server chỉ qua module erp (rule SQL qua ErpQueryService)")
        .allowEmptyShould(true)
        .check(classes);
  }
}
