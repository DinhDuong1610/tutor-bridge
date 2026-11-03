package com.tutorbridge;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void production_code_respects_module_boundaries() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.tutorbridge");

        for (var rule : ModuleBoundaries.rulesFor("com.tutorbridge")) {
            rule.check(classes);
        }
    }

    @Test
    void rules_reject_a_reverse_dependency_and_an_internal_access() {
        JavaClasses fixture = new ClassFileImporter().importPackages("com.tutorbridge.archfixture");

        var violations = ModuleBoundaries.rulesFor("com.tutorbridge.archfixture").stream()
                .map(rule -> rule.evaluate(fixture))
                .filter(result -> result.hasViolation())
                .flatMap(result -> result.getFailureReport().getDetails().stream())
                .toList();

        assertThat(violations)
                .anyMatch(detail -> detail.contains("identity.api.ReverseCall")
                        && detail.contains("classes.internal.ClassService"))
                .anyMatch(detail -> detail.contains("classes.internal.ClassService")
                        && detail.contains("identity.internal.AccountStore"));
    }
}
