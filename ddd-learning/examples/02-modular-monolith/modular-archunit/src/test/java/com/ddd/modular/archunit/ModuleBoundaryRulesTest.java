package com.ddd.modular.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * 模块化单体边界守护规则（ArchUnit）。
 *
 * <p>5 条核心规则：
 * <ol>
 *   <li>业务模块之间禁止跨边界依赖 infrastructure</li>
 *   <li>domain 不依赖 application / infrastructure</li>
 *   <li>Repository 必须是接口</li>
 *   <li>Controller 不直接依赖 Repository</li>
 *   <li>业务模块不依赖 modular-app</li>
 * </ol>
 */
class ModuleBoundaryRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .importPackages("com.ddd.modular");

    @Test
    void order_should_not_depend_on_inventory_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ddd.modular.order..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ddd.modular.inventory.infrastructure..");
        rule.check(CLASSES);
    }

    @Test
    void payment_should_not_depend_on_order_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ddd.modular.payment..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ddd.modular.order.infrastructure..");
        rule.check(CLASSES);
    }

    @Test
    void domain_should_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..");
        rule.check(CLASSES);
    }

    @Test
    void repositories_should_be_interfaces() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().beInterfaces();
        rule.check(CLASSES);
    }

    @Test
    void controllers_should_not_depend_on_repositories_directly() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository");
        rule.check(CLASSES);
    }

    @Test
    void business_modules_should_not_depend_on_app_module() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("com.ddd.modular.user..")
                .or().resideInAPackage("com.ddd.modular.order..")
                .or().resideInAPackage("com.ddd.modular.inventory..")
                .or().resideInAPackage("com.ddd.modular.payment..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.ddd.modular.app..");
        rule.check(CLASSES);
    }
}