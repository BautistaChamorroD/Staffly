package com.staffly.backend.payslip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Converter
class UUIDListConverter implements AttributeConverter<List<UUID>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<UUID>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<UUID> list) {
        try {
            return MAPPER.writeValueAsString(list == null ? List.of() : list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando lista de UUIDs", e);
        }
    }

    @Override
    public List<UUID> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Error deserializando lista de UUIDs", e);
        }
    }
}
