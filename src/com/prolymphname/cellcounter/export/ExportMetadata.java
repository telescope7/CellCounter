package com.prolymphname.cellcounter.export;

public class ExportMetadata {
    public static final ExportMetadata EMPTY = new ExportMetadata("", "", "");

    private final String cellType;
    private final String substrate;
    private final String flowCondition;

    public ExportMetadata(String cellType, String substrate, String flowCondition) {
        this.cellType = sanitize(cellType);
        this.substrate = sanitize(substrate);
        this.flowCondition = sanitize(flowCondition);
    }

    public String getCellType() {
        return cellType;
    }

    public String getSubstrate() {
        return substrate;
    }

    public String getFlowCondition() {
        return flowCondition;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
