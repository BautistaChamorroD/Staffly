package com.staffly.backend.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Converter
class ConceptoDescuentoListConverter implements AttributeConverter<List<ConceptoDescuento>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ConceptoDescuento>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<ConceptoDescuento> conceptos) {
        try {
            return MAPPER.writeValueAsString(conceptos == null ? List.of() : conceptos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando conceptos de descuento", e);
        }
    }

    @Override
    public List<ConceptoDescuento> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Error deserializando conceptos de descuento", e);
        }
    }
}
