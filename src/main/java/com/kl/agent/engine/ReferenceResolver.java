package com.kl.agent.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析计划参数中的 {@code $sN.path} 引用。
 *
 * <p>只处理“整个字符串就是一个引用”的情况，例如 {@code $s1.items}、
 * {@code $s1.items[0].score}；普通文本不做插值。</p>
 */
public final class ReferenceResolver {

    private static final Pattern FULL_REFERENCE =
            Pattern.compile("^\\$([A-Za-z_][A-Za-z0-9_]*)\\.(.+)$");

    private static final Pattern PATH_SEGMENT =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)(?:\\[(\\d+)])?$");

    /**
     * 递归解析 args 中的引用；返回新的 Map，不修改原参数。
     */
    public Map<String, Object> resolveArgs(Map<String, Object> args,
                                           Map<String, Object> outputs) {
        if (args == null) {
            return Map.of();
        }

        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            resolved.put(entry.getKey(), resolveValue(entry.getValue(), outputs));
        }
        return resolved;
    }

    /**
     * 逐层剥壳，直到取出string类型处理，不合法类型原样返回
     */
    private Object resolveValue(Object value, Map<String, Object> outputs) {
        if (value instanceof String text) {
            return resolveReference(text, outputs);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()),
                        resolveValue(entry.getValue(), outputs));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(resolveValue(item, outputs));
            }
            return result;
        }
        return value;
    }

    /**
     * 引用解析部分
     */
    private Object resolveReference(String text, Map<String, Object> outputs) {
        Matcher matcher = FULL_REFERENCE.matcher(text);
        if (!matcher.matches()) {
            return text;
        }

        String stepId = matcher.group(1);
        String path = matcher.group(2);

        if (!outputs.containsKey(stepId)) {
            throw new ReferenceResolutionException("引用的步骤未执行: " + stepId);
        }

        Object current = outputs.get(stepId);
        for (String segment : path.split("\\.")) {
            current = resolveSegment(current, segment, text);
        }
        return current;
    }

    private Object resolveSegment(Object current, String segment, String fullReference) {
        Matcher matcher = PATH_SEGMENT.matcher(segment);
        if (!matcher.matches()) {
            throw new ReferenceResolutionException(
                    "无法识别的引用路径片段: " + segment + " in " + fullReference);
        }

        String field = matcher.group(1);
        Integer index = matcher.group(2) == null ? null : Integer.valueOf(matcher.group(2));

        if (!(current instanceof Map<?, ?> map)) {
            throw new ReferenceResolutionException(
                    "引用路径无法继续解析: " + fullReference);
        }

        if (!map.containsKey(field) || map.get(field) == null) {
            throw new ReferenceResolutionException(
                    "引用字段不存在: " + field + " in " + fullReference);
        }

        current = map.get(field);
        if (index != null) {
            if (!(current instanceof List<?> list)) {
                throw new ReferenceResolutionException(
                        "引用字段不是列表: " + field + " in " + fullReference);
            }
            if (index < 0 || index >= list.size()) {
                throw new ReferenceResolutionException(
                        "引用列表下标越界: " + index + " in " + fullReference);
            }
            current = list.get(index);
        }

        return current;
    }
}
