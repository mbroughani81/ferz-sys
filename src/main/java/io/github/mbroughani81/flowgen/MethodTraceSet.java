package io.github.mbroughani81.flowgen;

import java.util.List;
import java.util.stream.Collectors;

import sootup.core.jimple.common.stmt.Stmt;

public class MethodTraceSet {
    public String method;
    public SpecInfo spec;
    public List<Trace> traces;

    // Getters and Setters for MethodTraceSet fields
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public SpecInfo getSpec() {
        return spec;
    }

    public void setSpec(SpecInfo spec) {
        this.spec = spec;
    }

    public List<Trace> getTraces() {
        return traces;
    }

    public void setTraces(List<Trace> traces) {
        this.traces = traces;
    }

    public static class SpecInfo {
        public String type;
        public long max;
        public String unit;
        public int percentile;

        // Getters and Setters for SpecInfo fields
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public long getMax() {
            return max;
        }

        public void setMax(long max) {
            this.max = max;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public int getPercentile() {
            return percentile;
        }

        public void setPercentile(int percentile) {
            this.percentile = percentile;
        }

        @Override
        public String toString() {
            return "SpecInfo{" +
                    "type='" + type + '\'' +
                    ", max=" + max +
                    ", unit='" + unit + '\'' +
                    ", percentile=" + percentile +
                    '}';
        }
    }

    public static class Trace {
        public String id;
        public List<Stmt> path;
        public List<String> tags;
        public long estimatedCost;       // total estimated cost (known + est)
        public long knownCost;           // sum of inner @IOSpec costs
        public String riskLevel;         // GUARANTEED_VIOLATION, SUSPECTED, LOW_RISK

        // Getters and Setters for Trace fields
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<Stmt> getPath() {
            return path;
        }

        public void setPath(List<Stmt> path) {
            this.path = path;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public long getEstimatedCost() {
            return estimatedCost;
        }

        public void setEstimatedCost(long estimatedCost) {
            this.estimatedCost = estimatedCost;
        }

        public long getKnownCost() {
            return knownCost;
        }

        public void setKnownCost(long knownCost) {
            this.knownCost = knownCost;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "Trace{" +
                    "id='" + id + '\'' +
                    ", path=" + path +
                    ", tags=" + tags +
                    ", estimatedCost=" + estimatedCost +
                    ", knownCost=" + knownCost +
                    ", riskLevel='" + riskLevel + '\'' +
                    '}';
        }

        public static String pathToStr(List<Stmt> path) {
            return path.stream()
                .map(stmt -> "line " + (stmt.getPositionInfo().getStmtPosition() != null
                                        ? stmt.getPositionInfo().getStmtPosition().getFirstLine()
                                        : -1) + ": " + stmt.toString())
                .collect(Collectors.joining("\n"));
        }
    }
}


