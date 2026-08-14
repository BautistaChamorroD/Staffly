package com.staffly.backend.payslip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staffly.backend.payroll.decorator.DeduccionLinea;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Converter
class DeduccionLineaListConverter implements AttributeConverter<List<DeduccionLinea>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<DeduccionLinea>> TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<DeduccionLinea> list) {
        try {
            return MAPPER.writeValueAsString(list == null ? List.of() : list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando deducciones", e);
        }
    }

    @Override
    public List<DeduccionLinea> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Error deserializando deducciones", e);
        }
    }
}
