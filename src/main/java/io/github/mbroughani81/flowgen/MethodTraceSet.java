package io.github.mbroughani81.flowgen;

import java.util.List;
import java.util.stream.Collectors;

import sootup.core.jimple.common.stmt.Stmt;

public class MethodTraceSet {
    public String method;
    public List<Trace> traces;

    // Getters and Setters for MethodTraceSet fields
    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<Trace> getTraces() {
        return traces;
    }

    public void setTraces(List<Trace> traces) {
        this.traces = traces;
    }

    public static class Trace {
        public String id;
        public List<Stmt> path;
        public List<String> tags;
        public long estimatedCost;       // total estimated cost (known + est)
        public long knownCost;           // sum of inner @MSpec costs

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

        @Override
        public String toString() {
            return "Trace{" +
                    "id='" + id + '\'' +
                    ", path=" + path +
                    ", tags=" + tags +
                    ", estimatedCost=" + estimatedCost +
                    ", knownCost=" + knownCost +
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


