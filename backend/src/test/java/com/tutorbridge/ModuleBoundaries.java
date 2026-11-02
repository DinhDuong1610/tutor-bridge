package com.tutorbridge;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The dependency table from docs/context/module-map.md, as ArchUnit rules. */
final class ModuleBoundaries {

    static final Map<String, Set<String>> ALLOWED = Map.of(
            "platform", Set.of(),
            "identity", Set.of("platform"),
            "classes", Set.of("identity", "platform"),
            "materials", Set.of("classes", "identity", "platform"),
            "learning", Set.of("materials", "classes", "identity", "platform"),
            "privacy", Set.of("materials", "learning", "classes", "identity", "platform"));

    private ModuleBoundaries() {}

    static List<ArchRule> rulesFor(String root) {
        List<ArchRule> rules = new ArrayList<>();
        for (var module : ALLOWED.entrySet()) {
            String name = module.getKey();
            for (String other : ALLOWED.keySet()) {
                if (!other.equals(name) && !module.getValue().contains(other)) {
                    rules.add(noClasses().that().resideInAPackage(root + "." + name + "..")
                            .should().dependOnClassesThat().resideInAPackage(root + "." + other + "..")
                            .allowEmptyShould(true));
                }
            }
            rules.add(noClasses().that().resideOutsideOfPackage(root + "." + name + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(root + "." + name + ".internal..", root + "." + name + ".web..")
                    .allowEmptyShould(true));
        }
        return rules;
    }
}
