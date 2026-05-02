package io.github.mbroughani81.flowgen;

import java.util.List;

public class MethodTraceSet {
    private String method;
    private SpecInfo spec;
    private List<Trace> traces;

    // Getters and Setters for PTestFlow
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("MethodTraceSet {\n");
        sb.append("  method='").append(method).append("'\n");

        if (spec != null) {
            sb.append("  spec=").append(spec).append("\n");
        } else {
            sb.append("  spec=null\n");
        }

        if (traces != null && !traces.isEmpty()) {
            sb.append("  traces (").append(traces.size()).append("):\n");
            for (Trace trace : traces) {
                sb.append("    ").append(trace).append("\n");
            }
        } else {
            sb.append("  traces=[]\n");
        }

        sb.append("}");

        return sb.toString();
    }

    public static class SpecInfo {
        private String type;
        private long max;
        private String unit;
        private int percentile;

        // Getters and Setters for SpecInfo
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
        private String id;
        private List<String> path;
        private List<String> tags;

        // Getters and Setters for Flow
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<String> getPath() {
            return path;
        }

        public void setPath(List<String> path) {
            this.path = path;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        @Override
        public String toString() {
            return "Trace{" +
                    "id='" + id + '\'' +
                    ", path=" + path +
                    ", tags=" + tags +
                    '}';
        }
    }
}


