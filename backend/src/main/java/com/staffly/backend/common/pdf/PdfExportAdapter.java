package com.staffly.backend.common.pdf;

/**
 * Adapter que desacopla la librería de generación de PDF del resto del sistema.
 * Las implementaciones concretas encapsulan la dependencia (OpenPDF, iText, etc.).
 *
 * @param <T> tipo del modelo de datos que alimenta el template
 */
public interface PdfExportAdapter<T> {
    byte[] generate(T data);
}
