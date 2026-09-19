package br.com.finance.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class CleanArchitectureTest {

    private static final JavaClasses APPLICATION_CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("br.com.finance");

    @Test
    void domainLayer_hasNoOuterLayerDependencies_whenArchitectureIsChecked() {
        noClasses()
            .that().resideInAPackage("..modules..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "br.com.finance.config..",
                "..modules..application..",
                "..modules..infrastructure..",
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet.."
            )
            .allowEmptyShould(true)
            .check(APPLICATION_CLASSES);
    }

    @Test
    void applicationLayer_hasNoOuterLayerDependencies_whenArchitectureIsChecked() {
        noClasses()
            .that().resideInAPackage("..modules..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "br.com.finance.config..",
                "..modules..infrastructure..",
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet.."
            )
            .allowEmptyShould(true)
            .check(APPLICATION_CLASSES);
    }

    @Test
    void controllers_areKeptAtTheWebBoundary_whenArchitectureIsChecked() {
        classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("..modules..infrastructure.presentation.web..")
            .allowEmptyShould(true)
            .check(APPLICATION_CLASSES);
    }

    @Test
    void functionalModules_haveNoDependencyCycles_whenArchitectureIsChecked() {
        slices()
            .matching("br.com.finance.modules.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .check(APPLICATION_CLASSES);
    }
}
