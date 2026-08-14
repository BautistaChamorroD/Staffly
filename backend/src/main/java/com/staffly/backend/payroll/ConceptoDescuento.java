package com.staffly.backend.payroll;

import java.math.BigDecimal;

public class ConceptoDescuento {

    private String nombre;
    private TipoConcepto tipo;
    private BigDecimal valor;

    public ConceptoDescuento() {}

    public ConceptoDescuento(String nombre, TipoConcepto tipo, BigDecimal valor) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.valor = valor;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public TipoConcepto getTipo() { return tipo; }
    public void setTipo(TipoConcepto tipo) { this.tipo = tipo; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
