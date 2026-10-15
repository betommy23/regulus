package com.regulus.api.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Regenerates public-field schemas from the pinned vendor artifact; no business enums are invented. */
public class VendorSchemaGenerator {
    private static final Map<String, Object> schemas = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        schema(regulusbo.book.entity.cash.class);
        schema(regulusbo.book.entity.trade.class);
        schema(regulusbo.book.entity.stock.class);
        schema(regulusbo.book.demo.demoAsset.class);
        File output = new File(args.length == 0 ? "build/vendor-schemas.json" : args[0]);
        output.getParentFile().mkdirs();
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output, schemas);
    }

    private static Map<String, Object> schema(Type type) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (type instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType) type;
            result.put("type", "array"); result.put("items", schema(p.getActualTypeArguments()[0]));
            return result;
        }
        Class<?> c = (Class<?>) type;
        if (c == String.class) result.put("type", "string");
        else if (c == boolean.class || c == Boolean.class) result.put("type", "boolean");
        else if (c == int.class || c == Integer.class || c == long.class || c == Long.class) {
            result.put("type", "integer"); result.put("format", c == long.class || c == Long.class ? "int64" : "int32");
        } else if (Number.class.isAssignableFrom(c) || c == double.class || c == float.class) {
            result.put("type", "number"); result.put("format", "double");
        } else if (java.util.Date.class.isAssignableFrom(c)) {
            result.put("type", "string"); result.put("format", "date");
        } else {
            String name = "Vendor_" + c.getSimpleName();
            result.put("$ref", "#/components/schemas/" + name);
            if (!schemas.containsKey(name)) {
                Map<String, Object> object = new LinkedHashMap<>();
                object.put("type", "object"); schemas.put(name, object);
                Map<String, Object> fields = new LinkedHashMap<>();
                for (Field f : c.getFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) fields.put(f.getName(), schema(f.getGenericType()));
                }
                object.put("properties", fields);
            }
        }
        if (!c.isPrimitive() && !result.containsKey("$ref")) result.put("nullable", true);
        return result;
    }
}
