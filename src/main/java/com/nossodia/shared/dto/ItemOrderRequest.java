package com.nossodia.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.nossodia.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(requiredProperties = {"items"}, description = "Complete set including inactive items, with unique IDs and orders; maximum 500")
public record ItemOrderRequest(List<Entry> items) {
    @Schema(requiredProperties = {"id", "sortOrder"})
    public record Entry(UUID id, @Schema(minimum = "0", maximum = "2147483647") Integer sortOrder) {}
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ItemOrderRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "items");
        if (!(values.get("items") instanceof List<?> list) || list.size() > 500) throw new InputException();
        var items = new ArrayList<Entry>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> entry) || !entry.keySet().equals(Set.of("id", "sortOrder"))) throw new InputException();
            UUID id = JsonFields.uuid(entry.get("id")); Integer order = JsonFields.integer(entry.get("sortOrder"));
            JsonFields.required(id); JsonFields.required(order); items.add(new Entry(id, order));
        }
        return new ItemOrderRequest(List.copyOf(items));
    }
    public Map<UUID, Integer> validate(Set<UUID> expected) {
        if (items == null || items.size() != expected.size()) throw new InputException();
        var result = new HashMap<UUID, Integer>(); var orders = new HashSet<Integer>();
        for (Entry entry : items) {
            if (entry == null || entry.id() == null || entry.sortOrder() == null || entry.sortOrder() < 0
                    || result.put(entry.id(), entry.sortOrder()) != null || !orders.add(entry.sortOrder())) throw new InputException();
        }
        if (!result.keySet().equals(expected)) throw new InputException();
        return result;
    }
}
