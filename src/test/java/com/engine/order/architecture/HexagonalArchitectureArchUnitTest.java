package com.engine.order.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.engine.order", importOptions = {ImportOption.DoNotIncludeTests.class})
public class HexagonalArchitectureArchUnitTest {

    @ArchTest
    public static final ArchRule domainMustNotDependOnFrameworks =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "jakarta.servlet..",
                            "org.hibernate.."
                    )
                    .because("The Domain model must remain pure with zero framework/JPA/Spring dependencies");

    @ArchTest
    public static final ArchRule domainMustNotDependOnOuterLayers =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..application..",
                            "..infrastructure.."
                    )
                    .because("Domain layer cannot depend on Application or Infrastructure layers");

    @ArchTest
    public static final ArchRule applicationMustNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("Application services must be independent of concrete infrastructure adapters");

    @ArchTest
    public static final ArchRule layeredHexagonalArchitectureRules =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy("com.engine.order.domain..")
                    .layer("Application").definedBy("com.engine.order.application..")
                    .layer("Infrastructure").definedBy("com.engine.order.infrastructure..")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

    @ArchTest
    public static final ArchRule restControllersMustResideInRestPackage =
            classes()
                    .that().haveSimpleNameEndingWith("RestController")
                    .should().resideInAPackage("..infrastructure.adapter.in.rest..")
                    .because("REST controllers are driving adapters and must reside in the adapter.in.rest package");

    @ArchTest
    public static final ArchRule repositoryAdaptersMustResideInPersistencePackage =
            classes()
                    .that().haveSimpleNameEndingWith("RepositoryAdapter")
                    .should().resideInAPackage("..infrastructure.adapter.out.persistence..")
                    .because("Repository adapters are persistence driven adapters");

    @ArchTest
    public static final ArchRule kafkaConsumersMustResideInKafkaPackage =
            classes()
                    .that().haveSimpleNameEndingWith("Consumer")
                    .should().resideInAPackage("..infrastructure.adapter.in.kafka..")
                    .because("Kafka event consumers are driving adapters residing in adapter.in.kafka");
}
