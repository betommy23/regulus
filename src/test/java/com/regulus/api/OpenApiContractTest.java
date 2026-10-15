package com.regulus.api;

import java.io.InputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class OpenApiContractTest {
    @Autowired RequestMappingHandlerMapping mappings;

    @Test @SuppressWarnings("unchecked")
    void specificationDocumentsEveryApiMappingAndResolvesReferences() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/static/openapi.yaml")) {
            assertNotNull(in);
            Map<String, Object> doc = new Yaml().load(in);
            assertEquals("3.0.3", doc.get("openapi"));
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) doc.get("paths");
            Set<String> actual = new HashSet<>();
            mappings.getHandlerMethods().forEach((mapping, handler) -> {
                for (String path : mapping.getPatternValues()) {
                    if (path.startsWith("/api/")) {
                        for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
                            actual.add(method.name().toLowerCase(Locale.ROOT) + " " + path);
                        }
                    }
                }
            });
            Set<String> documented = new HashSet<>();
            paths.forEach((path, operations) -> operations.forEach((method, operation) -> documented.add(method + " " + path)));
            assertEquals(actual, documented);
            assertEquals(9, actual.size());
            checkReferences(doc, doc);
        }
    }

    @SuppressWarnings("unchecked")
    private void checkReferences(Object node, Map<String, Object> root) {
        if (node instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) node;
            if (map.containsKey("$ref")) {
                String ref = (String) map.get("$ref");
                assertTrue(ref.startsWith("#/"));
                Object target = root;
                for (String part : ref.substring(2).split("/")) {
                    target = ((Map<String, Object>) target).get(part);
                    assertNotNull(target, "Unresolved reference: " + ref);
                }
            }
            map.values().forEach(value -> checkReferences(value, root));
        } else if (node instanceof List) {
            ((List<?>) node).forEach(value -> checkReferences(value, root));
        }
    }
}
