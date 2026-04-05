package com.github.realms42.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonMergeUtil {
    public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
        if (mainNode == null || mainNode.isNull()) {
            return updateNode.deepCopy();
        }
        if (updateNode == null || updateNode.isNull()) {
            return mainNode.deepCopy();
        }

        if (mainNode.isObject() && updateNode.isObject()) {
            ObjectNode merged = ((ObjectNode) mainNode).deepCopy();
            updateNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (merged.has(key)) {
                    merged.set(key, merge(merged.get(key), value));
                } else {
                    merged.set(key, value);
                }
            });
            return merged;
        }
        return updateNode.deepCopy();
    }
}
